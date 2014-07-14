/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;

import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.SubsystemInformation;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.AddNameFromAddressResourceTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;

/**
 * Global transformation rules for the domain, host and server-config model.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainTransformers {

    /** Dummy version for ignored subsystems. */
    static final ModelVersion IGNORED_SUBSYSTEMS = ModelVersion.create(-1);

    private static final PathElement JSF_EXTENSION = PathElement.pathElement(ModelDescriptionConstants.EXTENSION, "org.jboss.as.jsf");

    //AS 7.1.2.Final / EAP 6.0.0
    private static final ModelVersion VERSION_1_2 = ModelVersion.create(1, 2, 0);
    //AS 7.1.3.Final / EAP 6.0.1
    private static final ModelVersion VERSION_1_3 = ModelVersion.create(1, 3, 0);
    //AS 7.2.0.Final / EAP 6.1.0 / EAP 6.1.1
    private static final ModelVersion VERSION_1_4 = ModelVersion.create(1, 4, 0);
    // EAP 6.2.0
    private static final ModelVersion VERSION_1_5 = ModelVersion.create(1, 5, 0);
    // EAP 6.3.0
    private static final ModelVersion VERSION_1_6 = ModelVersion.create(1, 6, 0);
    //WF 8.0.0.Final
    private static final ModelVersion VERSION_2_0 = ModelVersion.create(2, 0, 0);
    //WF 8.1.0.Final
    private static final ModelVersion VERSION_2_1 = ModelVersion.create(2, 1, 0);

    /**
     * Initialize the domain registry.
     *
     * @param registry the domain registry
     */
    public static void initializeDomainRegistry(final TransformerRegistry registry) {

        initializeDomainRegistryEAP60(registry, VERSION_1_2);
        initializeDomainRegistryEAP60(registry, VERSION_1_3);
        initializeDomainRegistry14(registry, VERSION_1_4);
        initializeDomainRegistry15_21(registry, VERSION_1_5);
        initializeDomainRegistry15_21(registry, VERSION_1_6);
        initializeDomainRegistry15_21(registry, VERSION_2_0);
        initializeDomainRegistry15_21(registry, VERSION_2_1);
    }

    private static void initializeDomainRegistryEAP60(TransformerRegistry registry, ModelVersion modelVersion) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);

        ManagementTransformers.registerTransformersPreRBAC(builder);
        SystemPropertyTransformers.registerTransformers120(builder);
        PathsTransformers.registerTransformers120(builder);
        DeploymentTransformers.registerTransformers120(builder);
        ServerGroupTransformers.registerTransformers120(builder);
        SocketBindingGroupTransformers.registerTransformers120(builder);
        //Add the domain interface name. This is currently from a read attribute handler but in < 1.4.0 it existed in the model
        builder.addChildResource(PathElement.pathElement(INTERFACE))
            .setCustomResourceTransformer(AddNameFromAddressResourceTransformer.INSTANCE);

        TransformersSubRegistration domain = TransformationDescription.Tools.registerForDomain(builder.build(), registry, modelVersion);

        // Discard all operations to the newly introduced jsf extension
        domain.registerSubResource(JSF_EXTENSION, IGNORED_EXTENSIONS);

        JSFSubsystemTransformers.registerTransformers120(registry, domain);
    }

    private static void initializeDomainRegistry14(TransformerRegistry registry, ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);
        ManagementTransformers.registerTransformersPreRBAC(builder);
        ServerGroupTransformers.registerTransformers14_21(builder);
        TransformationDescription.Tools.registerForDomain(builder.build(), registry, version);
    }

    private static void initializeDomainRegistry15_21(TransformerRegistry registry, ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);
        ServerGroupTransformers.registerTransformers14_21(builder);
        TransformationDescription.Tools.registerForDomain(builder.build(), registry, version);
    }

    private static final ResourceTransformer IGNORED_EXTENSIONS = new IgnoreExtensionResourceTransformer();

    /**
     * Special resource transformer automatically ignoring all subsystems registered by an extension.
     */
    static class IgnoreExtensionResourceTransformer implements ResourceTransformer {

        @Override
        public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
            // we just ignore this resource  - so don't add it: context.addTransformedResource(...)
            final PathElement element = address.getLastElement();

            final TransformationTarget target = context.getTarget();
            final ExtensionRegistry registry = target.getExtensionRegistry();

            final Map<String, SubsystemInformation> subsystems = registry.getAvailableSubsystems(element.getValue());
            if(subsystems != null) {
                for(final Map.Entry<String, SubsystemInformation> subsystem : subsystems.entrySet()) {
                    final String name = subsystem.getKey();
                    target.addSubsystemVersion(name, IGNORED_SUBSYSTEMS);
                }
            }
        }
    }
}
