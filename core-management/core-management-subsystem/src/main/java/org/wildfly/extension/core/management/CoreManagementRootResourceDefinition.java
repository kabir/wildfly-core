/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;


import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.core.management.deployment.ReportUnstableApiAnnotationsProcessor;
import org.wildfly.extension.core.management.deployment.ScanUnstableApiAnnotationsProcessor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the core-management subsystem root resource.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
class CoreManagementRootResourceDefinition extends PersistentResourceDefinition {

    CoreManagementRootResourceDefinition() {
        super(CoreManagementExtension.SUBSYSTEM_PATH,
                CoreManagementExtension.getResourceDescriptionResolver(),
                new CoreManagementAddHandler(),
                ReloadRequiredRemoveStepHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    @Override
    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Arrays.asList(ConfigurationChangeResourceDefinition.INSTANCE,
                new ProcessStateListenerResourceDefinition(),
                UnstableApiAnnotationResourceDefinition.INSTANCE
        );
    }

    private static class CoreManagementAddHandler extends AbstractBoottimeAddStepHandler {

        @Override
        protected void performBoottime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            if (context.isNormalServer()) {
                context.addStep(new AbstractDeploymentChainStep() {
                    @Override
                    protected void execute(DeploymentProcessorTarget processorTarget) {
                        processorTarget.addDeploymentProcessor(CoreManagementExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_SCAN_EXPERIMENTAL_ANNOTATIONS, new ScanUnstableApiAnnotationsProcessor(context.getRunningMode(), context.getStability()));
                        processorTarget.addDeploymentProcessor(CoreManagementExtension.SUBSYSTEM_NAME, Phase.PARSE, Phase.PARSE_REPORT_EXPERIMENTAL_ANNOTATIONS, new ReportUnstableApiAnnotationsProcessor());
                    }
                }, OperationContext.Stage.RUNTIME);
            }
        }
    }
}
