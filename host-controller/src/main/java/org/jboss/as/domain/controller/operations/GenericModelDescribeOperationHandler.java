/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A generic model "describe" handler, returning a list of operations which is needed to create an equivalent model.
 *
 * @author Emanuel Muckenhuber
 */
public class GenericModelDescribeOperationHandler implements OperationStepHandler {

    public static final GenericModelDescribeOperationHandler INSTANCE = new GenericModelDescribeOperationHandler("describe-model", false);

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("describe-model", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    private final String operationName;
    private final boolean skipLocalAdd;
    protected GenericModelDescribeOperationHandler(final String operationName, final boolean skipAdd) {
        this.operationName = operationName;
        this.skipLocalAdd = skipAdd;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final PathAddressFilter filter = context.getAttachment(PathAddressFilter.KEY);
        if (filter != null && ! filter.accepts(address)) {
            context.stepCompleted();
            return;
        }
        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        if (registration.isAlias() || registration.isRemote() || registration.isRuntimeOnly()) {
            context.stepCompleted();
            return;
        }
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        final ModelNode result = context.getResult();
        result.setEmptyList();
        final ModelNode results = new ModelNode().setEmptyList();
        final AtomicReference<ModelNode> failureRef = new AtomicReference<ModelNode>();
        final Map<String, ModelNode> includeResults = new HashMap<String, ModelNode>();

        // Step to handle failed operations
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                boolean failed = false;
                if (failureRef.get() != null) {
                    // One of our subsystems failed
                    context.getFailureDescription().set(failureRef.get());
                    failed = true;
                } else {
                    for (final ModelNode includeRsp : includeResults.values()) {
                        if (includeRsp.hasDefined(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(includeRsp.get(FAILURE_DESCRIPTION));
                            failed = true;
                            break;
                        }
                        final ModelNode includeResult = includeRsp.get(RESULT);
                        if (includeResult.isDefined()) {
                            for (ModelNode op : includeResult.asList()) {
                                result.add(op);
                            }
                        }
                    }
                }
                if (!failed) {
                    for (final ModelNode childRsp : results.asList()) {
                        result.add(childRsp);
                    }
                    context.getResult().set(result);
                }
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL, true);

        final Set<String> children;
        if (address.size() == 0) {
            // TODO where to get the proper ordering !?
            List<String> s = Arrays.asList(SERVER_GROUP, PROFILE, SYSTEM_PROPERTY, PATH, INTERFACE, SOCKET_BINDING_GROUP, DEPLOYMENT, DEPLOYMENT_OVERLAY, MANAGEMENT_CLIENT_CONTENT, CORE_SERVICE, EXTENSION);
            children = new LinkedHashSet<>(s);
        } else {
            children = resource.getChildTypes();
        }

        for (final String childType : children) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {

                final PathElement childPE = entry.getPathElement();
                final PathAddress relativeChildAddress = PathAddress.EMPTY_ADDRESS.append(childPE);
                final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(relativeChildAddress);

                if (childRegistration.isRuntimeOnly()
                        || childRegistration.isRemote()
                        || childRegistration.isAlias()) {

                    continue;
                }

                final PathAddress absoluteChildAddr = address.append(childPE);
                // Skip ignored addresses
                if (filter != null && !filter.accepts(absoluteChildAddr)) {
                    continue;
                }

                final OperationStepHandler stepHandler = childRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, operationName);
                final ModelNode childRsp = new ModelNode();

                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        if (failureRef.get() == null) {
                            if (childRsp.hasDefined(FAILURE_DESCRIPTION)) {
                                failureRef.set(childRsp.get(FAILURE_DESCRIPTION));
                            } else if (childRsp.hasDefined(RESULT)) {
                                addChildOperation(address, childRsp.require(RESULT).asList(), results);
                            }
                        }
                        context.stepCompleted();
                    }
                }, OperationContext.Stage.MODEL, true);

                final ModelNode childOperation = operation.clone();
                childOperation.get(ModelDescriptionConstants.OP).set(operationName);
                childOperation.get(ModelDescriptionConstants.OP_ADDR).set(absoluteChildAddr.toModelNode());
                context.addStep(childRsp, childOperation, stepHandler, OperationContext.Stage.MODEL, true);
            }
        }

        if (resource.isProxy() || resource.isRuntime()) {
            context.stepCompleted();
            return;
        }

        if (address.size() == 0) {
            // Do we need to set any root attributes?
            result.setEmptyList();
        } else {
            // Generic operation generation
            final ModelNode model = resource.getModel();
            final OperationStepHandler addHandler = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
            if (addHandler != null) {

                final ModelNode add = new ModelNode();
                add.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
                add.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());
                final Set<String> attributes = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);
                for (final String attribute : attributes) {
                    if (!model.hasDefined(attribute)) {
                        continue;
                    }

                    // Process attributes
                    final AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                    if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                        add.get(attribute).set(model.get(attribute));
                    }
                }

                // Allow the profile describe handler to process profile includes
                processMore(context, operation, resource, address, includeResults);
                if (!skipLocalAdd) {
                    result.add(add);
                }

            } else {
                // Create write attribute operations
                final Set<String> attributes = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);
                for (final String attribute : attributes) {
                    if (!model.hasDefined(attribute)) {
                        continue;
                    }

                    // Process attributes
                    final AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                    if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                        final ModelNode writeAttribute = new ModelNode();
                        writeAttribute.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
                        writeAttribute.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());

                        writeAttribute.get(NAME).set(attribute);
                        writeAttribute.get(VALUE).set(model.get(attribute));
                        result.add(writeAttribute);
                    }
                }
            }
        }
        context.stepCompleted();
    }

    protected void addChildOperation(final PathAddress parent, final List<ModelNode> operations, ModelNode results) {
        for (final ModelNode operation : operations) {
            results.add(operation);
        }
    }

    protected void processMore(final OperationContext context, final ModelNode operation, final Resource resource, final PathAddress address, final Map<String, ModelNode> includeResults) throws OperationFailedException {

    }

}
