/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.domain.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.controller.operations.GenericModelDescribeOperationHandler;
import org.jboss.as.domain.controller.operations.ProfileAddHandler;
import org.jboss.as.domain.controller.operations.ProfileCloneHandler;
import org.jboss.as.domain.controller.operations.ProfileDescribeHandler;
import org.jboss.as.domain.controller.operations.ProfileModelDescribeHandler;
import org.jboss.as.domain.controller.operations.ProfileRemoveHandler;
import org.jboss.as.domain.controller.operations.ProfileValidationWriteAttributeHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProfileResourceDefinition extends SimpleResourceDefinition {

    private static OperationDefinition DESCRIBE = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.DESCRIBE, DomainResolver.getResolver(PROFILE, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .setReadOnly()
            .build();



    //This attribute exists in 7.1.2 and 7.1.3 but was always nillable
    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING)
            .setValidator(new StringLengthValidator(1, true))
            .setAllowNull(true)
            .setResourceOnly()
            .build();

    public static final ListAttributeDefinition INCLUDES = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.INCLUDES, ModelType.STRING)
            .setAllowNull(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {

                @Override
                public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault,
                        XMLStreamWriter writer) throws XMLStreamException {
                    if (!isMarshallable(attribute, resourceModel, marshallDefault)) {
                        return;
                    }

                    boolean first = true;
                    StringBuilder sb = new StringBuilder();
                    for (ModelNode include : resourceModel.get(ModelDescriptionConstants.INCLUDES).asList()) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(" ");
                        }
                        sb.append(include.asString());
                    }
                    writer.writeAttribute(attribute.getXmlName(), sb.toString());
                }
            })
            .build();


    public static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {INCLUDES};

    private final ExtensionRegistry extensionRegistry;

    public ProfileResourceDefinition(ExtensionRegistry extensionRegistry) {
        super(PathElement.pathElement(PROFILE), DomainResolver.getResolver(PROFILE, false), ProfileAddHandler.INSTANCE, ProfileRemoveHandler.INSTANCE);
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DESCRIBE, ProfileDescribeHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(ProfileCloneHandler.DEFINITION, ProfileCloneHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(GenericModelDescribeOperationHandler.DEFINITION, ProfileModelDescribeHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        OperationStepHandler handler = new ProfileValidationWriteAttributeHandler(ATTRIBUTES);
        resourceRegistration.registerReadWriteAttribute(INCLUDES, null, handler);
    }


}
