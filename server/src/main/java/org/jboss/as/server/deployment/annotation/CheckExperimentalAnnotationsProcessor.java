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

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

public class CheckExperimentalAnnotationsProcessor  implements DeploymentUnitProcessor {

    private static final String BASE_MODULE_NAME = "org.wildfly._internal.experimental-indices";
    private static final String INDEX_FILE = "experimental-index.txt";

    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        // Get the full list of experimental annotations
        Set<String> experimentalAnnotations = new HashSet<>();
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();

        // TODO This sucks and will need improving.... I had hoped that ModuleLoader.iterateModules(BASE_MODULE_NAME)
        // would have got all the child modules but it does not.
        // We can probably use the module.path system property and either search directories, or scan the jar
        // depending on how the modules are provided to discover the child modules
        String[] names = new String[]{
                BASE_MODULE_NAME + ".microprofile-feature-pack",
                BASE_MODULE_NAME + ".ee-feature-pack"};
        Set<String> moduleNames = new HashSet<>();
        for (String name : names) {
            try {
                moduleLoader.loadModule(name);
                moduleNames.add(name);
            } catch (Exception e) {
                ROOT_LOGGER.warn("Can't find module " + name);
            }
        }


        for (String name : moduleNames){
            System.out.println("Found experimental annotation module " + name);
            ROOT_LOGGER.debugf("Found experimental annotation module %s", name);

            Module module = null;
            try {
                module = moduleLoader.loadModule(name);
            } catch (ModuleLoadException e) {
                // Should not happen since we found the name by iterating
                throw new DeploymentUnitProcessingException(e);
            }
            ClassLoader cl = module.getClassLoader();
            InputStream stream = cl.getResourceAsStream(INDEX_FILE);
            if (stream == null) {
                throw new DeploymentUnitProcessingException("Module " + name + " does not contain a " + INDEX_FILE);
            }

            try {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    String annotationName = reader.readLine();
                    while (annotationName != null) {
                        experimentalAnnotations.add(annotationName);
                        annotationName = reader.readLine();
                    }
                }
            } catch (IOException e) {
                throw new DeploymentUnitProcessingException(e);
            }

        }

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        for (String annotation : experimentalAnnotations) {
            if (!index.getAnnotations(annotation).isEmpty()) {
                // Make the warning message stand out a bit more for demo purposes
                ROOT_LOGGER.warn("===========> The deployment contains an experimental annotation: " + annotation);

                // We could warn in WildFly as done here, and depending on what we want we could fail on EAP if users
                // are using such amnotation.
            }
        }
    }

}