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
package org.jboss.as.controller.extension;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.AttributeAccess.Storage;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.OperationEntry.EntryType;
import org.jboss.as.controller.registry.OperationEntry.Flag;

/**
 * <p>Wrapper for ManagementResourceRegistration allowing changes to be made simultaneously to two underlying
 * ManagementResourceRegistration instances.</p>
 *
 * <p>This class is only used to register subsystem resources for subsystems where {@link org.jboss.as.controller.SubsystemRegistration#setHostCapable()} has been called.
 * This makes sure that the normal profile resources definitions added to the domain model also get added to the host model. After the extension initialisation
 * has happened this class is thrown away, and it is NOT used outside the extension initialisation process. Thus, the expected use case is
 * mainly that the registerXXXX() methods will get called. However, care has been taken to make all methods work logically.</p>
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class DualProfileManagementResourceRegistration implements ManagementResourceRegistration {
    private final ManagementResourceRegistration primary;
    private final ManagementResourceRegistration secondary;

    public DualProfileManagementResourceRegistration(ManagementResourceRegistration primary,
            ManagementResourceRegistration secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public boolean isRuntimeOnly() {
        return primary.isRuntimeOnly();
    }

    @Override
    public boolean isRemote() {
        return primary.isRemote();
    }

    @Override
    public boolean isAlias() {
        return primary.isAlias();
    }

    @Override
    public AliasEntry getAliasEntry() {
        return primary.getAliasEntry();
    }

    @Override
    public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
        return primary.getOperationHandler(address, operationName);
    }

    @Override
    public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
        return primary.getOperationDescription(address, operationName);
    }

    @Override
    public Set<Flag> getOperationFlags(PathAddress address, String operationName) {
        return primary.getOperationFlags(address, operationName);
    }

    @Override
    public OperationEntry getOperationEntry(PathAddress address, String operationName) {
        return primary.getOperationEntry(address, operationName);
    }

    @Override
    public Set<String> getAttributeNames(PathAddress address) {
        return primary.getAttributeNames(address);
    }

    @Override
    public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
        return primary.getAttributeAccess(address, attributeName);
    }

    @Override
    public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
        return primary.getNotificationDescriptions(address, inherited);
    }

    @Override
    public Set<String> getChildNames(PathAddress address) {
        return primary.getChildNames(address);
    }

    @Override
    public Set<PathElement> getChildAddresses(PathAddress address) {
        return primary.getChildAddresses(address);
    }

    @Override
    public DescriptionProvider getModelDescription(PathAddress address) {
        return primary.getModelDescription(address);
    }

    @Override
    public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
        return primary.getOperationDescriptions(address, inherited);
    }

    @Override
    public ProxyController getProxyController(PathAddress address) {
        return primary.getProxyController(address);
    }

    @Override
    public Set<ProxyController> getProxyControllers(PathAddress address) {
        return primary.getProxyControllers(address);
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return primary.getAccessConstraints();
    }

    @Override
    public ManagementResourceRegistration getOverrideModel(String name) {
        ManagementResourceRegistration primaryReg = primary.getOverrideModel(name);
        if (primaryReg == null) {
            return null;
        }
        ManagementResourceRegistration secondaryReg = secondary.getOverrideModel(name);
        assert secondaryReg != null : "A primary registration with no secondary registration should not be possible";
        return new DualProfileManagementResourceRegistration(primaryReg, secondaryReg);
    }

    @Override
    public ManagementResourceRegistration getSubModel(PathAddress address) {
        ManagementResourceRegistration primaryReg = primary.getSubModel(address);
        if (primaryReg == null) {
            return null;
        }
        ManagementResourceRegistration secondaryReg = secondary.getSubModel(address);
        assert secondaryReg != null : "A primary registration with no secondary registration should not be possible";
        return new DualProfileManagementResourceRegistration(primaryReg, secondaryReg);
    }

    @Override
    public ManagementResourceRegistration registerSubModel(PathElement address, DescriptionProvider descriptionProvider) {
        ManagementResourceRegistration primaryReg = primary.registerSubModel(address, descriptionProvider);
        ManagementResourceRegistration secondaryReg = secondary.registerSubModel(address, descriptionProvider);
        return new DualProfileManagementResourceRegistration(primaryReg, secondaryReg);
    }

    @Override
    public ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition) {
        ManagementResourceRegistration primaryReg = primary.registerSubModel(resourceDefinition);
        ManagementResourceRegistration secondaryReg = secondary.registerSubModel(resourceDefinition);
        return new DualProfileManagementResourceRegistration(primaryReg, secondaryReg);
    }

    @Override
    public void unregisterSubModel(PathElement address) {
        primary.unregisterSubModel(address);
        secondary.unregisterSubModel(address);
    }

    @Override
    public boolean isAllowsOverride() {
        return primary.isAllowsOverride();
    }

    @Override
    public void setRuntimeOnly(boolean runtimeOnly) {
        primary.setRuntimeOnly(runtimeOnly);
        secondary.setRuntimeOnly(runtimeOnly);
    }

    @Override
    public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
        ManagementResourceRegistration primaryReg = primary.registerOverrideModel(name, descriptionProvider);
        ManagementResourceRegistration secondaryReg = secondary.registerOverrideModel(name, descriptionProvider);
        return new DualProfileManagementResourceRegistration(primaryReg, secondaryReg);
    }

    @Override
    public void unregisterOverrideModel(String name) {
        primary.unregisterOverrideModel(name);
        secondary.unregisterOverrideModel(name);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler,
            DescriptionProvider descriptionProvider, EnumSet<Flag> flags) {
        primary.registerOperationHandler(operationName, handler, descriptionProvider, flags);
        secondary.registerOperationHandler(operationName, handler, descriptionProvider, flags);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler,
            DescriptionProvider descriptionProvider, boolean inherited) {
        primary.registerOperationHandler(operationName, handler, descriptionProvider, inherited);
        secondary.registerOperationHandler(operationName, handler, descriptionProvider, inherited);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler,
            DescriptionProvider descriptionProvider, boolean inherited, EntryType entryType) {
        primary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType);
        secondary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler,
            DescriptionProvider descriptionProvider, boolean inherited, EnumSet<Flag> flags) {
        primary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, flags);
        secondary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, flags);
    }

    @Override
    public void registerOperationHandler(String operationName, OperationStepHandler handler,
            DescriptionProvider descriptionProvider, boolean inherited, EntryType entryType, EnumSet<Flag> flags) {
        primary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType, flags);
        secondary.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType, flags);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler) {
        primary.registerOperationHandler(definition, handler);
        secondary.registerOperationHandler(definition, handler);
    }

    @Override
    public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
        primary.registerOperationHandler(definition, handler, inherited);
        secondary.registerOperationHandler(definition, handler, inherited);
    }

    @Override
    public void unregisterOperationHandler(String operationName) {
        primary.unregisterOperationHandler(operationName);
        secondary.unregisterOperationHandler(operationName);
    }

    @Override
    public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler,
            OperationStepHandler writeHandler) {
        primary.registerReadWriteAttribute(definition, readHandler, writeHandler);
        secondary.registerReadWriteAttribute(definition, readHandler, writeHandler);
    }

    @Override
    public void registerReadOnlyAttribute(String attributeName, OperationStepHandler readHandler, Storage storage) {
        primary.registerReadOnlyAttribute(attributeName, readHandler, storage);
        secondary.registerReadOnlyAttribute(attributeName, readHandler, storage);
    }

    public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
        primary.registerReadOnlyAttribute(definition, readHandler);
        secondary.registerReadOnlyAttribute(definition, readHandler);
    }

    public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        primary.registerMetric(definition, metricHandler);
        secondary.registerMetric(definition, metricHandler);
    }

    public void unregisterAttribute(String attributeName) {
        primary.unregisterAttribute(attributeName);
        secondary.unregisterAttribute(attributeName);
    }

    public void registerProxyController(PathElement address, ProxyController proxyController) {
        primary.registerProxyController(address, proxyController);
        secondary.registerProxyController(address, proxyController);
    }

    public void unregisterProxyController(PathElement address) {
        primary.unregisterProxyController(address);
        secondary.unregisterProxyController(address);
    }

    public void registerAlias(PathElement address, AliasEntry aliasEntry) {
        primary.registerAlias(address, aliasEntry);
        secondary.registerAlias(address, aliasEntry);
    }

    public void unregisterAlias(PathElement address) {
        primary.unregisterAlias(address);
        secondary.unregisterAlias(address);
    }

    public void registerNotification(NotificationDefinition notification, boolean inherited) {
        primary.registerNotification(notification, inherited);
        secondary.registerNotification(notification, inherited);
    }

    public void registerNotification(NotificationDefinition notification) {
        primary.registerNotification(notification);
        secondary.registerNotification(notification);
    }

    public void unregisterNotification(String notificationType) {
        primary.unregisterNotification(notificationType);
        secondary.unregisterNotification(notificationType);
    }
}
