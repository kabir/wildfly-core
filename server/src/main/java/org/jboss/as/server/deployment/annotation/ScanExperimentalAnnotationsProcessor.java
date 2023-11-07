/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2022 Red Hat, Inc., and individual contributors
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
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;
import org.wildfly.experimental.api.classpath.index.RuntimeIndex;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ScanExperimentalAnnotationsProcessor implements DeploymentUnitProcessor {

    private final RuntimeIndex runtimeIndex;

    private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-api-index";
    private static final String INDEX_FILE = "index.txt";


    public ScanExperimentalAnnotationsProcessor(RunningMode runningMode) {
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
                        if (fileName.length() > 0) {
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
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        if (runtimeIndex == null) {
            return;
        }

        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = DeploymentUtils.getTopDeploymentUnit(du);

        ClassBytecodeInspector inspector = top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_INSPECTOR);
        if (inspector == null) {
            inspector = new ClassBytecodeInspector(runtimeIndex);
            top.putAttachment(Attachments.EXPERIMENTAL_ANNOTATION_INSPECTOR, inspector);
        }

        // TODO the Jandex scanning scans a lot more stuff, do we need to do that too?

        List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(du);
        for (ResourceRoot root : resourceRoots) {
            try {
                List<VirtualFile> files = root.getRoot().getChildrenRecursively();
                for (VirtualFile file : files) {
                    if (file.isFile() && file.getName().endsWith(".class")) {
                        try (InputStream in = file.openStream()) {
                            inspector.scanClassFile(in);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}