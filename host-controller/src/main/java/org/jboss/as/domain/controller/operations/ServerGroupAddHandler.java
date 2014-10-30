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

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class ServerGroupAddHandler implements OperationStepHandler {

    public static OperationStepHandler INSTANCE = new ServerGroupAddHandler();

    ServerGroupAddHandler() {
        //
    }

    @Deprecated
    public ServerGroupAddHandler(boolean master) {
        //
    }

    /** {@inheritDoc */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        for (AttributeDefinition attr : ServerGroupResourceDefinition.ADD_ATTRIBUTES) {
            attr.validateAndSet(operation, model);
        }

        context.addStep(DomainModelReferenceValidator.INSTANCE, OperationContext.Stage.MODEL);
        context.stepCompleted();
    }

}
