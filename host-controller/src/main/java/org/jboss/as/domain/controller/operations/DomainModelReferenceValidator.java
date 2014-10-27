/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.ROOT_LOGGER;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.AttachmentKey;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler validating that all known model references are present.
 *
 * This should probably be replaced with model reference validation at the end of the Stage.MODEL.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainModelReferenceValidator implements OperationStepHandler {

    static DomainModelReferenceValidator INSTANCE = new DomainModelReferenceValidator();
    private static final AttachmentKey<DomainModelReferenceValidator> KEY = AttachmentKey.create(DomainModelReferenceValidator.class);

    private DomainModelReferenceValidator() {
    }

    public static void addValidationStep(OperationContext context, ModelNode operation) {
        if (!context.isBooting()) {
            // This does not need to get executed on boot the domain controller service does that once booted
            // by calling validateAtBoot(). Otherwise we get issues with the testsuite, which only partially sets up the model
            if (context.attachIfAbsent(KEY, DomainModelReferenceValidator.INSTANCE) == null) {
                context.addStep(DomainModelReferenceValidator.INSTANCE, OperationContext.Stage.MODEL);
            }
        }
    }

    public static void validateAtBoot(OperationContext context, ModelNode operation) {
        assert context.isBooting() : "Should only be called at boot";
        assert operation.require(OP).asString().equals("validate"); //Should only be called by the domain controller service
        //Only validate once
        if (context.attachIfAbsent(KEY, DomainModelReferenceValidator.INSTANCE) == null) {
            context.addStep(DomainModelReferenceValidator.INSTANCE, OperationContext.Stage.MODEL);
        }
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Validate
        validate(context);
        // Done
        context.stepCompleted();
    }

    private void validate(final OperationContext context) throws OperationFailedException {
        final Set<String> serverGroups = new HashSet<>();
        final Set<String> socketBindings = new HashSet<>();

        final Resource domain = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

        Set<String> missingProfiles = new HashSet<>();
        Map<String, Set<String>> profileIncludes = checkProfileIncludes(context, domain, missingProfiles);

        final String hostName = determineHostName(domain);
        if (hostName != null) {
            // The testsuite does not always setup the model properly
            final Resource host = domain.getChild(PathElement.pathElement(HOST, hostName));
            for (final Resource.ResourceEntry serverConfig : host.getChildren(SERVER_CONFIG)) {
                final ModelNode model = serverConfig.getModel();
                final String group = model.require(GROUP).asString();
                if (!serverGroups.contains(group)) {
                    serverGroups.add(group);
                }
                processSocketBindingGroup(model, socketBindings);
            }
        }

        // process referenced server-groups
        for (final Resource.ResourceEntry serverGroup : domain.getChildren(SERVER_GROUP)) {
            final ModelNode model = serverGroup.getModel();
            final String profile = model.require(PROFILE).asString();
            if (!profileIncludes.containsKey(profile)) {
                missingProfiles.add(profile);
            }
            // Process the socket-binding-group
            processSocketBindingGroup(model, socketBindings);

            serverGroups.remove(serverGroup.getName()); // The server-group is present
        }

        // If we are missing a server group
        if (!serverGroups.isEmpty()) {
            throw DomainControllerLogger.CONTROLLER_LOGGER.missingReferences(SERVER_GROUP, serverGroups);
        }
        // We are missing a profile
        if (!missingProfiles.isEmpty()) {
            throw DomainControllerLogger.CONTROLLER_LOGGER.missingReferences(PROFILE, missingProfiles);
        }
        // Process socket-binding groups
        for (final Resource.ResourceEntry socketBindingGroup : domain.getChildren(SOCKET_BINDING_GROUP)) {
            socketBindings.remove(socketBindingGroup.getName());
        }
        // We are missing a socket-binding group
        if (!socketBindings.isEmpty()) {
            throw DomainControllerLogger.CONTROLLER_LOGGER.missingReferences(SOCKET_BINDING_GROUP, socketBindings);
        }
    }


    private void processSocketBindingGroup(final ModelNode model, final Set<String> socketBindings) {
        if (model.hasDefined(SOCKET_BINDING_GROUP)) {
            final String socketBinding = model.require(SOCKET_BINDING_GROUP).asString();
            if (!socketBindings.contains(socketBinding)) {
                socketBindings.add(socketBinding);
            }
        }
    }

    private String determineHostName(final Resource domain) {
        // This could use a better way to determine the local host name
        for (final Resource.ResourceEntry entry : domain.getChildren(HOST)) {
            if (entry.isProxy() || entry.isRuntime()) {
                continue;
            }
            return entry.getName();
        }
        return null;
    }

    private Map<String, Set<String>> checkProfileIncludes(OperationContext context, Resource domain, Set<String> missingProfiles) throws OperationFailedException {
        Map<String, Set<String>> profileIncludes = new HashMap<>();
        for (ResourceEntry entry : domain.getChildren(PROFILE)) {
            ModelNode model = entry.getModel();
            final Set<String> includes;
            if (model.hasDefined(INCLUDES)) {
                includes = new HashSet<>();
                for (ModelNode include : model.get(INCLUDES).asList()) {
                    includes.add(include.asString());
                }
            } else {
                includes = Collections.emptySet();
            }
            profileIncludes.put(entry.getName(), includes);
        }

        new ProfileIncludeValidator(profileIncludes).validate(missingProfiles);
        return profileIncludes;
    }

    private static class ProfileIncludeValidator {
        private final Set<String> seen = new HashSet<>();
        private final Set<String> onStack = new HashSet<>();
        private final Map<String, String> linkTo = new HashMap<>();
        private final Map<String, Set<String>> profileIncludes;

        public ProfileIncludeValidator(Map<String, Set<String>> profileIncludes) {
            this.profileIncludes = profileIncludes;
        }

        void validate(Set<String> missingProfiles) throws OperationFailedException {
            for (String profileName : profileIncludes.keySet()) {
                if (!seen.contains(profileName)) {
                    dfs(profileName, missingProfiles);
                }
            }
        }

        void dfs(String profileName, Set<String> missingProfiles) throws OperationFailedException {
            onStack.add(profileName);
            try {
                seen.add(profileName);
                Set<String> includes = profileIncludes.get(profileName);
                if (includes == null) {
                    missingProfiles.add(profileName);
                    return;
                }
                for (String include : includes) {
                    if (!seen.contains(include)) {
                        linkTo.put(include, profileName);
                        dfs(include, missingProfiles);
                    } else if (onStack.contains(include)) {
                        throw ROOT_LOGGER.profileInvolvedInACycle(include);
                    }
                }
            } finally {
                onStack.remove(profileName);
            }
        }
    }
}
