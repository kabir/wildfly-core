/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

import java.util.Collection;
import java.util.Collections;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UNSTABLE_AI_ANNOTATIONS;

/**
 * Resource to list all configuration changes.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class UnstableApiAnnotationResourceDefinition extends PersistentResourceDefinition {

    public static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create(
            ModelDescriptionConstants.LEVEL, ModelType.STRING, true)
            .setValidator(EnumValidator.create(UnstableApiAnnotationLevel.class))
            .setDefaultValue(new ModelNode(UnstableApiAnnotationLevel.LOG.name()))
            .build();
    public static final PathElement PATH = PathElement.pathElement(SERVICE, UNSTABLE_AI_ANNOTATIONS);
    static final UnstableApiAnnotationResourceDefinition INSTANCE = new UnstableApiAnnotationResourceDefinition();

    private UnstableApiAnnotationResourceDefinition() {
        super(new Parameters(PATH, CoreManagementExtension.getResourceDescriptionResolver(UNSTABLE_AI_ANNOTATIONS))
                .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
                .setRemoveHandler(ModelOnlyRemoveStepHandler.INSTANCE));
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(LEVEL, null, ModelOnlyWriteAttributeHandler.INSTANCE);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    public enum UnstableApiAnnotationLevel {
        LOG,
        ERROR
    }

}
