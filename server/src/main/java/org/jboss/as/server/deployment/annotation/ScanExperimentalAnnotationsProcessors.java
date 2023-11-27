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

import org.jboss.as.controller.RunningMode;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.wildfly.experimental.api.classpath.index.ByteRuntimeIndex;
import org.wildfly.experimental.api.classpath.index.RuntimeIndex;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector;
import org.wildfly.experimental.api.classpath.runtime.bytecode.FastClassInfoScanner;
import org.wildfly.experimental.api.classpath.runtime.bytecode.JandexCollector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScanExperimentalAnnotationsProcessors {

    public static DeploymentUnitProcessor createProcessor(RunningMode runningMode) {
        String poc = System.getProperty("scan.poc");
        if ("1".equals(poc)) {
            return new ScanExperimentalAnnotationsProcessorPoc1(runningMode);
        } else if ("2".equals(poc)) {
            //return new ScanExperimentalAnnotationsProcessorPoc2(runningMode);
            return new NullAnnotationsProcessor();
        } else if ("3".equals(poc)) {
            return new ScanExperimentalAnnotationsProcessorPoc3(runningMode);
        } else {
            if (runningMode == RunningMode.NORMAL) {
                throw new IllegalStateException("Unknown value -Dscan.poc=" + poc);
            }
            return new NullAnnotationsProcessor();
        }
    }

    private static class NullAnnotationsProcessor implements DeploymentUnitProcessor {
        @Override
        public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        }
    }

    private static class ScanExperimentalAnnotationsProcessorPoc1 implements DeploymentUnitProcessor {

        private final RuntimeIndex runtimeIndex;

        private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
        private static final String INDEX_FILE = "index.txt";


        private ScanExperimentalAnnotationsProcessorPoc1(RunningMode runningMode) {
            RuntimeIndex runtimeIndex = null;

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

                        runtimeIndex = RuntimeIndex.load(urls);

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

            final DeploymentUnit du = phaseContext.getDeploymentUnit();
            DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);

            // Hacky cast, only because we are switching between different POCs
            ClassBytecodeInspector inspector = (ClassBytecodeInspector)top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER);
            if (inspector == null) {
                inspector = new ClassBytecodeInspector(runtimeIndex);
                top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER, inspector);
                System.out.println();
            }
            ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment processor = top.getAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT);
            if (processor == null) {
                processor = new ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment();
                top.putAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT, processor);
            }

            // TODO the Jandex scanning scans a lot more stuff, do we need to do that too?
            ServerLogger.DEPLOYMENT_LOGGER.infof("=====> Scanning deployment with POC 1");
            List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
            for (ResourceRoot root : resourceRoots) {
                try {
                    List<VirtualFile> files = root.getRoot().getChildrenRecursively();
                    for (VirtualFile file : files) {
                        if (file.isFile() && file.getName().endsWith(".class")) {
                            try (InputStream in = file.openStream()) {
                                inspector.scanClassFile(in);
                                processor.incrementClassesScannedCount();
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static class ScanExperimentalAnnotationsProcessorPoc2 implements DeploymentUnitProcessor {

        private final ByteRuntimeIndex runtimeIndex;

        private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
        private static final String INDEX_FILE = "index.txt";


        private ScanExperimentalAnnotationsProcessorPoc2(RunningMode runningMode) {
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

            final DeploymentUnit du = phaseContext.getDeploymentUnit();
            DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);



            // Hacky cast, only because we are switching between different POCs
            JandexCollector collector = (JandexCollector) top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER);
            if (collector == null) {
                collector = new JandexCollector(runtimeIndex);
                top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER, collector);
            }
            ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment processor = top.getAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT);
            if (processor == null) {
                processor = new ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment();
                top.putAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT, processor);
            }

            Indexer indexer = new Indexer(collector);
            // TODO the Jandex scanning scans a lot more stuff, do we need to do that too?
            ServerLogger.DEPLOYMENT_LOGGER.infof("=====> Scanning deployment with POC 2");
            List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
            for (ResourceRoot root : resourceRoots) {
                try {
                    List<VirtualFile> files = root.getRoot().getChildrenRecursively();
                    for (VirtualFile file : files) {
                        if (file.isFile() && file.getName().endsWith(".class")) {
                            try (InputStream in = file.openStream()) {
                                indexer.index(in);
                                processor.incrementClassesScannedCount();
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static class ScanExperimentalAnnotationsProcessorPoc3 implements DeploymentUnitProcessor {

        private final ByteRuntimeIndex runtimeIndex;

        private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
        private static final String INDEX_FILE = "index.txt";


        private ScanExperimentalAnnotationsProcessorPoc3(RunningMode runningMode) {
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

            final DeploymentUnit du = phaseContext.getDeploymentUnit();
            DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);



            // Hacky cast, only because we are switching between different POCs
            FastClassInfoScanner scanner = (FastClassInfoScanner) top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER);
            if (scanner == null) {
                scanner = new FastClassInfoScanner(runtimeIndex);
                top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_USAGE_REPORTER, scanner);
            }
            ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment processor = top.getAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT);
            if (processor == null) {
                processor = new ReportExperimentalAnnotationsProcessor.ExperimentalAnnotationsAttachment();
                top.putAttachment(ReportExperimentalAnnotationsProcessor.ATTACHMENT, processor);
            }

            // TODO the Jandex scanning scans a lot more stuff, do we need to do that too?
            ServerLogger.DEPLOYMENT_LOGGER.infof("=====> Scanning deployment with POC 3");
            List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
            for (ResourceRoot root : resourceRoots) {
                try {
                    List<VirtualFile> files = root.getRoot().getChildrenRecursively();
                    for (VirtualFile file : files) {
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
        }
    }
}
