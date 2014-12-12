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

package org.jboss.as.controller.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.xml.namespace.QName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ModelVersionRange;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Caller;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemXmlWriterRegistry;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.transform.CombinedTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLMapper;

/**
 * A registry for information about {@link org.jboss.as.controller.Extension}s to the core application server.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ExtensionRegistry {

    // Hack to restrict the extensions to which we expose ExtensionContextSupplement
    private static final Set<String> legallySupplemented;
    static {
        Set<String> set = new HashSet<>(4);
        set.add("org.jboss.as.jmx");
        set.add("Test");  // used by shared subsystem test fixture TestModelControllerService
        legallySupplemented = Collections.unmodifiableSet(set);
    }

    private final ProcessType processType;

    private SubsystemXmlWriterRegistry writerRegistry;
    private volatile PathManager pathManager;

    private final ConcurrentMap<String, ExtensionInfo> extensions = new ConcurrentHashMap<String, ExtensionInfo>();
    // subsystem -> extension
    private final ConcurrentMap<String, String> reverseMap = new ConcurrentHashMap<String, String>();
    private final RunningModeControl runningModeControl;
    private final ManagedAuditLogger auditLogger;
    private final JmxAuthorizer authorizer;
    private final ConcurrentHashMap<String, SubsystemInformation> subsystemsInfo = new ConcurrentHashMap<String, SubsystemInformation>();
    private volatile TransformerRegistry transformerRegistry = TransformerRegistry.Factory.create();

    /**
     * Constructor
     *
     * @param processType the type of the process
     * @param runningModeControl the process' running mode
     * @param auditLogger logger for auditing changes
     * @param authorizer hook for exposing access control information to the JMX subsystem
     */
    public ExtensionRegistry(ProcessType processType, RunningModeControl runningModeControl, ManagedAuditLogger auditLogger, JmxAuthorizer authorizer) {
        this.processType = processType;
        this.runningModeControl = runningModeControl;
        this.auditLogger = auditLogger != null ? auditLogger : AuditLogger.NO_OP_LOGGER;
        this.authorizer = authorizer != null ? authorizer : NO_OP_AUTHORIZER;
    }

    /**
     * Constructor
     *
     * @param processType the type of the process
     * @param runningModeControl the process' running mode
     * @deprecated Here for core-model-test and subsystem-test backwards compatibility
     */
    @Deprecated
    public ExtensionRegistry(ProcessType processType, RunningModeControl runningModeControl) {
        this(processType, runningModeControl, null, null);
    }

    /**
     * Sets the {@link SubsystemXmlWriterRegistry} to use for storing subsystem marshallers.
     *
     * @param writerRegistry the writer registry
     */
    public void setWriterRegistry(final SubsystemXmlWriterRegistry writerRegistry) {
        this.writerRegistry = writerRegistry;

    }

    /**
     * Sets the {@link PathManager} to provide {@link ExtensionContext#getPathManager() via the ExtensionContext}.
     *
     * @param pathManager the path manager
     */
    public void setPathManager(final PathManager pathManager) {
        this.pathManager = pathManager;
    }

    public SubsystemInformation getSubsystemInfo(final String name) {
        return subsystemsInfo.get(name);
    }

    /**
     * Gets the module names of all known {@link org.jboss.as.controller.Extension}s.
     *
     * @return the names. Will not return {@code null}
     */
    public Set<String> getExtensionModuleNames() {
        return Collections.unmodifiableSet(extensions.keySet());
    }

    /**
     * Gets information about the subsystems provided by a given {@link org.jboss.as.controller.Extension}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @return map of subsystem names to information about the subsystem.
     */
    public Map<String, SubsystemInformation> getAvailableSubsystems(String moduleName) {
        Map<String, SubsystemInformation> result = null;
        final ExtensionInfo info = extensions.get(moduleName);
        if (info != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (info) {
                result = Collections.unmodifiableMap(new HashMap<String, SubsystemInformation>(info.subsystems));
            }
        }
        return result;
    }

    /**
     * Gets an {@link ExtensionParsingContext} for use when
     * {@link org.jboss.as.controller.Extension#initializeParsers(ExtensionParsingContext) initializing the extension's parsers}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param xmlMapper  the {@link XMLMapper} handling the extension parsing. Can be {@code null} if there won't
     *                   be any actual parsing (e.g. in a slave Host Controller or in a server in a managed domain)
     * @return the {@link ExtensionParsingContext}.  Will not return {@code null}
     */
    public ExtensionParsingContext getExtensionParsingContext(final String moduleName, final XMLMapper xmlMapper) {
        return new ExtensionParsingContextImpl(moduleName, xmlMapper);
    }

    /**
     * Gets an {@link ExtensionContext} for use when handling an {@code add} operation for
     * a resource representing an {@link org.jboss.as.controller.Extension}.
     *
     * @param moduleName the name of the extension's module. Cannot be {@code null}
     * @param rootRegistration the root management resource registration
     * @param isMasterDomainController set to {@code true} if we are the master domain controller, in which case transformers get registered
     *
     * @return  the {@link ExtensionContext}.  Will not return {@code null}
     */
    public ExtensionContext getExtensionContext(final String moduleName, ManagementResourceRegistration rootRegistration, boolean isMasterDomainController) {
        // Can't use processType.isServer() to determine where to look for profile reg because a lot of test infrastructure
        // doesn't add the profile mrr even in HC-based tests
        ManagementResourceRegistration profileRegistration = rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
        if (profileRegistration == null) {
            profileRegistration = rootRegistration;
        }
        ManagementResourceRegistration deploymentsRegistration = processType.isServer() ? rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT))) : null;

        // Hack to restrict extra data to specified extension(s)
        boolean allowSupplement = legallySupplemented.contains(moduleName);
        ManagedAuditLogger al = allowSupplement ? auditLogger : null;
        return new ExtensionContextImpl(moduleName, profileRegistration, deploymentsRegistration, pathManager, isMasterDomainController, al);
    }

    public Set<ProfileParsingCompletionHandler> getProfileParsingCompletionHandlers() {
        Set<ProfileParsingCompletionHandler> result = new HashSet<ProfileParsingCompletionHandler>();

        for (ExtensionInfo extensionInfo : extensions.values()) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (extensionInfo) {
                if (extensionInfo.parsingCompletionHandler != null) {
                    result.add(extensionInfo.parsingCompletionHandler);
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Cleans up a extension module's subsystems from the resource registration model.
     *
     * @param rootResource the model root resource
     * @param moduleName   the name of the extension's module. Cannot be {@code null}
     * @throws IllegalStateException if the extension still has subsystems present in {@code rootResource} or its children
     */
    public void removeExtension(Resource rootResource, String moduleName, ManagementResourceRegistration rootRegistration) throws IllegalStateException {
        // Can't use processType.isServer() to determine where to look for profile reg because a lot of test infrastructure
        // doesn't add the profile mrr even in HC-based tests
        ManagementResourceRegistration profileReg = rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(PROFILE)));
        if (profileReg == null) {
            profileReg = rootRegistration;
        }
        ManagementResourceRegistration deploymentsReg = processType.isServer() ? rootRegistration.getSubModel(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT))) : null;

        ExtensionInfo extension = extensions.remove(moduleName);
        if (extension != null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (extension) {
                Set<String> subsystemNames = extension.subsystems.keySet();
                for (String subsystem : subsystemNames) {
                    if (rootResource.getChild(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem)) != null) {
                        // Restore the data
                        extensions.put(moduleName, extension);
                        throw ControllerLogger.ROOT_LOGGER.removingExtensionWithRegisteredSubsystem(moduleName, subsystem);
                    }
                }
                for (Map.Entry<String, SubsystemInformation> entry : extension.subsystems.entrySet()) {
                    String subsystem = entry.getKey();
                    profileReg.unregisterSubModel(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem));
                    if (deploymentsReg != null) {
                        deploymentsReg.unregisterSubModel(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, subsystem));
                    }

                    if (extension.xmlMapper != null) {
                        SubsystemInformationImpl subsystemInformation = SubsystemInformationImpl.class.cast(entry.getValue());
                        for (String namespace : subsystemInformation.getXMLNamespaces()) {
                            extension.xmlMapper.unregisterRootElement(new QName(namespace, SUBSYSTEM));
                        }
                    }
                }
            }
        }
    }

    /**
     * Clears the registry to prepare for re-registration (e.g. as part of a reload).
     */
    public void clear() {
        synchronized (extensions) {    // we synchronize just to guard unnamedMerged
            transformerRegistry = TransformerRegistry.Factory.create();
            extensions.clear();
            reverseMap.clear();
        }
    }

    /**
     * Records the versions of the subsystems associated with the given {@code moduleName} as properties in the
     * provided {@link ModelNode}. Each subsystem property key will be the subsystem name and the value will be
     * a string composed of the subsystem major version dot appended to its minor version.
     *
     * @param moduleName the name of the extension module
     * @param subsystems a model node of type {@link org.jboss.dmr.ModelType#UNDEFINED} or type {@link org.jboss.dmr.ModelType#OBJECT}
     */
    public void recordSubsystemVersions(String moduleName, ModelNode subsystems) {
        final Map<String, SubsystemInformation> subsystemsInfo = getAvailableSubsystems(moduleName);
        if(subsystemsInfo != null && ! subsystemsInfo.isEmpty()) {
            for(final Map.Entry<String, SubsystemInformation> entry : subsystemsInfo.entrySet()) {
                SubsystemInformation subsystem = entry.getValue();
                subsystems.add(entry.getKey(),
                        subsystem.getManagementInterfaceMajorVersion() + "."
                        + subsystem.getManagementInterfaceMinorVersion()
                        + "." + subsystem.getManagementInterfaceMicroVersion());
            }
        }
    }

    /**
     * Checks if an extension module has initialised its subsystems yet. This is for internal use only.
     *
     * @param moduleName the name of the extension module
     * @return {@code true} if the module has initialised its subsystems
     */
    public boolean hasInitializedSubsystems(String moduleName) {
        final Map<String, SubsystemInformation> infos = getAvailableSubsystems(moduleName);
        if(infos != null && ! infos.isEmpty()) {
            for(final Map.Entry<String, SubsystemInformation> entry : infos.entrySet()) {
                if (this.subsystemsInfo.containsKey(entry.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ExtensionInfo getExtensionInfo(final String extensionModuleName) {
        ExtensionInfo result = extensions.get(extensionModuleName);
        if (result == null) {
            result = new ExtensionInfo(extensionModuleName);
            ExtensionInfo existing = extensions.putIfAbsent(extensionModuleName, result);
            result = existing == null ? result : existing;
        }
        return result;
    }

    private void checkNewSubystem(final String extensionModuleName, final String subsystemName) {
        String existingModule = reverseMap.putIfAbsent(subsystemName, extensionModuleName);
        if (existingModule != null && !extensionModuleName.equals(existingModule)) {
            throw ControllerLogger.ROOT_LOGGER.duplicateSubsystem(subsystemName, extensionModuleName, existingModule);
        }
    }

    public TransformerRegistry getTransformerRegistry() {
        return transformerRegistry;
    }

    private class ExtensionParsingContextImpl implements ExtensionParsingContext {

        private final ExtensionInfo extension;

        private ExtensionParsingContextImpl(String extensionName, XMLMapper xmlMapper) {
            extension = getExtensionInfo(extensionName);
            if (xmlMapper != null) {
                synchronized (extension) {
                    extension.xmlMapper = xmlMapper;
                }
            }
        }

        @Override
        public ProcessType getProcessType() {
            return processType;
        }

        @Override
        public void setSubsystemXmlMapping(String subsystemName, String namespaceUri, XMLElementReader<List<ModelNode>> reader) {
            assert subsystemName != null : "subsystemName is null";
            assert namespaceUri != null : "namespaceUri is null";
            synchronized (extension) {
                extension.getSubsystemInfo(subsystemName).addParsingNamespace(namespaceUri);
                if (extension.xmlMapper != null) {
                    extension.xmlMapper.registerRootElement(new QName(namespaceUri, SUBSYSTEM), reader);
                }
            }
        }

        @Override
        public void setProfileParsingCompletionHandler(ProfileParsingCompletionHandler handler) {
            assert handler != null : "handler is null";
            synchronized (extension) {
                extension.parsingCompletionHandler = handler;
            }
        }
    }

    private class ExtensionContextImpl implements ExtensionContext, ExtensionContextSupplement {

        private final ExtensionInfo extension;
        private final PathManager pathManager;
        private final boolean registerTransformers;
        private final ManagedAuditLogger auditLogger;
        private final boolean allowSupplement;
        private final ManagementResourceRegistration profileRegistration;
        private final ManagementResourceRegistration deploymentsRegistration;

        private ExtensionContextImpl(String extensionName, ManagementResourceRegistration profileResourceRegistration,
                                     ManagementResourceRegistration deploymentsResourceRegistration, PathManager pathManager,
                                     boolean registerTransformers, ManagedAuditLogger auditLogger) {
            assert pathManager != null || !processType.isServer() : "pathManager is null";
            this.pathManager = pathManager;
            this.extension = getExtensionInfo(extensionName);
            this.registerTransformers = registerTransformers;
            this.auditLogger = auditLogger;
            this.allowSupplement = auditLogger != null;
            this.profileRegistration = profileResourceRegistration;

            if (deploymentsResourceRegistration != null) {
                PathAddress subdepAddress = PathAddress.pathAddress(PathElement.pathElement(ModelDescriptionConstants.SUBDEPLOYMENT));
                final ManagementResourceRegistration subdeployments = deploymentsResourceRegistration.getSubModel(subdepAddress);
                this.deploymentsRegistration = subdeployments == null ? deploymentsResourceRegistration
                        : new DeploymentManagementResourceRegistration(deploymentsResourceRegistration, subdeployments);
            } else {
                this.deploymentsRegistration = null;
            }
        }

        @Override
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion) throws IllegalArgumentException, IllegalStateException {
            return registerSubsystem(name, majorVersion, minorVersion, 0);
        }

        @Override
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion) {
            return registerSubsystem(name, majorVersion, minorVersion, microVersion, false);
        }

        @Override
        public SubsystemRegistration registerSubsystem(String name, int majorVersion, int minorVersion, int microVersion, boolean deprecated) {
            assert name != null : "name is null";
            checkNewSubystem(extension.extensionModuleName, name);
            SubsystemInformationImpl info = extension.getSubsystemInfo(name);
            info.setMajorVersion(majorVersion);
            info.setMinorVersion(minorVersion);
            info.setMicroVersion(microVersion);
            info.setDeprecated(deprecated);
            subsystemsInfo.put(name, info);
            if (deprecated){
                ControllerLogger.DEPRECATED_LOGGER.extensionDeprecated(name);
            }
            return new SubsystemRegistrationImpl(name, majorVersion, minorVersion, microVersion,
                    profileRegistration, deploymentsRegistration);
        }

        @Override
        public ProcessType getProcessType() {
            return processType;
        }

        @Override
        public RunningMode getRunningMode() {
            return runningModeControl.getRunningMode();
        }

        @Override
        public boolean isRuntimeOnlyRegistrationValid() {
            return processType.isServer() && runningModeControl.getRunningMode() != RunningMode.ADMIN_ONLY;
        }

        @Override
        public PathManager getPathManager() {
            if (!processType.isServer()) {
                throw ControllerLogger.ROOT_LOGGER.pathManagerNotAvailable(processType);
            }
            return pathManager;
        }

        @Override
        public boolean isRegisterTransformers() {
            return registerTransformers;
        }

        // ExtensionContextSupplement implementation

        /**
         * This method is only for internal use. We do NOT currently want to expose it on the ExtensionContext interface.
         */
        @Override
        public AuditLogger getAuditLogger(boolean inheritConfiguration, boolean manualCommit) {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            if (inheritConfiguration) {
                return auditLogger;
            }
            return auditLogger.createNewConfiguration(manualCommit);
        }

        /**
         * This method is only for internal use. We do NOT currently want to expose it on the ExtensionContext interface.
         */
        @Override
        public JmxAuthorizer getAuthorizer() {
            if (!allowSupplement) {
                throw new UnsupportedOperationException();
            }
            return authorizer;
        }
    }

    private class SubsystemInformationImpl implements SubsystemInformation {

        private Integer majorVersion;
        private Integer minorVersion;
        private Integer microVersion;
        private boolean deprecated = false;
        private final List<String> parsingNamespaces = new ArrayList<String>();

        @Override
        public List<String> getXMLNamespaces() {
            return Collections.unmodifiableList(parsingNamespaces);
        }

        void addParsingNamespace(final String namespace) {
            parsingNamespaces.add(namespace);
        }

        @Override
        public Integer getManagementInterfaceMajorVersion() {
            return majorVersion;
        }

        private void setMajorVersion(Integer majorVersion) {
            this.majorVersion = majorVersion;
        }

        @Override
        public Integer getManagementInterfaceMinorVersion() {
            return minorVersion;
        }

        private void setMinorVersion(Integer minorVersion) {
            this.minorVersion = minorVersion;
        }

        @Override
        public Integer getManagementInterfaceMicroVersion() {
            return microVersion;
        }

        private void setMicroVersion(Integer microVersion) {
            this.microVersion = microVersion;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        private void setDeprecated(boolean deprecated) {
            this.deprecated = deprecated;
        }
    }

    private class SubsystemRegistrationImpl implements SubsystemRegistration {
        private final String name;
        private final ModelVersion version;
        private final ManagementResourceRegistration profileRegistration;
        private final ManagementResourceRegistration deploymentsRegistration;

        private SubsystemRegistrationImpl(String name, int major, int minor, int micro,
                                          ManagementResourceRegistration profileRegistration,
                                          ManagementResourceRegistration deploymentsRegistration) {
            assert profileRegistration != null;
            this.name = name;
            this.profileRegistration = profileRegistration;
            this.deploymentsRegistration = deploymentsRegistration;
            this.version = ModelVersion.create(major, minor, micro);
        }

        @Override
        public ManagementResourceRegistration registerSubsystemModel(ResourceDefinition resourceDefinition) {
            assert resourceDefinition != null : "resourceDefinition is null";

            return profileRegistration.registerSubModel(resourceDefinition);
        }

        @Override
        public ManagementResourceRegistration registerDeploymentModel(ResourceDefinition resourceDefinition) {
            assert resourceDefinition != null : "resourceDefinition is null";
            final ManagementResourceRegistration deploymentsReg = deploymentsRegistration;
            ManagementResourceRegistration base = deploymentsReg != null
                    ? deploymentsReg
                    : getDummyRegistration();
            return base.registerSubModel(resourceDefinition);
        }

        @Override
        public void registerXMLElementWriter(XMLElementWriter<SubsystemMarshallingContext> writer) {
            writerRegistry.registerSubsystemWriter(name, writer);
        }

        @Override
        public TransformersSubRegistration registerModelTransformers(final ModelVersionRange range, final ResourceTransformer subsystemTransformer) {
            return transformerRegistry.registerSubsystemTransformers(name, range, subsystemTransformer);
        }

        @Override
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean placeholder) {
            return transformerRegistry.registerSubsystemTransformers(name, version, resourceTransformer, operationTransformer, placeholder);
        }

        @Override
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer) {
            return transformerRegistry.registerSubsystemTransformers(name, version, resourceTransformer, operationTransformer, false);
        }


        @Override
        public TransformersSubRegistration registerModelTransformers(ModelVersionRange version, CombinedTransformer combinedTransformer) {
            return transformerRegistry.registerSubsystemTransformers(name, version, combinedTransformer, combinedTransformer, false);
        }

        private ManagementResourceRegistration getDummyRegistration() {
            return ManagementResourceRegistration.Factory.create(new DescriptionProvider() {
                @Override
                public ModelNode getModelDescription(Locale locale) {
                    return new ModelNode();
                }
            });
        }

        @Override
        public ModelVersion getSubsystemVersion() {
            return version;
        }
    }

    private class ExtensionInfo {
        private final Map<String, SubsystemInformation> subsystems = new HashMap<String, SubsystemInformation>();
        private final String extensionModuleName;
        private XMLMapper xmlMapper;
        private ProfileParsingCompletionHandler parsingCompletionHandler;

        public ExtensionInfo(String extensionModuleName) {
            this.extensionModuleName = extensionModuleName;
        }


        private SubsystemInformationImpl getSubsystemInfo(final String subsystemName) {
            checkNewSubystem(extensionModuleName, subsystemName);
            synchronized (this) {
                SubsystemInformationImpl subsystem = SubsystemInformationImpl.class.cast(subsystems.get(subsystemName));
                if (subsystem == null) {
                    subsystem = new SubsystemInformationImpl();
                    subsystems.put(subsystemName, subsystem);
                }
                return subsystem;
            }

        }
    }

    private static class DeploymentManagementResourceRegistration implements ManagementResourceRegistration {

        private final ManagementResourceRegistration deployments;
        private final ManagementResourceRegistration subdeployments;

        private DeploymentManagementResourceRegistration(final ManagementResourceRegistration deployments,
                                                         final ManagementResourceRegistration subdeployments) {
            this.deployments = deployments;
            this.subdeployments = subdeployments;
        }

        @Override
        public boolean isRuntimeOnly() {
            return deployments.isRuntimeOnly();
        }

        @Override
        public void setRuntimeOnly(final boolean runtimeOnly) {
            deployments.setRuntimeOnly(runtimeOnly);
            subdeployments.setRuntimeOnly(runtimeOnly);
        }


        @Override
        public boolean isRemote() {
            return deployments.isRemote();
        }

        @Override
        public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
            return deployments.getOperationHandler(address, operationName);
        }

        @Override
        public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
            return deployments.getOperationDescription(address, operationName);
        }

        @Override
        public Set<OperationEntry.Flag> getOperationFlags(PathAddress address, String operationName) {
            return deployments.getOperationFlags(address, operationName);
        }

        @Override
        public OperationEntry getOperationEntry(PathAddress address, String operationName) {
            return deployments.getOperationEntry(address, operationName);
        }

        @Override
        public Set<String> getAttributeNames(PathAddress address) {
            return deployments.getAttributeNames(address);
        }

        @Override
        public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
            return deployments.getAttributeAccess(address, attributeName);
        }

        @Override
        public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
            return deployments.getNotificationDescriptions(address, inherited);
        }

        @Override
        public Set<String> getChildNames(PathAddress address) {
            return deployments.getChildNames(address);
        }

        @Override
        public Set<PathElement> getChildAddresses(PathAddress address) {
            return deployments.getChildAddresses(address);
        }

        @Override
        public DescriptionProvider getModelDescription(PathAddress address) {
            return deployments.getModelDescription(address);
        }

        @Override
        public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
            return deployments.getOperationDescriptions(address, inherited);
        }

        @Override
        public ProxyController getProxyController(PathAddress address) {
            return deployments.getProxyController(address);
        }

        @Override
        public Set<ProxyController> getProxyControllers(PathAddress address) {
            return deployments.getProxyControllers(address);
        }

        @Override
        public ManagementResourceRegistration getOverrideModel(String name) {
            return deployments.getOverrideModel(name);
        }

        @Override
        public ManagementResourceRegistration getSubModel(PathAddress address) {
            return deployments.getSubModel(address);
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return deployments.getAccessConstraints();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ManagementResourceRegistration registerSubModel(PathElement address, DescriptionProvider descriptionProvider) {
            ManagementResourceRegistration depl = deployments.registerSubModel(address, descriptionProvider);
            ManagementResourceRegistration subdepl = subdeployments.registerSubModel(address, descriptionProvider);
            return new DeploymentManagementResourceRegistration(depl, subdepl);
        }

        @Override
        public ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition) {
            ManagementResourceRegistration depl = deployments.registerSubModel(resourceDefinition);
            ManagementResourceRegistration subdepl = subdeployments.registerSubModel(resourceDefinition);
            return new DeploymentManagementResourceRegistration(depl, subdepl);
        }

        @Override
        public void unregisterSubModel(PathElement address) {
            deployments.unregisterSubModel(address);
            subdeployments.unregisterSubModel(address);
        }

        @Override
        public boolean isAllowsOverride() {
            return deployments.isAllowsOverride();
        }

        @Override
        public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
            ManagementResourceRegistration depl = deployments.registerOverrideModel(name, descriptionProvider);
            ManagementResourceRegistration subdepl = subdeployments.registerOverrideModel(name, descriptionProvider);
            return new DeploymentManagementResourceRegistration(depl, subdepl);
        }

        @Override
        public void unregisterOverrideModel(String name) {
            deployments.unregisterOverrideModel(name);
            subdeployments.unregisterOverrideModel(name);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, EnumSet<OperationEntry.Flag> flags) {
            deployments.registerOperationHandler(operationName, handler, descriptionProvider, flags);
            subdeployments.registerOperationHandler(operationName, handler, descriptionProvider, flags);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited) {
            deployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited);
            subdeployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, OperationEntry.EntryType entryType) {
            deployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType);
            subdeployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, OperationEntry.EntryType entryType, EnumSet<OperationEntry.Flag> flags) {
            deployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType, flags);
            subdeployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, entryType, flags);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void registerOperationHandler(String operationName, OperationStepHandler handler, DescriptionProvider descriptionProvider, boolean inherited, EnumSet<OperationEntry.Flag> flags) {
            deployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, flags);
            subdeployments.registerOperationHandler(operationName, handler, descriptionProvider, inherited, flags);
        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler) {
            registerOperationHandler(definition, handler, false);
        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {
            deployments.registerOperationHandler(definition, handler, inherited);
            subdeployments.registerOperationHandler(definition, handler, inherited);
        }

        @Override
        public void unregisterOperationHandler(String operationName) {
            deployments.unregisterOperationHandler(operationName);
            subdeployments.unregisterOperationHandler(operationName);
        }

        @Override
        public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler) {
            deployments.registerReadWriteAttribute(definition, readHandler, writeHandler);
            subdeployments.registerReadWriteAttribute(definition, readHandler, writeHandler);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void registerReadOnlyAttribute(String attributeName, OperationStepHandler readHandler, AttributeAccess.Storage storage) {
            deployments.registerReadOnlyAttribute(attributeName, readHandler, storage);
            subdeployments.registerReadOnlyAttribute(attributeName, readHandler, storage);
        }

        @Override
        public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
            deployments.registerReadOnlyAttribute(definition, readHandler);
            subdeployments.registerReadOnlyAttribute(definition, readHandler);
        }

        @Override
        public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
            deployments.registerMetric(definition, metricHandler);
            subdeployments.registerMetric(definition, metricHandler);
        }

        @Override
        public void unregisterAttribute(String attributeName) {
            deployments.unregisterAttribute(attributeName);
            subdeployments.unregisterAttribute(attributeName);
        }

        @Override
        public void registerNotification(NotificationDefinition notification, boolean inherited) {
            deployments.registerNotification(notification, inherited);
            subdeployments.registerNotification(notification, inherited);
        }

        @Override
        public void registerNotification(NotificationDefinition notification) {
            deployments.registerNotification(notification);
            subdeployments.registerNotification(notification);
        }

        @Override
        public void unregisterNotification(String notificationType) {
            deployments.unregisterNotification(notificationType);
            subdeployments.unregisterNotification(notificationType);
        }

        @Override
        public void registerProxyController(PathElement address, ProxyController proxyController) {
            deployments.registerProxyController(address, proxyController);
            subdeployments.registerProxyController(address, proxyController);
        }

        @Override
        public void unregisterProxyController(PathElement address) {
            deployments.unregisterProxyController(address);
            subdeployments.unregisterProxyController(address);
        }

        @Override
        public void registerAlias(PathElement address, AliasEntry alias) {
            deployments.registerAlias(address, alias);
            subdeployments.registerAlias(address, alias);
        }

        @Override
        public void unregisterAlias(PathElement address) {
            deployments.unregisterAlias(address);
            subdeployments.unregisterAlias(address);
        }

        @Override
        public AliasEntry getAliasEntry() {
            return deployments.getAliasEntry();
        }

        @Override
        public boolean isAlias() {
            return deployments.isAlias();
        }
    }

    private static final JmxAuthorizer NO_OP_AUTHORIZER = new JmxAuthorizer() {

        @Override
        public AuthorizationResult authorize(Caller caller, Environment callEnvironment, Action action, TargetResource target) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizerDescription getDescription() {
            return new AuthorizerDescription() {
                @Override
                public boolean isRoleBased() {
                    return false;
                }

                @Override
                public Set<String> getStandardRoles() {
                    return Collections.emptySet();
                }
            };
        }

        @Override
        public AuthorizationResult authorize(Caller caller, Environment callEnvironment, Action action, TargetAttribute target) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public AuthorizationResult authorizeJmxOperation(Caller caller, Environment callEnvironment, JmxAction action) {
            return AuthorizationResult.PERMITTED;
        }

        @Override
        public Set<String> getCallerRoles(Caller caller, Environment callEnvironment, Set<String> runAsRoles) {
            return null;
        }

        @Override
        public void setNonFacadeMBeansSensitive(boolean sensitive) {
        }
    };
}
