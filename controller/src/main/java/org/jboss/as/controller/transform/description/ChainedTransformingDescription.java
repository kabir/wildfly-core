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
package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.transform.ChainedTransformationTools;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderImpl.ModelVersionPair;
import org.jboss.dmr.ModelNode;

/**
 * Placeholder transformer implementation for chained transformers. It uses {@link org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver} to override how the transformers for the child resources
 * of the placeholder are resolved.
 *
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ChainedTransformingDescription extends AbstractDescription implements TransformationDescription, ResourceTransformer, OperationTransformer {

    private final LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> extraResolvers;

    ChainedTransformingDescription(PathElement pathElement, LinkedHashMap<ModelVersionPair, ChainedPlaceholderResolver> extraResolvers) {
        super(pathElement, PathAddressTransformer.DEFAULT, true);
        this.extraResolvers = extraResolvers;
    }

    @Override
    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation)
            throws OperationFailedException {
        //TODO
        return null;
    }

    @Override
    public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
        if (resource.isProxy() || resource.isRuntime()) {
            return;
        }

        //For now just assume we come in through the top layer
        ResourceTransformationContext current = context;
        Iterator<Map.Entry<ModelVersionPair, ChainedPlaceholderResolver>> it = extraResolvers.entrySet().iterator();
        if (it.hasNext()) {
            ChainedPlaceholderResolver resolver = it.next().getValue();
            current = ChainedTransformationTools.initialiseChain(context, resolver);
            resolver.getDescription().getResourceTransformer().transformResource(current, address, resource);
        }
        while (it.hasNext()) {
            ChainedPlaceholderResolver resolver = it.next().getValue();
            current = ChainedTransformationTools.nextInChain(current, resolver);
            resolver.getDescription().getResourceTransformer().transformResource(current, address, current.readResourceFromRoot(address));
        }

        Resource transformed = current.getTransformedRoot();
        Resource originalTransformed = context.getTransformedRoot();
        copy(transformed, originalTransformed);
    }


    @Override
    public PathAddressTransformer getPathAddressTransformer() {
        //TODO
        return PathAddressTransformer.DEFAULT;
    }

    @Override
    public OperationTransformer getOperationTransformer() {
        return this;
    }

    @Override
    public ResourceTransformer getResourceTransformer() {
        return this;
    }

    @Override
    public Map<String, OperationTransformer> getOperationTransformers() {
        //TODO make this configurable
        return Collections.emptyMap();
    }

    @Override
    public List<TransformationDescription> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public List<String> getDiscardedOperations() {
        //TODO make this configurable
        return Collections.emptyList();
    }

    @Override
    public boolean isPlaceHolder() {
        return true;
    }

    private void copy(Resource src, Resource dest) {
        dest.getModel().set(src.getModel());
        for (String type : src.getChildTypes()) {
            for (ResourceEntry entry : src.getChildren(type)) {
                Resource added = Resource.Factory.create();
                dest.registerChild(PathElement.pathElement(type, entry.getName()), added);
                copy(entry, added);
            }
        }
    }

}
