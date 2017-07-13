/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.provisioning;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESSES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESOURCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Takes the output of {@link ProvisionedResourceInfoCollector} and constructs the resource tree and gets the order that
 * the resources were added in.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProvisionedResourceAssembler {
    private final List<PathAddress> addresses;
    private final Resource rootResource;

    public ProvisionedResourceAssembler(List<PathAddress> addresses, Resource rootResource) {
        this.addresses = Collections.unmodifiableList(addresses);
        this.rootResource = rootResource;
    }

    public List<PathAddress> getAddresses() {
        return addresses;
    }

    public Resource getRootResource() {
        return rootResource;
    }

    static ProvisionedResourceAssembler read(ModelNode modelNode) {
        if (!modelNode.hasDefined(ADDRESSES)) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }
        if (!modelNode.hasDefined(RESOURCE)) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }

        List<PathAddress> addresses = new ArrayList<>();
        modelNode.get(ADDRESSES).asList().forEach(m -> addresses.add(PathAddress.parseCLIStyleAddress(m.asString())));

        List<ModelNode> resources = new ArrayList<>(modelNode.get(RESOURCE).asList());
        if (resources.size() == 0) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }
        final ModelNode rootNode = resources.remove(0);
        if (!rootNode.has(ADDRESS)) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }

        if (!rootNode.get(ADDRESS).asString().equals("/")) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }
        final Resource root = createResource(rootNode);

        for (ModelNode resourceNode : resources) {
            final PathAddress address = readAddress(resourceNode);
            Resource parent = root;
            for (int i = 0 ; i < address.size() - 1 ; i++) {
                PathElement element = address.getElement(i);
                parent = parent.getChild(element);
                if (parent == null) {
                    break;
                }
            }
            if (parent == null) {
                throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
            }
            parent.registerChild(address.getLastElement(), createResource(resourceNode));
        }

        return new ProvisionedResourceAssembler(addresses, root);
    }

    private static PathAddress readAddress(ModelNode modelNode) {
        if (!modelNode.has(ADDRESS)) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }
        return PathAddress.parseCLIStyleAddress(modelNode.get(ADDRESS).asString());
    }

    private static Resource createResource(ModelNode modelNode) {
        final Resource resource = Resource.Factory.create();
        if (!modelNode.has(VALUE)) {
            throw ControllerLogger.ROOT_LOGGER.invalidProvisionedData();
        }
        resource.getModel().set(modelNode.get(VALUE));
        return resource;
    }
}
