/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.moduleservice.ModuleIndexBuilder;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VirtualFileFilter;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.SuffixMatchFilter;
import org.wildfly.experimental.api.classpath.index.ByteRuntimeIndex;
import org.wildfly.experimental.api.classpath.runtime.bytecode.JandexCollector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for indexing a resource root
 */
public class ResourceRootIndexer {
    private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
    private static final String INDEX_FILE = "index.txt";

    /**
     * Creates and attaches the annotation index to a resource root, if it has not already been attached
     */
    public static void indexResourceRoot(final DeploymentUnit tempUnit, final ResourceRoot resourceRoot) throws DeploymentUnitProcessingException {
        if (resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX) != null) {
            return;
        }

        VirtualFile indexFile = resourceRoot.getRoot().getChild(ModuleIndexBuilder.INDEX_LOCATION);
        if (indexFile.exists()) {
            try {
                IndexReader reader = new IndexReader(indexFile.openStream());
                resourceRoot.putAttachment(Attachments.ANNOTATION_INDEX, reader.read());
                ServerLogger.DEPLOYMENT_LOGGER.tracef("Found and read index at: %s", indexFile);
                return;
            } catch (Exception e) {
                ServerLogger.DEPLOYMENT_LOGGER.cannotLoadAnnotationIndex(indexFile.getPathName(), e.toString());
            }
        }

        // if this flag is present and set to false then do not index the resource
        Boolean shouldIndexResource = resourceRoot.getAttachment(Attachments.INDEX_RESOURCE_ROOT);
        if (shouldIndexResource != null && !shouldIndexResource) {
            return;
        }

        final List<String> indexIgnorePathList = resourceRoot.getAttachment(Attachments.INDEX_IGNORE_PATHS);
        final Set<String> indexIgnorePaths;
        if (indexIgnorePathList != null && !indexIgnorePathList.isEmpty()) {
            indexIgnorePaths = new HashSet<String>(indexIgnorePathList);
        } else {
            indexIgnorePaths = null;
        }

        JandexCollector collector = null;
        String poc = System.getProperty("scan.poc");
        if ("2".equals(poc)) {
            DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(tempUnit);
            // Hacky cast, only because we are switching between different POCs
            collector = (JandexCollector) top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER);
            if (collector == null) {
                collector = new JandexCollector(loadRuntimeIndex());
                top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER, collector);
            }
            ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment processor = top.getAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT);
            if (processor == null) {
                processor = new ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment();
                top.putAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT, processor);
            }
        }


        final VirtualFile virtualFile = resourceRoot.getRoot();
        final Indexer indexer = new Indexer(collector);
        try {
            final VisitorAttributes visitorAttributes = new VisitorAttributes();
            visitorAttributes.setLeavesOnly(true);
            visitorAttributes.setRecurseFilter(new VirtualFileFilter() {
                public boolean accepts(VirtualFile file) {
                    return indexIgnorePaths == null || !indexIgnorePaths.contains(file.getPathNameRelativeTo(virtualFile));
                }
            });

            final List<VirtualFile> classChildren = virtualFile.getChildren(new SuffixMatchFilter(".class", visitorAttributes));
            for (VirtualFile classFile : classChildren) {
                InputStream inputStream = null;
                try {
                    inputStream = classFile.openStream();
                    indexer.index(inputStream);
                } catch (Exception e) {
                    ServerLogger.DEPLOYMENT_LOGGER.cannotIndexClass(classFile.getPathNameRelativeTo(virtualFile), virtualFile.getPathName(), e);
                } finally {
                    VFSUtils.safeClose(inputStream);
                }
            }
            final Index index = indexer.complete();
            resourceRoot.putAttachment(Attachments.ANNOTATION_INDEX, index);
            ServerLogger.DEPLOYMENT_LOGGER.tracef("Generated index for archive %s", virtualFile);
        } catch (Throwable t) {
            throw ServerLogger.ROOT_LOGGER.deploymentIndexingFailed(t);
        }
    }

    private static ByteRuntimeIndex loadRuntimeIndex() {
        ModuleLoader moduleLoader = ((ModuleClassLoader) ResourceRootIndexer.class.getClassLoader()).getModule().getModuleLoader();
        Module module = null;
        try {
            module = moduleLoader.loadModule(BASE_MODULE_NAME);
        } catch (ModuleLoadException e) {
            // TODO make this module part of core so it is always there
        }
        if (module != null) {
            URL url = module.getExportedResource(INDEX_FILE);
            List<URL> urls = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                String fileName = reader.readLine();
                while (fileName != null) {
                    if (!fileName.isEmpty()) {
                        urls.add(module.getExportedResource(fileName));
                    }
                    fileName = reader.readLine();
                }

                return ByteRuntimeIndex.load(urls);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }
}
