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

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;

/**
 * Context in which a {@link org.jboss.as.controller.capability.AbstractCapability capability} is available.
 * <p>
 * The {@link #GLOBAL} context can be used for most cases. A Host Controller will use a different implementation
 * of this interface for capabilities that are limited to some subset of the domain-wide model, e.g. a single
 * profile.
 * </p>
 * <p>
 * Implementations of this interface should override {@link #equals(Object)} and {@link #hashCode()} such that
 * logically equivalent but non-identical instances can function as keys in a hash map.
 * </p>
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public interface CapabilityContext {

    /**
     * Gets whether a given capability associated with this context can satisfy the given requirement.
     * @param context resolution context in use for this resolution run
     *
     * @return {@code true} if the requirement can be satisfied from this context; {@code false} otherwise
     */
    boolean canSatisfyRequirement(String requiredName, CapabilityContext dependentContext,
                                  CapabilityResolutionContext context);

    /**
     * Gets whether a consistency check must be performed when other capabilities depend on capabilities
     * in this context. A consistency check is necessary if different capabilities in the dependent context
     * can potentially require capabilities in different other contexts, but all such capabilities must be
     * available in at least one context.
     * @return {@code true} if a consistency check is required
     */
    boolean requiresConsistencyCheck();

    /**
     * Gets a descriptive name of the context
     * @return the name. Will not return {@code null}
     */
    String getName();

    /**
     * Gets any contexts that logically include this one, i.e. where this context can satisfy
     * requirements as if it were the including context.
     *
     * @param context resolution context in use for this resolution run
     * @return the including contexts. Will not be {@code null} but may be empty.
     */
    default Set<CapabilityContext> getIncludingContexts(CapabilityResolutionContext context) {
        return Collections.emptySet();
    }

    /**
     * A {@code CapabilityContext} that can satisfy any dependent context. Meant for capabilities that are present
     * regardless of any context, or for convenience use in cases where there is only one context.
     */
    CapabilityContext GLOBAL = new CapabilityContext() {

        /**
         * Always returns {@code true}
         * @return {@code true}, always
         */
        @Override
        public boolean canSatisfyRequirement(String requiredName, CapabilityContext dependentContext, CapabilityResolutionContext context) {
            return true;
        }

        @Override
        public boolean requiresConsistencyCheck() {
            return false;
        }

        @Override
        public String getName() {
            return "global";
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{global}";
        }
    };

    /** Factory for creating a {@code CapabilityContext} */
    class Factory {
        /**
         * Create a {@code CapabilityContext} appropriate for the given process type and address
         *
         * @param processType the type of process in which the {@code CapabilityContext} exists
         * @param address the address with which the {@code CapabilityContext} is associated
         */
        public static CapabilityContext create(ProcessType processType, PathAddress address) {
            CapabilityContext context = CapabilityContext.GLOBAL;
            PathElement pe = processType.isServer() || address.size() == 0 ? null : address.getElement(0);
            if (pe != null) {
                String type = pe.getKey();
                switch (type) {
                    case PROFILE: {
                        context = address.size() == 1 ? ProfilesCapabilityContext.INSTANCE : new ProfileChildCapabilityContext(pe.getValue());
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        context = address.size() == 1 ? SocketBindingGroupsCapabilityContext.INSTANCE : new SocketBindingGroupChildContext(pe.getValue());
                        break;
                    }
                    case HOST: {
                        if (address.size() >= 2) {
                            PathElement hostElement = address.getElement(1);
                            final String hostType = hostElement.getKey();
                            switch (hostType) {
                                case SUBSYSTEM:
                                case SOCKET_BINDING_GROUP:
                                    context = HostCapabilityContext.INSTANCE;
                                    break;
                                case SERVER_CONFIG:
                                    context = ServerConfigCapabilityContext.INSTANCE;
                            }
                        }
                        break;
                    }
                    case SERVER_GROUP :  {
                        context = ServerGroupsCapabilityContext.INSTANCE;
                        break;
                    }
                }
            }
            return context;

        }
    }
}
