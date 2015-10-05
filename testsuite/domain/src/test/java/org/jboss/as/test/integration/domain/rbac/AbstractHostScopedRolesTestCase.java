/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.rbac;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BASE_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOSTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROXIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_DESCRIPTION_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RECURSIVE_DEPTH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.IOException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Abstract superclass of access control provider test cases covering host scoped roles.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class AbstractHostScopedRolesTestCase extends AbstractRbacTestCase implements RbacDomainRolesTests {

    public static final String MASTER_MONITOR_USER = "HostMasterMonitor";
    public static final String MASTER_OPERATOR_USER = "HostMasterOperator";
    public static final String MASTER_MAINTAINER_USER = "HostMasterMaintainer";
    public static final String MASTER_DEPLOYER_USER = "HostMasterDeployer";
    public static final String MASTER_ADMINISTRATOR_USER = "HostMasterAdministrator";
    public static final String MASTER_AUDITOR_USER = "HostMasterAuditor";
    public static final String MASTER_SUPERUSER_USER = "HostMasterSuperUser";
    public static final String SLAVE_MONITOR_USER = "HostSlaveMonitor";
    public static final String SLAVE_OPERATOR_USER = "HostSlaveOperator";
    public static final String SLAVE_MAINTAINER_USER = "HostSlaveMaintainer";
    public static final String SLAVE_DEPLOYER_USER = "HostSlaveDeployer";
    public static final String SLAVE_ADMINISTRATOR_USER = "HostSlaveAdministrator";
    public static final String SLAVE_AUDITOR_USER = "HostSlaveAuditor";
    public static final String SLAVE_SUPERUSER_USER = "HostSlaveSuperUser";

    static final String[] MASTER_USERS = {MASTER_MONITOR_USER, MASTER_OPERATOR_USER, MASTER_MAINTAINER_USER, MASTER_DEPLOYER_USER,
            MASTER_ADMINISTRATOR_USER, MASTER_AUDITOR_USER, MASTER_SUPERUSER_USER};
    static final String[] SLAVE_USERS = {SLAVE_MONITOR_USER, SLAVE_OPERATOR_USER, SLAVE_MAINTAINER_USER, SLAVE_DEPLOYER_USER,
            SLAVE_ADMINISTRATOR_USER, SLAVE_AUDITOR_USER, SLAVE_SUPERUSER_USER};
    private static final String[] BASES = { RbacUtil.MONITOR_USER, RbacUtil.OPERATOR_USER, RbacUtil.MAINTAINER_USER,
            RbacUtil.DEPLOYER_USER, RbacUtil.ADMINISTRATOR_USER, RbacUtil.AUDITOR_USER,
            RbacUtil.SUPERUSER_USER };

    protected static final String SCOPED_ROLE = "core-service=management/access=authorization/host-scoped-role=";

    protected static void setupRoles(DomainClient domainClient) throws IOException {
        for (int i = 0; i < MASTER_USERS.length; i++) {
            ModelNode op = createOpNode(SCOPED_ROLE + MASTER_USERS[i], ADD);
            op.get(BASE_ROLE).set(BASES[i]);
            op.get(HOSTS).add(MASTER);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
        for (int i = 0; i < SLAVE_USERS.length; i++) {
            ModelNode op = createOpNode(SCOPED_ROLE + SLAVE_USERS[i], ADD);
            op.get(BASE_ROLE).set(BASES[i]);
            op.get(HOSTS).add(SLAVE);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
    }

    protected static void tearDownRoles(DomainClient domainClient) throws IOException {
        for (String role : MASTER_USERS) {
            ModelNode op = createOpNode(SCOPED_ROLE + role, REMOVE);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
        for (String role : SLAVE_USERS) {
            ModelNode op = createOpNode(SCOPED_ROLE + role, REMOVE);
            RbacUtil.executeOperation(domainClient, op, Outcome.SUCCESS);
        }
    }

    @After
    public void tearDown() throws IOException {
        AssertionError assertionError = null;
        String[] toRemove = {DEPLOYMENT_2, TEST_PATH, getPrefixedAddress(HOST, MASTER, SMALL_JVM),
                getPrefixedAddress(HOST, SLAVE, SMALL_JVM),
                getPrefixedAddress(HOST, SLAVE, SCOPED_ROLE_SERVER),
                getPrefixedAddress(HOST, MASTER, SCOPED_ROLE_SERVER)};
        for (String address : toRemove) {
            try {
                removeResource(address);
            } catch (AssertionError e) {
                if (assertionError == null) {
                    assertionError = e;
                }
            }
        }


        if (assertionError != null) {
            throw assertionError;
        }
    }

    protected abstract boolean isAllowLocalAuth();

    protected abstract void configureRoles(ModelNode op, String[] roles);

    @Test
    public void testMasterMonitor() throws Exception {
        testMonitor(true);
    }

    @Test
    public void testSlaveMonitor() throws Exception {
        testMonitor(false);
    }

    private void testMonitor(boolean master) throws Exception {
        final String user = master ? MASTER_MONITOR_USER : SLAVE_MONITOR_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, false, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, false, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN: Outcome.UNAUTHORIZED, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);

        if (master) {
            testWLFY2299(client, Outcome.UNAUTHORIZED, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
    }

    @Test
    public void testMasterOperator() throws Exception {
        testOperator(true);
    }

    @Test
    public void testSlaveOperator() throws Exception {
        testOperator(false);
    }

    private void testOperator(boolean master) throws Exception {
        final String user = master ? MASTER_OPERATOR_USER : SLAVE_OPERATOR_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, false, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, false, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN: Outcome.SUCCESS, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);

        if (master) {
            testWLFY2299(client, Outcome.UNAUTHORIZED, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
    }

    @Test
    public void testMasterMaintainer() throws Exception {
        testMaintainer(true);
    }

    @Test
    public void testSlaveMaintainer() throws Exception {
        testMaintainer(false);
    }

    public void testMaintainer(boolean master) throws Exception {
        final String user = master ? MASTER_MAINTAINER_USER : SLAVE_MAINTAINER_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, false, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, false, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);

        if (master) {
            testWLFY2299(client, Outcome.SUCCESS, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
    }

    @Test
    public void testMasterDeployer() throws Exception {
        testDeployer(true);
    }

    @Test
    public void testSlaveDeployer() throws Exception {
        testDeployer(false);
    }

    private void testDeployer(boolean master) throws Exception {
        final String user = master ? MASTER_DEPLOYER_USER : SLAVE_DEPLOYER_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, Outcome.HIDDEN, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, false, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, false, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);

        if (master) {
            testWLFY2299(client, Outcome.UNAUTHORIZED, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
    }

    @Test
    public void testMasterAdministrator() throws Exception {
        testAdministrator(true);
    }

    @Test
    public void testSlaveAdministrator() throws Exception {
        testAdministrator(false);
    }

    private void testAdministrator(boolean master) throws Exception {
        final String user = master ? MASTER_ADMINISTRATOR_USER : SLAVE_ADMINISTRATOR_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, true, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, true, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);

        if (master) {
            testWLFY2299(client, Outcome.SUCCESS, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
    }

    @Test
    public void testMasterAuditor() throws Exception {
        testAuditor(true);
    }

    @Test
    public void testSlaveAuditor() throws Exception {
        testAuditor(false);
    }

    private void testAuditor(boolean master) throws Exception {
        final String user = master ? MASTER_AUDITOR_USER: SLAVE_AUDITOR_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, true, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, true, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);

        if (master) {
            testWLFY2299(client, Outcome.UNAUTHORIZED, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.UNAUTHORIZED : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.UNAUTHORIZED, user);
    }

    @Test
    public void testMasterSuperUser() throws Exception {
        testSuperUser(true);
    }

    @Test
    public void testSlaveSuperUser() throws Exception {
        testSuperUser(false);
    }

    private void testSuperUser(boolean master) throws Exception {
        final String user = master ? MASTER_SUPERUSER_USER: SLAVE_SUPERUSER_USER;

        ModelControllerClient client = getClientForUser(user, isAllowLocalAuth(), masterClientConfig);
        readWholeConfig(client, Outcome.UNAUTHORIZED, user);
        checkStandardReads(client, null, null, user);
        checkRootRead(client, null, null, Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkRootRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkRootRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        readResource(client, AUTHORIZATION, null, null, Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        readResource(client, AUTHORIZATION, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSecurityDomainRead(client, null, null, Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        checkSecurityDomainRead(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        checkSensitiveAttribute(client, null, null, false, user);
        if (master) {
            checkSensitiveAttribute(client, MASTER, MASTER_A, true, user);
        } else {
            checkSensitiveAttribute(client, SLAVE, SLAVE_B, true, user);
        }
        testHostScopedRoleCanReadHostChildResources(client, user);

        if (readOnly) return;

        runGC(client, MASTER, null, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, null, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        runGC(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        runGC(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
        addDeployment2(client, Outcome.UNAUTHORIZED, user);
        addPath(client, Outcome.UNAUTHORIZED, user);
        addJvm(client, HOST, MASTER, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        addJvm(client, HOST, SLAVE, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);

        if (master) {
            testWLFY2299(client, Outcome.SUCCESS, user);
        }
        restartServer(client, MASTER, MASTER_A, master ? Outcome.SUCCESS : Outcome.HIDDEN, user);
        restartServer(client, SLAVE, SLAVE_B, master ? Outcome.HIDDEN : Outcome.SUCCESS, user);
    }

    private void testHostScopedRoleCanReadHostChildResources(ModelControllerClient client, String... roles) throws Exception {
        ModelNode op = Util.createOperation(READ_CHILDREN_RESOURCES_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(CHILD_TYPE).set(HOST);
        configureRoles(op, roles);
        //System.out.println("host scoped read host child resources result for " + roles[0]);
        //System.out.println(RbacUtil.executeOperation(client, op, Outcome.SUCCESS));
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);


        op = Util.createOperation(READ_RESOURCE_DESCRIPTION_OPERATION, PathAddress.EMPTY_ADDRESS);
        op.get(RECURSIVE_DEPTH).set(2);
        op.get(PROXIES).set(true);
        configureRoles(op, roles);
        //System.out.println("host scoped :read-resource-description(recursive-depth=1,proxies=true) result for " + roles[0]);
        //System.out.println(RbacUtil.executeOperation(client, op, Outcome.SUCCESS));
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);

    }

    private void testWLFY2299(ModelControllerClient client, Outcome expected, String... roles) throws IOException {

        addServerConfig(client, SLAVE, SERVER_GROUP_A, Outcome.HIDDEN, roles);
        addServerConfig(client, MASTER, SERVER_GROUP_A, expected, roles);

        ModelNode metadata = getServerConfigAccessControl(client, roles);
        ModelNode add = metadata.get("default", "operations", "add", "execute");
        Assert.assertTrue(add.isDefined());
        Assert.assertEquals(expected == Outcome.SUCCESS, add.asBoolean());

        ModelNode writeConfig = metadata.get("default", "write");
        Assert.assertTrue(writeConfig.isDefined());
        Assert.assertEquals(expected == Outcome.SUCCESS, writeConfig.asBoolean());
    }
}
