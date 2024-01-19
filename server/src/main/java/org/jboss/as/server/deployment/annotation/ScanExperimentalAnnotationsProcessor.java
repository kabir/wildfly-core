/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2023 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jboss.as.server.deployment.annotation;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.VisitorAttributes;
import org.jboss.vfs.util.SuffixMatchFilter;
import org.wildfly.experimental.api.classpath.index.ByteRuntimeIndex;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassInfoScanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScanExperimentalAnnotationsProcessor implements DeploymentUnitProcessor {

    private final ByteRuntimeIndex runtimeIndex;

    private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
    private static final String INDEX_FILE = "index.txt";
    private final Stability stability;


    public ScanExperimentalAnnotationsProcessor(RunningMode runningMode, Stability stability) {
        this.stability = stability;
        ByteRuntimeIndex runtimeIndex = null;
        if (runningMode != RunningMode.ADMIN_ONLY) {
            ModuleLoader moduleLoader = ((ModuleClassLoader) this.getClass().getClassLoader()).getModule().getModuleLoader();
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

                    runtimeIndex = ByteRuntimeIndex.load(urls);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        this.runtimeIndex = runtimeIndex;
    }

    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        if (runtimeIndex == null) {
            return;
        }
        if (stability.enables(Stability.EXPERIMENTAL)) {
            // If running with Stability.EXPERIMENTAL we don't care if the user is using experimental annotations
            return;
        }

        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);
        readValueFromModel(top);


        ClassInfoScanner scanner = top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_SCANNER);
        if (scanner == null) {
            scanner = new ClassInfoScanner(runtimeIndex);
            top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_SCANNER, scanner);
        }
        ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment processor = top.getAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT);
        if (processor == null) {
            processor = new ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment();
            top.putAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT, processor);
        }

        long start = System.currentTimeMillis();
        // TODO the Jandex scanning scans a lot more stuff, do we need to do that too?
        ServerLogger.DEPLOYMENT_LOGGER.infof("=====> Scanning deployment with POC 3");
        List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
        for (ResourceRoot root : resourceRoots) {

            if (root.getAttachment(Attachments.ANNOTATION_INDEX) != null) {
                // Taken from ResourceRootIndexer.
                // During my tests for a war with a lot of nested WEB-INF/lib archives and some classes in WEB-INF/classes
                // the classes from the nested archives were counted twice if I did not ignore this file
                // The WEB-INF/lib archives and WEB-INF/classes are available as other resource roots so they will
                // get scanned anyway
                break;
            }
            final VisitorAttributes visitorAttributes = new VisitorAttributes();
            visitorAttributes.setLeavesOnly(true);
            visitorAttributes.setRecurseFilter(f-> true);

            try {
                final List<VirtualFile> classChildren = root.getRoot().getChildren(new SuffixMatchFilter(".class", visitorAttributes));
                for (VirtualFile file : classChildren) {
                    if (file.isFile() && file.getName().endsWith(".class")) {
                        try (InputStream in = file.openStream()) {
                            scanner.scanClass(in);
                            processor.incrementClassesScannedCount();
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        long time = (System.currentTimeMillis() - start);
        ServerLogger.DEPLOYMENT_LOGGER.infof("==== POC 3 took %d ms to scan %d classes", time, processor.getClassesScannedCount());
    }

    private String readValueFromModel(DeploymentUnit top) {
        ModelController controller = (ModelController) top.getServiceRegistry().getService(Services.JBOSS_SERVER_CONTROLLER).getValue();
        // Temporary experiment at reading the model until I add the model attribute
        ///core-service=management/access=authorization:read-attribute(name=permission-combination-policy)
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress("core-service", "management").append("access", "authorization"), "permission-combination-policy");
        ModelNode result = controller.execute(op, null, ModelController.OperationTransactionControl.COMMIT, null);
        System.out.println("===== Reading model");
        System.out.println(result);
        return null;
    }
}
