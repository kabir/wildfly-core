/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2024 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.core.management.deployment;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.logging.Logger;
import org.wildfly.extension.core.management.UnstableApiAnnotationResourceDefinition.UnstableApiAnnotationLevel;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedClassUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedFieldReference;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotatedMethodReference;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotationOnUserClassUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotationOnUserFieldUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotationOnUserMethodUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.AnnotationUsage;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ClassInfoScanner;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ExtendsAnnotatedClass;
import org.wildfly.unstable.api.annotation.classpath.runtime.bytecode.ImplementsAnnotatedInterface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.wildfly.extension.core.management.logging.CoreManagementLogger.UNSUPPORTED_ANNOTATION_LOGGER;

public class ReportUnstableApiAnnotationsProcessor implements DeploymentUnitProcessor {

    private final Supplier<UnstableApiAnnotationLevel> levelSupplier;

    public ReportUnstableApiAnnotationsProcessor(Supplier<UnstableApiAnnotationLevel> levelSupplier) {
        this.levelSupplier = levelSupplier;
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
        ClassInfoScanner scanner = top.getAttachment(UnstableApiAnnotationAttachments.UNSTABLE_API_ANNOTATION_SCANNER);
        if (scanner == null) {
            return;
        }

        // ScanExperimentalAnnotationsProcessor has looked for class, interface, method and field usage where those
        // parts have been annotated with an annotation flagged as experimental.
        // The finale part is to check the annotations indexed by Jandex
        CompositeIndex index = du.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        scanner.checkAnnotationIndex(annotationName -> index.getAnnotations(annotationName));

        Set<AnnotationUsage> usages = scanner.getUsages();

        if (!usages.isEmpty()) {
            AnnotationUsages annotationUsages = AnnotationUsages.parseAndGroup(scanner.getUsages());
            AnnotationUsageReporter reporter = getAnnotationUsageReporter(phaseContext, top);
            if (reporter.isEnabled()) {
                reportAnnotationUsages(top, annotationUsages, reporter);
            }
        }
    }

