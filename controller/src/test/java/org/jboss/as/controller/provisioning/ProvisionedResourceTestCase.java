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

package org.jboss.as.controller.provisioning;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROVISION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.TestModelControllerService;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.operations.global.ResourceProvisioningHandler;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProvisionedResourceTestCase {

    static final SimpleAttributeDefinition REQUIRED = SimpleAttributeDefinitionBuilder.create("required", ModelType.STRING, false).build();

    static final SimpleAttributeDefinition TIME = ObjectTypeAttributeDefinition.Builder.of("time",
            SimpleAttributeDefinitionBuilder.create("value", ModelType.INT, false).build(),
            SimpleAttributeDefinitionBuilder.create("unit", ModelType.STRING, false).build())
            .setRequired(false)
            .build();

    static final SimpleAttributeDefinition DEFAULT = SimpleAttributeDefinitionBuilder.create("default", ModelType.STRING, false)
            .setDefaultValue(new ModelNode("The default"))
            .build();

    private List<ModelNode> bootOperations;

    private ServiceContainer container;
    private ProvisioningTestControllerService controllerService;
    private ModelController controller;
    private AtomicBoolean sharedState;

    @Before
    public void beforeTest() {
        bootOperations = new ArrayList<>();
    }

    @After
    public void afterTest() {
        container.shutdown();
        container = null;
        controller = null;
    }

    @Test
    public void testProvisioningNotSetup() throws Exception {
        setupController();

        final ModelNode provision = Util.createEmptyOperation(PROVISION_OPERATION, PathAddress.EMPTY_ADDRESS);
        ModelNode result = execute(provision);
        System.out.println(result);
        Assert.assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    @Test
    public void testProvisioningSetup() throws Exception {
        System.setProperty(ProvisionedResourceInfoCollector.PROPERTY, "true");
        try {
            setupController();

            final ModelNode provision = Util.createEmptyOperation(PROVISION_OPERATION, PathAddress.EMPTY_ADDRESS);
            ModelNode result = execute(provision);
            System.out.println(result);
            Assert.assertEquals(SUCCESS, result.get(OUTCOME).asString());

            ProvisionedResourceAssembler assembler = ProvisionedResourceAssembler.read(result.get(RESULT));
            ModelNode readModel = createResourceNode(assembler.getRootResource());

            //Check the model is the right shape
            final ModelNode readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
            readResource.get(RECURSIVE).set(true);
            result = execute(readResource);
            System.out.println(result);
            Assert.assertEquals(result.get(RESULT), readModel);

            //Check the order of the addresses
            List<PathAddress> addresses = assembler.getAddresses();
            Assert.assertEquals(8, addresses.size());

            PathAddress[] expected ={
                    PathAddress.pathAddress("child1", "first"),
                    PathAddress.pathAddress("child1", "second"),
                    PathAddress.pathAddress("child1", "first").append("sub1", "b"),
                    PathAddress.pathAddress("child1", "first").append("sub1", "a"),
                    PathAddress.pathAddress("child1", "first").append("sub2", "a"),
                    PathAddress.pathAddress("child1", "second").append("sub1", "a"),
                    PathAddress.pathAddress("child1", "second").append("sub2", "b"),
                    PathAddress.pathAddress("child1", "second").append("sub2", "a")};
            Assert.assertEquals(Arrays.asList(expected), addresses);
        } finally {
            System.clearProperty(ProvisionedResourceInfoCollector.PROPERTY);
        }


        final ModelNode readResource = Util.createEmptyOperation(READ_RESOURCE_OPERATION, PathAddress.EMPTY_ADDRESS);
        readResource.get(RECURSIVE).set(true);
        ModelNode result = execute(readResource);
        System.out.println(result);
    }

    private ModelNode createResourceNode(Resource root) {
        ModelNode rootNode = new ModelNode();
        rootNode.set(root.getModel());
        addResourcesRecursively(rootNode, root);
        return rootNode;
    }

    private void addResourcesRecursively(ModelNode parentNode, Resource parentResource) {
        for (String type : parentResource.getChildTypes()) {
            for (String name : parentResource.getChildrenNames(type)) {
                Resource childResource = parentResource.getChild(PathElement.pathElement(type, name));
                ModelNode childNode = parentNode.get(type, name).set(childResource.getModel());
                addResourcesRecursively(childNode, childResource);
            }
        }
    }

    private ModelNode execute(ModelNode op) throws Exception {
        return controller.execute(op, null, null, null);
    }

    private void initModel(ManagementModel managementModel) {
        managementModel.getRootResource().getModel().setEmptyObject();


        ManagementResourceRegistration root = managementModel.getRootResourceRegistration();
        ManagementResourceRegistration child =
                root.registerSubModel(new TestResourceDefinition(PathElement.pathElement("child1"), REQUIRED, TIME, DEFAULT));

        child.registerSubModel(new TestResourceDefinition(PathElement.pathElement("sub1"), DEFAULT));
        child.registerSubModel(new TestResourceDefinition(PathElement.pathElement("sub2"), DEFAULT));
    }

    private void setupController() throws InterruptedException {
        List<ModelNode> bootOperations = new ArrayList<>();

        ModelNode add = Util.createAddOperation(PathAddress.pathAddress("child1", "first"));
        add.get("required").set("one");
        add.get("time", "value").set("50");
        add.get("time", "unit").set("years");
        add.get("default").set("here we go");
        bootOperations.add(add);

        add = Util.createAddOperation(PathAddress.pathAddress("child1", "second"));
        add.get("required").set("two");
        add.get("time", "value").set("10");
        add.get("time", "unit").set("seconds");
        bootOperations.add(add);

        add = Util.createAddOperation(PathAddress.pathAddress("child1", "first").append("sub1", "b"));
        bootOperations.add(add);
        add = Util.createAddOperation(PathAddress.pathAddress("child1", "first").append("sub1", "a"));
        add.get("default").set("not lazy");
        bootOperations.add(add);
        add = Util.createAddOperation(PathAddress.pathAddress("child1", "first").append("sub2", "a"));
        add.get("default").set("lots of effort");
        bootOperations.add(add);

        add = Util.createAddOperation(PathAddress.pathAddress("child1", "second").append("sub1", "a"));
        bootOperations.add(add);
        add = Util.createAddOperation(PathAddress.pathAddress("child1", "second").append("sub2", "b"));
        bootOperations.add(add);
        add = Util.createAddOperation(PathAddress.pathAddress("child1", "second").append("sub2", "a"));
        add.get("default").set("more stuff");
        bootOperations.add(add);






        setupController(bootOperations);
    }

    private void setupController(List<ModelNode> bootOperations) throws InterruptedException {
        System.out.println("=========  New Test \n");

        for (ModelNode bootOp : bootOperations) {
            this.bootOperations.add(bootOp.clone()); // clone so we don't have to worry about mutated ops when we compare
        }

        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();
        controllerService = new ProvisioningTestControllerService();
        ServiceBuilder<ModelController> builder = target.addService(ServiceName.of("ModelController"), controllerService);
        builder.install();
        sharedState = controllerService.getSharedState();
        controllerService.awaitStartup(30, TimeUnit.SECONDS);
        controller = controllerService.getValue();
        ModelNode setup = Util.getEmptyOperation("setup", new ModelNode());
        controller.execute(setup, null, null, null);

        assertEquals(ControlledProcessState.State.RUNNING, controllerService.getCurrentProcessState());
    }

    private class RootResourceDefinition extends SimpleResourceDefinition {
        RootResourceDefinition() {
            super(new Parameters(PathElement.pathElement("root"), new NonResolvingResourceDescriptionResolver())
                    .setAddHandler(new AbstractAddStepHandler() {})
                    .setRemoveHandler(new AbstractRemoveStepHandler() {}));
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            GlobalOperationHandlers.registerGlobalOperations(resourceRegistration, ProcessType.EMBEDDED_SERVER);
            resourceRegistration.registerOperationHandler(
                    ResourceProvisioningHandler.DEFINITION, new ResourceProvisioningHandler(controllerService.getProvisionedResourceInfoCollector()));
        }
    }

    private static class TestResourceDefinition extends SimpleResourceDefinition {

        private final AttributeDefinition[] attributes;

        TestResourceDefinition(PathElement pathElement, AttributeDefinition...attributes) {
            super(new Parameters(pathElement, new NonResolvingResourceDescriptionResolver())
                    .setAddHandler(new ModelOnlyAddStepHandler(attributes) {})
                    .setRemoveHandler(new ModelOnlyRemoveStepHandler() {}));
            this.attributes = attributes;
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            super.registerAttributes(resourceRegistration);
            ModelOnlyWriteAttributeHandler write = new ModelOnlyWriteAttributeHandler(attributes);
            for (AttributeDefinition def : attributes) {
                resourceRegistration.registerReadWriteAttribute(def, null, write);
            }
        }
    }

    private class ProvisioningTestControllerService extends TestModelControllerService {

        ProvisioningTestControllerService() {
            super(ProcessType.EMBEDDED_SERVER, new NullConfigurationPersister(), new ControlledProcessState(true), new RootResourceDefinition());
        }
        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            ProvisionedResourceTestCase.this.initModel(managementModel);
        }

        @Override
        protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure)
                throws ConfigurationPersistenceException {
            List<ModelNode> bootOps = new ArrayList<>(bootOperations);
            try {
                for (ModelNode bootOp : ProvisionedResourceTestCase.this.bootOperations) {
                    bootOps.add(bootOp.clone()); // clone so we don't have to worry about mutated ops when we compare
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return super.boot(bootOps, rollbackOnRuntimeFailure);
        }

        @Override
        protected ProvisionedResourceInfoCollector getProvisionedResourceInfoCollector() {
            return super.getProvisionedResourceInfoCollector();
        }
    }
}
