/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESSES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CAPABILITIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVISION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.capability.ProvisionCapablitiesUtil;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.provisioning.ProvisionedResourceInfoCollector;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Operation handler used to gather provisioning information
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerProvisioningHandler implements OperationStepHandler {

    private final ProvisionedResourceInfoCollector provisionedResourceInfoCollector;
    private final CapabilityRegistry capabilityRegistry;

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(PROVISION_OPERATION, ControllerResolver.getResolver("global"))
            .setReadOnly()
            .setRuntimeOnly()
            .setReplyType(ModelType.OBJECT)
            .build();

    public ServerProvisioningHandler(ProvisionedResourceInfoCollector provisionedResourceInfoCollector, CapabilityRegistry capabilityRegistry) {
        this.provisionedResourceInfoCollector = provisionedResourceInfoCollector;
        this.capabilityRegistry = capabilityRegistry;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        if (!provisionedResourceInfoCollector.isProvisioning()) {
            context.setRollbackOnly();
            context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.notRunningInProvisioningMode());
            return;
        }
        final ModelNode addresses = new ModelNode().setEmptyList();
        for (PathAddress address : provisionedResourceInfoCollector.getAddresses()) {
            //No need to synchronize the addresses here since we do not change the list after boot, and this operation is called after boot
            addresses.add(address.toCLIStyleString());
        }
        context.getResult().get(ADDRESSES).set(addresses);

        context.getResult().get(CAPABILITIES).set(ProvisionCapablitiesUtil.provisionCapabilities(capabilityRegistry));

        final ModelNode response = new ModelNode();
        final ModelNode rr = Util.createOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        rr.get(RECURSIVE).set(true);
        context.addStep(response, rr, ReadResourceHandler.INSTANCE, OperationContext.Stage.MODEL);

        context.completeStep(
                new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext ctx, ModelNode op) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            ImmutableManagementResourceRegistration registration = context.getRootResourceRegistration();
                            Map<PathAddress, ModelNode> modelMap = new LinkedHashMap<>();
                            createResourceTree(PathAddress.EMPTY_ADDRESS, registration, response.get(RESULT), modelMap);

                            ModelNode modelList = new ModelNode().setEmptyList();

                            for (Map.Entry<PathAddress, ModelNode> entry : modelMap.entrySet()) {
                                ModelNode resource = new ModelNode();
                                resource.get(ADDRESS).set(entry.getKey().toCLIStyleString());
                                resource.get(VALUE).set(entry.getValue());
                                modelList.add(resource);
                            }

                            context.getResult().get(RESOURCE).set(modelList);
                        }
                    }
                }
        );
    }

    private void createResourceTree(PathAddress address, ImmutableManagementResourceRegistration registration, ModelNode resourceNode, Map<PathAddress, ModelNode> modelMap) {
        ModelNode resourceModel = new ModelNode().setEmptyObject();
        Set<String> childKeys = new LinkedHashSet<>();
        Set<String> childTypes = registration.getChildNames(address);

        if (resourceNode.isDefined()) {
            for (String key : resourceNode.keys()) {
                if (childTypes.contains(key)) {
                    childKeys.add(key);
                } else {
                    resourceModel.get(key).set(resourceNode.get(key));
                }
            }
        }

        modelMap.put(address, resourceModel);

        for (String childKey : childKeys) {
            ModelNode childParent = resourceNode.get(childKey);
            if (childParent.isDefined()) {
                for (String childValue : childParent.keys()) {
                    PathElement element = PathElement.pathElement(childKey, childValue);
                    ModelNode childNode = childParent.get(childValue);
                    PathAddress childAddress = address.append(element);
                    createResourceTree(childAddress, registration, childNode, modelMap);
                }
            }
        }
    }
}
