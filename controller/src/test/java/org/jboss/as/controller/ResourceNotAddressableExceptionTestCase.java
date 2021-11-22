/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import org.jboss.as.controller.access.ResourceNotAddressableException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link org.jboss.as.controller.access.ResourceNotAddressableException}.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
public class ResourceNotAddressableExceptionTestCase {

    /**
     * Test that the exception message is what we expect to prevent this exception
     * looking different from a non-authorization triggered exception
     */
    @Test
    public void testFailureDescription() {
        PathAddress pa = PathAddress.pathAddress(PathElement.pathElement("key", "value"));
        ResourceNotAddressableException rnae = new ResourceNotAddressableException(pa);
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        Resource.NoSuchResourceException nsre = ControllerLogger.ROOT_LOGGER.managementResourceNotFound(pa);
        Assert.assertEquals(nsre.getFailureDescription(), rnae.getFailureDescription());
    }
}
