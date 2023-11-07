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
import org.jboss.as.server.logging.ServerLogger;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotatedFieldReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotatedMethodReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.AnnotationUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.ExtendsAnnotatedClass;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassBytecodeInspector.ImplementsAnnotatedInterface;

import java.util.Set;

public class ReportExperimentalAnnotationsProcessor implements DeploymentUnitProcessor {


    /**
     * Process this deployment for annotations.  This will use an annotation indexer to create an index of all annotations
     * found in this deployment and attach it to the deployment unit context.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = du.getParent() == null ? du : du.getParent();
        ClassBytecodeInspector inspector = top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_INSPECTOR);
        if (inspector == null) {
            return;
        }

        // ScanExperimentalAnnotationsProcessor has looked for class, interface, method and field usage where those
        // parts have been annotated with an annotation flagged as experimental.
        // The finale part is to check the annotations indexed by Jandex
        CompositeIndex index = du.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        inspector.checkAnnotationIndex(annotationName -> index.getAnnotations(annotationName));



        // TODO We could do something here to group things a bit more ordered
        Set<AnnotationUsage> usages = inspector.getUsages();

        if (usages.size() > 0) {
            // TODO Configure whether to give an error or warn

            // TODO i18n here and for the other messages
            ServerLogger.DEPLOYMENT_LOGGER.warn(top.getName() + " contains usage of annotations which indicate not fully supported API.");

            for (AnnotationUsage usage : usages) {
                switch (usage.getType()) {
                    case EXTENDS_CLASS: {
                        ExtendsAnnotatedClass ext = usage.asExtendsAnnotatedClass();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s extends %s which has been annotated with %s", ext.getSourceClass(), ext.getSuperClass(), ext.getAnnotations());
                    } break;
                    case IMPLEMENTS_INTERFACE: {
                        ImplementsAnnotatedInterface imp = usage.asImplementsAnnotatedInterface();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s implements %s which has been annotated with", imp.getSourceClass(), imp.getInterface(), imp.getAnnotations());
                    }
                    break;
                    case FIELD_REFERENCE: {
                        AnnotatedFieldReference ref = usage.asAnnotatedFieldReference();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s references field %s.%s which has been annotated with %s", ref.getSourceClass(), ref.getFieldClass(), ref.getFieldName(), ref.getAnnotations());
                    }
                    break;
                    case METHOD_REFERENCE: {
                        AnnotatedMethodReference ref = usage.asAnnotatedMethodReference();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s references method %s.%s%s which has been annotated with %s", ref.getSourceClass(), ref.getMethodClass(), ref.getMethodName(), ref.getDescriptor(), ref.getAnnotations());
                    }
                    break;
                    case CLASS_USAGE: {
                        ClassBytecodeInspector.AnnotatedClassUsage ref = usage.asAnnotatedClassUsage();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s references class %s which has been annotated with %s", ref.getSourceClass(), ref.getReferencedClass(), ref.getAnnotations());
                    }
                    break;
                    case ANNOTATION_USAGE: {
                        ClassBytecodeInspector.AnnotatedAnnotation ref = usage.asAnnotatedAnnotation();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "The deployment also uses the following unsupported annotations %s", ref.getAnnotations());
                    }
                    break;
                }

            }
        }
    }

}