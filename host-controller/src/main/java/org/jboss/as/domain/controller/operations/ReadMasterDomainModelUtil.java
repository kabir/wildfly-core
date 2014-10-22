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
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Utility for the DC operation handlers to describe the missing resources for the slave hosts which are
 * set up to ignore domain config which does not affect their servers
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainModelUtil {

    public static final String DOMAIN_RESOURCE_ADDRESS = "domain-resource-address";

    public static final String DOMAIN_RESOURCE_MODEL = "domain-resource-model";

    private final Set<PathElement> newRootResources = new HashSet<>();

    private volatile List<ModelNode> describedResources;

    private ReadMasterDomainModelUtil() {
    }

    /**
     * Used to read the domain model when a slave host connects to the DC
     *
     *  @param context the operation context
     *  @param transformers the transformers for the host
     *  @param domainRoot the domain root resource
     *  @return a read master domain model util instance
     */
    static ReadMasterDomainModelUtil readMasterDomainResourcesForInitialConnect(
            final OperationContext context, final Transformers transformers, final Resource domainRoot, final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException {

        Resource transformedResource = transformers.transformRootResource(context, domainRoot, ignoredTransformationRegistry);
        ReadMasterDomainModelUtil util = new ReadMasterDomainModelUtil();
        util.describedResources = util.describeAsNodeList(PathAddress.EMPTY_ADDRESS, transformedResource, false);
        return util;
    }

    /**
     * Gets a list of the resources for the slave's ApplyXXXXHandlers. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @return the resources
     */
    public List<ModelNode> getDescribedResources(){
        return describedResources;
    }

    /**
     * Describe the model as a list of resources with their address and model, which
     * the HC can directly apply to create the model. Although the format might appear
     * similar as the operations generated at boot-time this description is only useful
     * to create the resource tree and cannot be used to invoke any operation.
     *
     * @param rootAddress the address of the root resource being described
     * @param resource the root resource
     * @return the list of resources
     */
    private List<ModelNode> describeAsNodeList(PathAddress rootAddress, final Resource resource, boolean isRuntimeChange) {
        final List<ModelNode> list = new ArrayList<ModelNode>();

        describe(rootAddress, resource, list, isRuntimeChange);
        return list;
    }

    private void describe(final PathAddress base, final Resource resource, List<ModelNode> nodes, boolean isRuntimeChange) {
        if (resource.isProxy() || resource.isRuntime()) {
            return; // ignore runtime and proxies
        } else if (base.size() >= 1 && base.getElement(0).getKey().equals(ModelDescriptionConstants.HOST)) {
            return; // ignore hosts
        }
        if (base.size() == 1) {
            newRootResources.add(base.getLastElement());
        }
        final ModelNode description = new ModelNode();
        description.get(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_ADDRESS).set(base.toModelNode());
        description.get(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_MODEL).set(resource.getModel());
        nodes.add(description);
        for (final String childType : resource.getChildTypes()) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {
                describe(base.append(entry.getPathElement()), entry, nodes, isRuntimeChange);
            }
        }
    }


    /**
     * Create a resource based on the result of the {@code ReadMasterDomainModelHandler}.
     *
     * @param result        the operation result
     * @param extensions    set to track extensions
     * @return the resource
     */
    static Resource createResourceFromDomainModelOp(final ModelNode result, final Set<String> extensions) {
        final Resource root = Resource.Factory.create();
        for (ModelNode model : result.asList()) {

            final PathAddress resourceAddress = PathAddress.pathAddress(model.require(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_ADDRESS));

            if (resourceAddress.size() == 1) {
                final PathElement element = resourceAddress.getElement(0);
                if (element.getKey().equals(EXTENSION)) {
                    if (!extensions.contains(element.getValue())) {
                        extensions.add(element.getValue());
                    }
                }
            }

            final Iterator<PathElement> i = resourceAddress.iterator();

            Resource resource = root;
            while (i.hasNext()) {
                final PathElement e = i.next();

                if (resource.hasChild(e)) {
                    resource = resource.getChild(e);
                } else {
                    final Resource nr = Resource.Factory.create();
                    resource.registerChild(e, nr);
                    resource = nr;
                }

                if (!i.hasNext()) {
                    resource.getModel().set(model.require(ReadMasterDomainModelUtil.DOMAIN_RESOURCE_MODEL));
                }
            }
        }
        return root;
    }

    /**
     * Process the host info and determine which configuration elements are required on the slave host.
     *
     * @param hostInfo             the host info
     * @param root                 the model root
     * @param extensionRegistry    the extension registry
     * @return
     */
    public static RequiredConfigurationHolder populateHostResolutionContext(final HostInfo hostInfo, final Resource root, final ExtensionRegistry extensionRegistry) {
        final RequiredConfigurationHolder rc = new RequiredConfigurationHolder();
        for (IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo info : hostInfo.getServerConfigInfos()) {
            ReadMasterDomainModelUtil.processServerConfig(root, rc, info, extensionRegistry);
        }
        return rc;
    }

    /**
     * Determine the relevant pieces of configuration which need to be included when processing the domain model.
     *
     * @param root                 the resource root
     * @param requiredConfigurationHolder    the resolution context
     * @param serverConfig         the server config
     * @param extensionRegistry    the extension registry
     */
    static void processServerConfig(final Resource root, final RequiredConfigurationHolder requiredConfigurationHolder, final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig, final ExtensionRegistry extensionRegistry) {

        final Set<String> extensions = requiredConfigurationHolder.extensions;
        final Set<String> profiles = requiredConfigurationHolder.profiles;
        final Set<String> serverGroups = requiredConfigurationHolder.serverGroups;
        final Set<String> socketBindings = requiredConfigurationHolder.socketBindings;

        if (serverConfig.getSocketBindingGroup() != null && !socketBindings.contains(serverConfig.getSocketBindingGroup())) {
            socketBindings.add(serverConfig.getSocketBindingGroup());
        }

        final String groupName = serverConfig.getServerGroup();
        final PathElement groupElement = PathElement.pathElement(SERVER_GROUP, groupName);
        // Also check the root, since this also gets executed on the slave which may not have the server-group configured yet
        if (!serverGroups.contains(groupName) && root.hasChild(groupElement)) {

            final Resource serverGroup = root.getChild(groupElement);
            final ModelNode groupModel = serverGroup.getModel();
            serverGroups.add(groupName);

            // Include the socket binding groups
            if (groupModel.hasDefined(SOCKET_BINDING_GROUP)) {
                final String socketBinding = groupModel.get(SOCKET_BINDING_GROUP).asString();
                if (!socketBindings.contains(socketBinding)) {
                    socketBindings.add(socketBinding);
                }
            }

            final String profileName = groupModel.get(PROFILE).asString();
            final PathElement profileElement = PathElement.pathElement(PROFILE, profileName);
            if (!profiles.contains(profileName) && root.hasChild(profileElement)) {
                final Resource profile = root.getChild(profileElement);

                if (profile.getModel().hasDefined(INCLUDE)) {
                    // TODO include is currently disabled
                }

                profiles.add(profileName);
                final Set<String> subsystems = new HashSet<>();
                final Set<String> availableExtensions = extensionRegistry.getExtensionModuleNames();
                for (final Resource.ResourceEntry subsystem : profile.getChildren(SUBSYSTEM)) {
                    subsystems.add(subsystem.getName());
                }
                for (final String extension : availableExtensions) {
                    if (extensions.contains(extension)) {
                        // Skip already processed extensions
                        continue;
                    }
                    for (final String subsystem : extensionRegistry.getAvailableSubsystems(extension).keySet()) {
                        if (subsystems.contains(subsystem)) {
                            extensions.add(extension);
                        }
                    }
                }
            }
        }
    }

    static void processHostModel(final RequiredConfigurationHolder holder, final Resource domain, final Resource hostModel, ExtensionRegistry extensionRegistry) {

        final Set<String> serverGroups = holder.serverGroups;
        final Set<String> socketBindings = holder.socketBindings;

        for (final Resource.ResourceEntry entry : hostModel.getChildren(SERVER_CONFIG)) {
            final ModelNode model = entry.getModel();
            final String serverGroup = model.get(GROUP).asString();

            if (!serverGroups.contains(serverGroup)) {
                serverGroups.add(serverGroup);
            }
            if (model.hasDefined(SOCKET_BINDING_GROUP)) {
                final String socketBindingGroup = model.get(SOCKET_BINDING_GROUP).asString();
                if (!socketBindings.contains(socketBindingGroup)) {
                    socketBindings.add(socketBindingGroup);
                }
            }
            // Always process the server group, since it may be different between the current vs. original model
            processServerGroup(holder, serverGroup, domain, extensionRegistry);
        }
    }

    private static void processServerGroup(final RequiredConfigurationHolder holder, final String group, final Resource domain, ExtensionRegistry extensionRegistry) {

        final PathElement groupElement = PathElement.pathElement(SERVER_GROUP, group);
        if (!domain.hasChild(groupElement)) {
            return;
        }
        final Set<String> profiles = holder.profiles;
        final Set<String> extensions = holder.extensions;
        final Set<String> socketBindings = holder.socketBindings;

        final Resource serverGroup = domain.getChild(groupElement);
        final ModelNode model = serverGroup.getModel();

        final String profile = model.get(PROFILE).asString();
        if (!profiles.contains(profile)) {
            profiles.add(profile);
        }

        if (model.hasDefined(SOCKET_BINDING_GROUP)) {
            final String socketBindingGroup = model.get(SOCKET_BINDING_GROUP).asString();
            if (!socketBindings.contains(socketBindingGroup)) {
                socketBindings.add(socketBindingGroup);
            }
        }

        final PathElement profileElement = PathElement.pathElement(PROFILE, profile);
        if (domain.hasChild(profileElement)) {
            final Resource resource = domain.getChild(profileElement);

            final Set<String> subsystems = new HashSet<>();
            final Set<String> availableExtensions = extensionRegistry.getExtensionModuleNames();
            for (final Resource.ResourceEntry subsystem : resource.getChildren(SUBSYSTEM)) {
                subsystems.add(subsystem.getName());
            }
            for (final String extension : availableExtensions) {
                if (extensions.contains(extension)) {
                    // Skip already processed extensions
                    continue;
                }
                for (final String subsystem : extensionRegistry.getAvailableSubsystems(extension).keySet()) {
                    if (subsystems.contains(subsystem)) {
                        extensions.add(extension);
                    }
                }
            }
        }
    }

    /**
     * Create the ResourceIgnoredTransformationRegistry for connection/reconnection process.
     *
     * @param hostInfo the host info
     * @param rc       the resolution context
     * @param local    whether the operation is executed on the slave host locally
     * @return
     */
    public static Transformers.ResourceIgnoredTransformationRegistry createHostIgnoredRegistry(final HostInfo hostInfo, final RequiredConfigurationHolder rc, final boolean local) {
        return new Transformers.ResourceIgnoredTransformationRegistry() {
            @Override
            public boolean isResourceTransformationIgnored(PathAddress address) {
                if (hostInfo.isResourceTransformationIgnored(address)) {
                    return true;
                }
                if (address.size() == 1 && hostInfo.isIgnoreUnaffectedConfig()) {
                    final PathElement element = address.getElement(0);
                    final String type = element.getKey();
                    switch (type) {
                        case ModelDescriptionConstants.EXTENSION:
                            // Don't ignore extensions for now
                            return false;
//                            if (local) {
//                                return false; // Always include all local extensions
//                            } else if (!rc.getExtensions().contains(element.getValue())) {
//                                return true;
//                            }
//                            break;
                        case PROFILE:
                            if (!rc.getProfiles().contains(element.getValue())) {
                                return true;
                            }
                            break;
                        case SERVER_GROUP:
                            if (!rc.getServerGroups().contains(element.getValue())) {
                                return true;
                            }
                            break;
                        case SOCKET_BINDING_GROUP:
                            if (!rc.getSocketBindings().contains(element.getValue())) {
                                return true;
                            }
                            break;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Create the ResourceIgnoredTransformationRegistry when fetching missing content, only including relevant pieces
     * to a server-config.
     *
     * @param rc       the resolution context
     * @param local    whether the operation is executed on the slave host locally
     * @return
     */
    public static Transformers.ResourceIgnoredTransformationRegistry createServerIgnoredRegistry(final RequiredConfigurationHolder rc, final boolean local) {
        return new Transformers.ResourceIgnoredTransformationRegistry() {
            @Override
            public boolean isResourceTransformationIgnored(PathAddress address) {
                final int length = address.size();
                if (length == 0) {
                    return false;
                } else if (length >= 1) {
                    final PathElement element = address.getElement(0);
                    final String type = element.getKey();
                    switch (type) {
                        case ModelDescriptionConstants.EXTENSION:
                            // Don't ignore extensions for now
                            return false;
//                            if (local) {
//                                return false; // Always include all local extensions
//                            } else if (rc.getExtensions().contains(element.getValue())) {
//                                return false;
//                            }
//                            break;
                        case ModelDescriptionConstants.PROFILE:
                            if (rc.getProfiles().contains(element.getValue())) {
                                return false;
                            }
                            break;
                        case ModelDescriptionConstants.SERVER_GROUP:
                            if (rc.getServerGroups().contains(element.getValue())) {
                                return false;
                            }
                            break;
                        case ModelDescriptionConstants.SOCKET_BINDING_GROUP:
                            if (rc.getSocketBindings().contains(element.getValue())) {
                                return false;
                            }
                            break;
                    }
                }
                return true;
            }
        };
    }

    static class RequiredConfigurationHolder {

        private final Set<String> extensions = new HashSet<>();
        private final Set<String> profiles = new HashSet<>();
        private final Set<String> serverGroups = new HashSet<>();
        private final Set<String> socketBindings = new HashSet<>();

        public Set<String> getExtensions() {
            return extensions;
        }

        public Set<String> getProfiles() {
            return profiles;
        }

        public Set<String> getServerGroups() {
            return serverGroups;
        }

        public Set<String> getSocketBindings() {
            return socketBindings;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("ResolutionContext{");
            builder.append("extensions=").append(extensions);
            builder.append("profiles=").append(profiles).append(", ");
            builder.append("server-groups=").append(serverGroups).append(", ");
            builder.append("socket-bindings=").append(socketBindings).append("}");
            return builder.toString();
        }
    }

}
