/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.suites;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.domain.extension.ExtensionSetup;
import org.jboss.as.test.integration.domain.extension.OrderedChildResourceExtension;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests management of extensions.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OrderedChildResourcesTestCase {

    private static final PathAddress EXTENSION_ADDRESS = PathAddress.pathAddress(EXTENSION, OrderedChildResourceExtension.MODULE_NAME);
    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PathAddress.pathAddress(PROFILE, "default"), OrderedChildResourceExtension.SUBSYSTEM_PATH);
    private static final PathAddress MASTER_SERVER_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(HOST, "master"),
            PathElement.pathElement(SERVER, "main-one"), OrderedChildResourceExtension.SUBSYSTEM_PATH);
    private static final PathAddress SLAVE_SERVER_SUBSYSTEM_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(HOST, "slave"),
            PathElement.pathElement(SERVER, "main-three"), OrderedChildResourceExtension.SUBSYSTEM_PATH);

    private static DomainTestSupport testSupport;
    private static DomainLifecycleUtil domainMasterLifecycleUtil;
    private static DomainLifecycleUtil domainSlaveLifecycleUtil;

    @BeforeClass
    public static void setupDomain() throws Exception {
        testSupport = DomainTestSuite.createSupport(OrderedChildResourcesTestCase.class.getSimpleName());
        domainMasterLifecycleUtil = testSupport.getDomainMasterLifecycleUtil();
        domainSlaveLifecycleUtil = testSupport.getDomainSlaveLifecycleUtil();
        // Initialize the test extension
        ExtensionSetup.initializeOrderedChildResourceExtension(testSupport);
    }

    @AfterClass
    public static void tearDownDomain() throws Exception {
        testSupport = null;
        domainMasterLifecycleUtil = null;
        domainSlaveLifecycleUtil = null;
        DomainTestSuite.stopSupport();
    }

    @Test
    public void testOrderedExtension() throws Exception  {
        //Add the extension
        DomainClient masterClient = domainMasterLifecycleUtil.getDomainClient();
        DomainTestUtils.executeForResult(Util.createAddOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), masterClient);
        try {
            //Add the subsystem
            DomainTestUtils.executeForResult(Util.createAddOperation(SUBSYSTEM_ADDRESS), masterClient);
            try {
                DomainClient slaveClient = domainSlaveLifecycleUtil.getDomainClient();
                checkDomainChildOrder(masterClient);
                compareSubsystemModels(masterClient, slaveClient);

                //Now add some child resources
                addChild(masterClient, "Z", -1);
                checkDomainChildOrder(masterClient, "Z");
                compareSubsystemModels(masterClient, slaveClient);

                //Try an indexed add
                addChild(masterClient, "N", 0);
                checkDomainChildOrder(masterClient, "N", "Z");
                compareSubsystemModels(masterClient, slaveClient);


            } finally {
                DomainTestUtils.executeForResult(Util.createRemoveOperation(SUBSYSTEM_ADDRESS), masterClient);
            }

        } finally {
            DomainTestUtils.executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(EXTENSION_ADDRESS)), masterClient);
        }
    }

    private ModelNode compareSubsystemModels(DomainClient masterClient, DomainClient slaveClient) throws Exception {
        final ModelNode domain = DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), masterClient);

        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS), slaveClient));
        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(MASTER_SERVER_SUBSYSTEM_ADDRESS), masterClient));
        Assert.assertEquals(domain, DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(SLAVE_SERVER_SUBSYSTEM_ADDRESS), masterClient));

        return domain;
    }

    private ModelNode getRecursiveReadResourceOperation(PathAddress address) {
        final ModelNode op = Util.createOperation(READ_RESOURCE_OPERATION, address);
        op.get(RECURSIVE).set(true);
        return op;
    }

    private void addChild(DomainClient masterClient, String childName, int index) throws Exception {
        final ModelNode op =  Util.createAddOperation(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(OrderedChildResourceExtension.CHILD.getKey(), childName)));
        op.get("attr").set(childName.toLowerCase());
        if (index >= 0) {
            op.get(ADD_INDEX).set(index);
        }
        DomainTestUtils.executeForResult(op, masterClient);
    }

    private void checkDomainChildOrder(DomainClient masterClient, String...childNames ) throws Exception {
        final ModelNode op = getRecursiveReadResourceOperation(SUBSYSTEM_ADDRESS);
        final ModelNode result = DomainTestUtils.executeForResult(getRecursiveReadResourceOperation(MASTER_SERVER_SUBSYSTEM_ADDRESS), masterClient);
        if (childNames.length > 0) {
            Assert.assertTrue(result.hasDefined(OrderedChildResourceExtension.CHILD.getKey()));
            Set<String> childKeys = result.get(OrderedChildResourceExtension.CHILD.getKey()).keys();
            String[] childKeyArray = childKeys.toArray(new String[childKeys.size()]);
            Assert.assertArrayEquals(childNames, childKeyArray);
        } else {
            Assert.assertFalse(result.hasDefined(OrderedChildResourceExtension.CHILD.getKey()));
        }
    }
}
