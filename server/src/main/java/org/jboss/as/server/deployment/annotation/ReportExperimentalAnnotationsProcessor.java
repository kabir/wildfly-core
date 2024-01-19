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

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedAnnotation;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedClassUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedFieldReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotatedMethodReference;
import org.wildfly.experimental.api.classpath.runtime.bytecode.AnnotationUsage;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ClassInfoScanner;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ExtendsAnnotatedClass;
import org.wildfly.experimental.api.classpath.runtime.bytecode.ImplementsAnnotatedInterface;

import java.util.Set;

import static org.jboss.as.server.logging.ServerLogger.UNSUPPORTED_ANNOTATION_LOGGER;

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
        ClassInfoScanner scanner = top.getAttachment(Attachments.EXPERIMENTAL_ANNOTATION_SCANNER);
        if (scanner == null) {
            return;
        }

        // ScanExperimentalAnnotationsProcessor has looked for class, interface, method and field usage where those
        // parts have been annotated with an annotation flagged as experimental.
        // The finale part is to check the annotations indexed by Jandex
        CompositeIndex index = du.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        scanner.checkAnnotationIndex(annotationName -> index.getAnnotations(annotationName));
        ExperimentalAnnotationsAttachment attachment = top.getAttachment(ATTACHMENT);
        if (attachment == null) {
            return;
        }
        ServerLogger.DEPLOYMENT_LOGGER.infof("===> Scan took %d ms, and scanned %d classes",
                System.currentTimeMillis() - attachment.startTime,
                attachment.classesScannedCount);

        // TODO We could do something here to group things a bit more ordered
        Set<AnnotationUsage> usages = scanner.getUsages();

        if (!usages.isEmpty()) {

            AnnotationUsageReporter reporter = getAnnotationUsageReporter(top);
            if (reporter.isEnabled()) {
                reporter.header(UNSUPPORTED_ANNOTATION_LOGGER.deploymentContainsUnsupportedAnnotations(top.getName()));


                for (AnnotationUsage usage : usages) {
                    switch (usage.getType()) {
                        case EXTENDS_CLASS: {
                            ExtendsAnnotatedClass ext = usage.asExtendsAnnotatedClass();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.classExtendsClassWithUnsupportedAnnotations(
                                            ext.getSourceClass(),
                                            ext.getSuperClass(),
                                            ext.getAnnotations()));
                        } break;
                        case IMPLEMENTS_INTERFACE: {
                            ImplementsAnnotatedInterface imp = usage.asImplementsAnnotatedInterface();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.classImplementsInterfaceWithUnsupportedAnnotations(
                                            imp.getSourceClass(),
                                            imp.getInterface(),
                                            imp.getAnnotations()));
                        }
                        break;
                        case FIELD_REFERENCE: {
                            AnnotatedFieldReference ref = usage.asAnnotatedFieldReference();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesFieldWithUnsupportedAnnotations(
                                            ref.getSourceClass(),
                                            ref.getFieldClass(),
                                            ref.getFieldName(),
                                            ref.getAnnotations()));
                        }
                        break;
                        case METHOD_REFERENCE: {
                            AnnotatedMethodReference ref = usage.asAnnotatedMethodReference();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesMethodWithUnsupportedAnnotations(
                                            ref.getSourceClass(),
                                            ref.getMethodClass(),
                                            ref.getMethodName(),
                                            ref.getDescriptor(),
                                            ref.getAnnotations()));
                        }
                        break;
                        case CLASS_USAGE: {
                            AnnotatedClassUsage ref = usage.asAnnotatedClassUsage();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesClassWithUnsupportedAnnotations(
                                            ref.getSourceClass(),
                                            ref.getReferencedClass(),
                                            ref.getAnnotations()));
                        }
                        break;
                        case ANNOTATION_USAGE: {
                            AnnotatedAnnotation ref = usage.asAnnotatedAnnotation();
                            reporter.reportAnnotationUsage(
                                    UNSUPPORTED_ANNOTATION_LOGGER.deploymentClassesAnnotatedWithUnsupportedAnnotations(ref.getAnnotations()));
                        }
                        break;
                    }

                }
                reporter.complete();
            }
        }
    }


    private AnnotationUsageReporter getAnnotationUsageReporter(DeploymentUnit top) {
        ModelController controller = (ModelController) top.getServiceRegistry().getService(Services.JBOSS_SERVER_CONTROLLER).getValue();
        // Temporary experiment at reading the model until I add the model attribute
        ///core-service=management/access=authorization:read-attribute(name=permission-combination-policy)
        ModelNode op = Util.getReadAttributeOperation(PathAddress.pathAddress("core-service", "management").append("access", "authorization"), "permission-combination-policy");
        ModelNode result = controller.execute(op, null, ModelController.OperationTransactionControl.COMMIT, null);
        System.out.println("===== Reading model");
        System.out.println(result);
        // TODO return the value read from the model
        return new WarningAnnotationUsageReporter();
    }

    private interface AnnotationUsageReporter {
        void header(String message);

        void reportAnnotationUsage(String message);

        void complete() throws DeploymentUnitProcessingException;

        boolean isEnabled();
    }

    private class WarningAnnotationUsageReporter implements AnnotationUsageReporter {
        @Override
        public void header(String message) {
            UNSUPPORTED_ANNOTATION_LOGGER.warn(message);
        }

        @Override
        public void reportAnnotationUsage(String message) {
            UNSUPPORTED_ANNOTATION_LOGGER.warn(message);
        }

        @Override
        public void complete() throws DeploymentUnitProcessingException {

        }

        @Override
        public boolean isEnabled() {
            return UNSUPPORTED_ANNOTATION_LOGGER.isEnabled(Logger.Level.WARN);
        }
    }

    private class ErrorAnnotationUsageReporter implements AnnotationUsageReporter {
        private final StringBuilder sb = new StringBuilder();
        @Override
        public void header(String message) {
            sb.append(message);
        }

        @Override
        public void reportAnnotationUsage(String message) {
            sb.append("\n");
            sb.append("-");
            sb.append(message);
        }

        @Override
        public void complete() throws DeploymentUnitProcessingException {
            throw new DeploymentUnitProcessingException(sb.toString());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}