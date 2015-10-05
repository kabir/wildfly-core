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

import java.io.IOException;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
interface RbacDomainRolesTests {
    void testMasterMonitor() throws Exception;
    void testMasterOperator() throws Exception;
    void testMasterMaintainer() throws Exception;
    void testMasterDeployer() throws Exception;
    void testMasterAdministrator() throws Exception;
    void testMasterAuditor() throws Exception;
    void testMasterSuperUser() throws Exception;

    void tearDown() throws IOException;
}