    private void reportAnnotationUsages(DeploymentUnit top, AnnotationUsages annotationUsages, AnnotationUsageReporter reporter) throws DeploymentUnitProcessingException {
        reporter.header(UNSUPPORTED_ANNOTATION_LOGGER.deploymentContainsUnstableApiAnnotations(top.getName()));
        for (ExtendsAnnotatedClass ext : annotationUsages.extendsAnnotatedClasses) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classExtendsClassWithUnstableApiAnnotations(
                            ext.getSourceClass(),
                            ext.getSuperClass(),
                            ext.getAnnotations()));
        }
        for (ImplementsAnnotatedInterface imp : annotationUsages.implementsAnnotatedInterfaces) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classImplementsInterfaceWithUnstableApiAnnotations(
                            imp.getSourceClass(),
                            imp.getInterface(),
                            imp.getAnnotations()));
        }
        for (AnnotatedFieldReference ref : annotationUsages.annotatedFieldReferences) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesFieldWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getFieldClass(),
                            ref.getFieldName(),
                            ref.getAnnotations()));
        }
        for (AnnotatedMethodReference ref : annotationUsages.annotatedMethodReferences) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesMethodWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getMethodClass(),
                            ref.getMethodName(),
                            ref.getDescriptor(),
                            ref.getAnnotations()));
        }
        for (AnnotatedClassUsage ref : annotationUsages.annotatedClassUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classReferencesClassWithUnstableApiAnnotations(
                            ref.getSourceClass(),
                            ref.getReferencedClass(),
                            ref.getAnnotations()));
        }
        for (AnnotationOnUserClassUsage ref : annotationUsages.annotationOnUserClassUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.classIsAnnotatedWithUnstableApiAnnotation(
                            ref.getClazz(), ref.getAnnotations()));

        }
        for (AnnotationOnUserFieldUsage ref : annotationUsages.annotationOnUserFieldUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.fieldIsAnnotatedWithUnstableApiAnnotation(
                            ref.getClazz(),
                            ref.getFieldName(),
                            ref.getAnnotations()));

        }
        for (AnnotationOnUserMethodUsage ref : annotationUsages.annotationOnUserMethodUsages) {
            reporter.reportAnnotationUsage(
                    UNSUPPORTED_ANNOTATION_LOGGER.methodIsAnnotatedWithUnstableApiAnnotation(
                            ref.getClazz(),
                            ref.getMethodName(),
                            ref.getDescriptor(),
                            ref.getAnnotations()));
        }

        reporter.complete();
    }

    private AnnotationUsageReporter getAnnotationUsageReporter(DeploymentPhaseContext ctx, DeploymentUnit top) throws DeploymentUnitProcessingException {
        UnstableApiAnnotationLevel level = levelSupplier.get();
        if (level == UnstableApiAnnotationLevel.ERROR) {
            return new ErrorAnnotationUsageReporter();
        }
        return new WarningAnnotationUsageReporter();
    }


    private static class AnnotationUsages {


        private final List<ExtendsAnnotatedClass> extendsAnnotatedClasses;
        private final List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces;
        private final List<AnnotatedFieldReference> annotatedFieldReferences;
        private final List<AnnotatedMethodReference> annotatedMethodReferences;
        private final List<AnnotatedClassUsage> annotatedClassUsages;
        private final List<AnnotationOnUserClassUsage> annotationOnUserClassUsages;
        private final List<AnnotationOnUserFieldUsage> annotationOnUserFieldUsages;
        private final List<AnnotationOnUserMethodUsage> annotationOnUserMethodUsages;

        public AnnotationUsages(List<ExtendsAnnotatedClass> extendsAnnotatedClasses,
                                List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces,
                                List<AnnotatedFieldReference> annotatedFieldReferences,
                                List<AnnotatedMethodReference> annotatedMethodReferences,
                                List<AnnotatedClassUsage> annotatedClassUsages,
                                List<AnnotationOnUserClassUsage> annotationOnUserClassUsages,
                                List<AnnotationOnUserFieldUsage> annotationOnUserFieldUsages,
                                List<AnnotationOnUserMethodUsage> annotationOnUserMethodUsages) {

            this.extendsAnnotatedClasses = extendsAnnotatedClasses;
            this.implementsAnnotatedInterfaces = implementsAnnotatedInterfaces;
            this.annotatedFieldReferences = annotatedFieldReferences;
            this.annotatedMethodReferences = annotatedMethodReferences;
            this.annotatedClassUsages = annotatedClassUsages;
            this.annotationOnUserClassUsages = annotationOnUserClassUsages;
            this.annotationOnUserFieldUsages = annotationOnUserFieldUsages;
            this.annotationOnUserMethodUsages = annotationOnUserMethodUsages;
        }

        static AnnotationUsages parseAndGroup(Set<AnnotationUsage> usages) {
            List<ExtendsAnnotatedClass> extendsAnnotatedClasses = new ArrayList<>();
            List<ImplementsAnnotatedInterface> implementsAnnotatedInterfaces = new ArrayList<>();
            List<AnnotatedFieldReference> annotatedFieldReferences = new ArrayList<>();
            List<AnnotatedMethodReference> annotatedMethodReferences = new ArrayList<>();
            List<AnnotatedClassUsage> annotatedClassUsages = new ArrayList<>();
            List<AnnotationOnUserClassUsage> annotationOnUserClassUsages = new ArrayList<>();
            List<AnnotationOnUserFieldUsage> annotationOnUserFieldUsages = new ArrayList<>();
            List<AnnotationOnUserMethodUsage> annotationOnUserMethodUsages = new ArrayList<>();
            for (AnnotationUsage usage : usages) {
                switch (usage.getType()) {
                    case EXTENDS_CLASS: {
                        ExtendsAnnotatedClass ext = usage.asExtendsAnnotatedClass();
                        extendsAnnotatedClasses.add(ext);
                    }
                    break;
                    case IMPLEMENTS_INTERFACE: {
                        ImplementsAnnotatedInterface imp = usage.asImplementsAnnotatedInterface();
                        implementsAnnotatedInterfaces.add(imp);
                    }
                    break;
                    case FIELD_REFERENCE: {
                        AnnotatedFieldReference ref = usage.asAnnotatedFieldReference();
                        annotatedFieldReferences.add(ref);
                    }
                    break;
                    case METHOD_REFERENCE: {
                        AnnotatedMethodReference ref = usage.asAnnotatedMethodReference();
                        annotatedMethodReferences.add(ref);
                    }
                    break;
                    case CLASS_USAGE: {
                        AnnotatedClassUsage ref = usage.asAnnotatedClassUsage();
                        annotatedClassUsages.add(ref);
                    }
                    break;
                    case ANNOTATED_USER_CLASS: {
                        AnnotationOnUserClassUsage ref = usage.asAnnotationOnUserClassUsage();
                        annotationOnUserClassUsages.add(ref);
                    }
                    break;
                    case ANNOTATED_USER_METHOD: {
                        AnnotationOnUserMethodUsage ref = usage.asAnnotationOnUserMethodUsage();
                        annotationOnUserMethodUsages.add(ref);
                    }
                    break;
                    case ANNOTATED_USER_FIELD: {
                        AnnotationOnUserFieldUsage ref = usage.asAnnotationOnUserFieldUsage();
                        annotationOnUserFieldUsages.add(ref);
                    }
                    break;
                }
            }
            extendsAnnotatedClasses.sort(new Comparator<>() {
                @Override
                public int compare(ExtendsAnnotatedClass o1, ExtendsAnnotatedClass o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getSuperClass().compareTo(o2.getSuperClass());
                    }

                    return i;
                }
            });
            implementsAnnotatedInterfaces.sort(new Comparator<>() {
                @Override
                public int compare(ImplementsAnnotatedInterface o1, ImplementsAnnotatedInterface o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getInterface().compareTo(o2.getInterface());
                    }

                    return i;
                }
            });
            annotatedFieldReferences.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedFieldReference o1, AnnotatedFieldReference o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getFieldClass().compareTo(o2.getFieldClass());
                        if (i == 0) {
                            i = o1.getFieldName().compareTo(o2.getFieldName());
                        }
                    }
                    return i;
                }
            });
            annotatedMethodReferences.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedMethodReference o1, AnnotatedMethodReference o2) {
                    int i = o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getMethodClass().compareTo(o2.getMethodClass());
                        if (i == 0) {
                            i = o1.getMethodName().compareTo(o2.getMethodName());
                            if (i == 0) {
                                i = o1.getDescriptor().compareTo(o2.getDescriptor());
                            }
                        }
                    }
                    return i;
                }
            });
            annotatedClassUsages.sort(new Comparator<>() {
                @Override
                public int compare(AnnotatedClassUsage o1, AnnotatedClassUsage o2) {
                    int i =  o1.getSourceClass().compareTo(o2.getSourceClass());
                    if (i == 0) {
                        i = o1.getReferencedClass().compareTo(o2.getReferencedClass());
                    }
                    return i;
                }
            });
            annotationOnUserClassUsages.sort(new Comparator<>(){
                @Override
                public int compare(AnnotationOnUserClassUsage o1, AnnotationOnUserClassUsage o2) {
                    return o1.getClazz().compareTo(o2.getClazz());
                }
            });
            annotationOnUserFieldUsages.sort(new Comparator<>(){
                @Override
                public int compare(AnnotationOnUserFieldUsage o1, AnnotationOnUserFieldUsage o2) {
                    int i =  o1.getClazz().compareTo(o2.getClazz());
                    if (i == 0) {
                        i = o1.getFieldName().compareTo(o2.getFieldName());
                    }
                    return i;
                }

            });
            annotationOnUserMethodUsages.sort(new Comparator<>(){
                @Override
                public int compare(AnnotationOnUserMethodUsage o1, AnnotationOnUserMethodUsage o2) {
                    int i =  o1.getClazz().compareTo(o2.getClazz());
                    if (i == 0) {
                        i = o1.getMethodName().compareTo(o2.getMethodName());
                    }
                    if (i == 0) {
                        i = o1.getDescriptor().compareTo(o2.getDescriptor());
                    }
                    return i;
                }

            });

            return new AnnotationUsages(extendsAnnotatedClasses,
                    implementsAnnotatedInterfaces,
                    annotatedFieldReferences,
                    annotatedMethodReferences,
                    annotatedClassUsages,
                    annotationOnUserClassUsages,
                    annotationOnUserFieldUsages,
                    annotationOnUserMethodUsages);
        }
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