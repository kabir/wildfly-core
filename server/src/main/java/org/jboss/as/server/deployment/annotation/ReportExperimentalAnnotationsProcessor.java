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

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.logging.ServerLogger;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedAnnotation;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedClassUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedFieldReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedMethodReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotationUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassInfoScanner;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ExtendsAnnotatedClass;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ImplementsAnnotatedInterface;

import java.util.Set;

public class ReportExperimentalAnnotationsProcessor implements DeploymentUnitProcessor {

    static final AttachmentKey<ExperimentalAnnotationsAttachment> ATTACHMENT = AttachmentKey.create(ExperimentalAnnotationsAttachment.class);
    public static class ExperimentalAnnotationsAttachment {
        private final long startTime = System.currentTimeMillis();

        private volatile int classesScannedCount = 0;

        public ExperimentalAnnotationsAttachment() {
            ServerLogger.DEPLOYMENT_LOGGER.infof("TMP - creating attachment");
        }

        void incrementClassesScannedCount() {
            classesScannedCount++;
        }

        public int getClassesScannedCount() {
            return classesScannedCount;
        }
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
        final DeploymentUnit du = phaseContext.getDeploymentUnit();
        DeploymentUnit top = du.getParent() == null ? du : du.getParent();
        ClassInfoScanner reporter = top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_SCANNER);
        if (reporter == null) {
            return;
        }

        // ScanExperimentalAnnotationsProcessor has looked for class, interface, method and field usage where those
        // parts have been annotated with an annotation flagged as experimental.
        // The finale part is to check the annotations indexed by Jandex
        CompositeIndex index = du.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        reporter.checkAnnotationIndex(annotationName -> index.getAnnotations(annotationName));
        ExperimentalAnnotationsAttachment attachment = top.getAttachment(ATTACHMENT);
        if (attachment == null) {
            return;
        }
        ServerLogger.DEPLOYMENT_LOGGER.infof("===> Scan took %d ms, and scanned %d classes",
                System.currentTimeMillis() - attachment.startTime,
                attachment.classesScannedCount);

        // TODO We could do something here to group things a bit more ordered
        Set<AnnotationUsage> usages = reporter.getUsages();

        if (usages.size() > 0) {

            System.out.println();
            System.out.println("=========>>>>>>>> OUTPUT FOR DEMO <<<<<<<<=========");
            System.out.println();

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
                        AnnotatedClassUsage ref = usage.asAnnotatedClassUsage();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "%s references class %s which has been annotated with %s", ref.getSourceClass(), ref.getReferencedClass(), ref.getAnnotations());
                    }
                    break;
                    case ANNOTATION_USAGE: {
                        AnnotatedAnnotation ref = usage.asAnnotatedAnnotation();
                        ServerLogger.DEPLOYMENT_LOGGER.infof(
                                "The deployment uses the following unsupported annotations %s", ref.getAnnotations());
                    }
                    break;
                }

            }
        }
    }

}