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

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.dmr.ModelType;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Kabir Khan
 */
public class OrderedChildResourceExtension implements Extension {

    public static final String MODULE_NAME = "org.wildfly.extension.ordered-child-resource-test";
    public static final String SUBSYSTEM_NAME = "ordered-children";
    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);

    private final EmptySubsystemParser parser = new EmptySubsystemParser("urn:jboss:test:extension:ordered:child:resource:1.0");
    public static final PathElement CHILD = PathElement.pathElement("child");
    private static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    private static final AttributeDefinition[] REQUEST_ATTRIBUTES = new AttributeDefinition[]{ATTR};


    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration reg = context.registerSubsystem(SUBSYSTEM_NAME, 1, 1, 1);
        reg.registerXMLElementWriter(parser);
        reg.registerSubsystemModel(new SubsystemResourceDefinition());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, parser.getNamespace(), parser);
    }

    private static class SubsystemResourceDefinition extends SimpleResourceDefinition {

        public SubsystemResourceDefinition() {
            super(SUBSYSTEM_PATH,
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler(REQUEST_ATTRIBUTES) {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(false, CHILD.getKey());
                        }

                    },
                    new ModelOnlyRemoveStepHandler());
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new OrderedChildResourceDefinition());
        }
    }

    private static class OrderedChildResourceDefinition extends SimpleResourceDefinition {

        public OrderedChildResourceDefinition() {
            super(CHILD, new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler() {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(true);
                        }

                    },
                    new ModelOnlyRemoveStepHandler());
        }

        @Override
        protected boolean isOrderedChildResource() {
            return true;
        }


        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, new ModelOnlyWriteAttributeHandler(ATTR));
        }
    }
}
