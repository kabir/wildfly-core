package org.jboss.as.server.deployment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.BatchServiceTarget;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.value.Value;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Utils to track the deployers which have taken effect during a deployment. These deployers are then serialized
 * to a file, for quick boot and less classloading when starting up the server again.
 * <p/>
 * This is generally done by checking if attachments are written to and if services are installed.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProvisioningDeploymentUtils {
    public static final String PROVISION_DEPLOYMENTS = "org.wildfly.provision.deployments";

    public static final String PROVISIONED_DEPLOYERS = "org.wildfly.provisioned.deployers";

    private static volatile Map<Phase, Map<String, Set<Integer>>> deployersByPhaseSubsystemPriority;
    private static Boolean provisionDeployments;
    private static Boolean provisionedDeployers;

    public static void reset() {
        deployersByPhaseSubsystemPriority = null;
        if (readProvisionDeploymentsProperty()) {
            File file = getFile();
            if (file.exists()) {
                file.delete();
            }
        }
        provisionDeployments = null;
        provisionedDeployers = null;
    }

    public static boolean isProvisionDeployments() {
        if (provisionDeployments == null) {
            boolean property = readProvisionDeploymentsProperty();
            if (property && readProvisionedDeployersProperty()) {
                throw ServerLogger.ROOT_LOGGER.cannotUseBothProvisionAndProvisionedProperties();
            }
            provisionDeployments = property;
        }
        return provisionDeployments;
    }

    public static boolean shouldRegisterDeployer(String subsystemName, Phase phase, int priority) {
        if (provisionedDeployers == null) {
            boolean property = readProvisionedDeployersProperty();
            if (property && readProvisionDeploymentsProperty()) {
                throw ServerLogger.ROOT_LOGGER.cannotUseBothProvisionAndProvisionedProperties();
            }
            provisionedDeployers = property;
        }
        if (!provisionedDeployers) {
            return true;
        }
        if (deployersByPhaseSubsystemPriority == null) {
            synchronized (ProvisioningDeploymentUtils.class) {
                if (deployersByPhaseSubsystemPriority == null) {
                    deployersByPhaseSubsystemPriority = loadFile();
                }
            }
        }
        Map<String, Set<Integer>> deployersBySubsystemPriority = deployersByPhaseSubsystemPriority.get(phase);
        if (deployersBySubsystemPriority != null) {
            Set<Integer> priorities = deployersBySubsystemPriority.get(subsystemName);
            if (priorities != null) {
                return priorities.contains(priority);
            }
        }

        return false;
    }

    private static boolean readProvisionDeploymentsProperty() {
        return WildFlySecurityManager.getPropertyPrivileged(PROVISION_DEPLOYMENTS, "false").equals("true");
    }

    private static boolean readProvisionedDeployersProperty() {
        return WildFlySecurityManager.getPropertyPrivileged(PROVISIONED_DEPLOYERS, "false").equals("true");
    }

    private static Map<Phase, Map<String, Set<Integer>>> loadFile() {
        final Map<Phase, Map<String, Set<Integer>>> deployersByPhaseSubsystemPriority =
                Collections.synchronizedMap(new HashMap<>());
        try {

            Map<String, Set<Integer>> deployersBySubsystemPriority = null;
            try (final BufferedReader reader = new BufferedReader(new FileReader(getFile()))) {
                String line = reader.readLine();
                while (line != null) {
                    if (deployersBySubsystemPriority == null) {
                        Phase phaseValue = Phase.valueOf(line);
                        deployersBySubsystemPriority =
                                deployersByPhaseSubsystemPriority.computeIfAbsent(
                                        phaseValue, p -> Collections.synchronizedMap(new HashMap<>()));
                    } else {
                        if (line.length() == 0) {
                            deployersBySubsystemPriority = null;
                        } else {
                            String[] parts = line.split(" ");
                            String subsystem = parts[0];
                            Integer priority = Integer.valueOf(parts[1]);
                            //TODO we probably don't need this since using this information would mean loading the processor class when checking
                            String processorClassName = parts[2];

                            Set<Integer> priorities = deployersBySubsystemPriority.computeIfAbsent(
                                    subsystem, c -> Collections.synchronizedSet(new HashSet<>()));
                            priorities.add(priority);
                        }
                    }
                    line = reader.readLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return deployersByPhaseSubsystemPriority;
    }

    public static void outputPhaseProcessors(Phase phase, List<RegisteredDeploymentUnitProcessor> trackedProcessors) {
        try {
            final File file = getFile();
            boolean first = false;
            if (!file.exists()) {
                file.createNewFile();
                first = true;
            }
            try (final BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
                if (!first) {
                    writer.newLine();
                }
                writer.write(phase.name());
                writer.newLine();
                for (RegisteredDeploymentUnitProcessor processor : trackedProcessors) {
                    writer.write(processor.getSubsystemName());
                    writer.write(" ");
                    writer.write(String.valueOf(processor.getPriority()));
                    writer.write(" ");
                    //TODO we probably don't need this since using this information would mean loading the processor class when checking
                    writer.write(processor.getProcessor().getClass().getName());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static File getFile() {
        String configDirValue = WildFlySecurityManager.getPropertyPrivileged(ServerEnvironment.SERVER_CONFIG_DIR, null);
        File file = new File(configDirValue);
        file = new File(file, "recorded-deployer-chains.txt");
        return file;
    }

    static class ProvisioningDeploymentPhaseContext implements DeploymentPhaseContext {
        private final DeploymentPhaseContext delegate;
        private boolean updatedInformation = false;

        public ProvisioningDeploymentPhaseContext(DeploymentPhaseContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasAttachment(AttachmentKey<?> key) {
            return delegate.hasAttachment(key);
        }

        @Override
        public <T> T getAttachment(AttachmentKey<T> key) {
            return delegate.getAttachment(key);
        }

        @Override
        public ServiceName getPhaseServiceName() {
            return delegate.getPhaseServiceName();
        }

        @Override
        public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
            return delegate.getAttachmentList(key);
        }

        @Override
        public ServiceTarget getServiceTarget() {
            return new ProvisioningServiceTarget(this, delegate.getServiceTarget());
        }

        @Override
        public <T> T putAttachment(AttachmentKey<T> key, T value) {
            updatedInformation = true;
            return delegate.putAttachment(key, value);
        }

        @Override
        public ServiceRegistry getServiceRegistry() {
            return delegate.getServiceRegistry();
        }

        @Override
        public <T> T removeAttachment(AttachmentKey<T> key) {
            updatedInformation = true;
            return delegate.removeAttachment(key);
        }

        @Override
        public DeploymentUnit getDeploymentUnit() {
            return delegate.getDeploymentUnit();
        }

        @Override
        public Phase getPhase() {
            return delegate.getPhase();
        }

        @Override
        public <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value) {
            updatedInformation = true;
            delegate.addToAttachmentList(key, value);
        }

        @Override
        public <T> void addDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey) {
            updatedInformation = true;
            delegate.addDependency(serviceName, attachmentKey);
        }

        @Override
        public <T> void addDependency(ServiceName serviceName, Class<T> type, Injector<T> injector) {
            updatedInformation = true;
            delegate.addDependency(serviceName, type, injector);
        }

        @Override
        public <T> void addDeploymentDependency(ServiceName serviceName, AttachmentKey<T> attachmentKey) {
            updatedInformation = true;
            delegate.addDeploymentDependency(serviceName, attachmentKey);
        }

        public boolean getAndClearUpdatedInformation() {
            boolean updated = updatedInformation;
            updatedInformation = false;
            return updated;
        }
    }

    static class ProvisioningDeploymentUnit implements DeploymentUnit {
        private final DeploymentUnit deploymentUnit;
        private ProvisioningDeploymentPhaseContext phaseContext;

        public ProvisioningDeploymentUnit(DeploymentUnit deploymentUnit) {
            this.deploymentUnit = deploymentUnit;
        }

        void setPhaseContext(ProvisioningDeploymentPhaseContext phaseContext) {
            this.phaseContext = phaseContext;
        }

        @Override
        public boolean hasAttachment(AttachmentKey<?> key) {
            return deploymentUnit.hasAttachment(key);
        }

        @Override
        public ServiceName getServiceName() {
            return deploymentUnit.getServiceName();
        }

        @Override
        public <T> T getAttachment(AttachmentKey<T> key) {
            return deploymentUnit.getAttachment(key);
        }

        @Override
        public DeploymentUnit getParent() {
            return deploymentUnit.getParent();
        }

        @Override
        public <T> List<T> getAttachmentList(AttachmentKey<? extends List<T>> key) {
            return deploymentUnit.getAttachmentList(key);
        }

        @Override
        public String getName() {
            return deploymentUnit.getName();
        }

        @Override
        public ServiceRegistry getServiceRegistry() {
            return deploymentUnit.getServiceRegistry();
        }

        @Override
        public <T> T putAttachment(AttachmentKey<T> key, T value) {
            phaseContext.updatedInformation = true;
            return deploymentUnit.putAttachment(key, value);
        }

        @Override
        @Deprecated
        public ModelNode getDeploymentSubsystemModel(String subsystemName) {
            return deploymentUnit.getDeploymentSubsystemModel(subsystemName);
        }

        @Override
        public <T> T removeAttachment(AttachmentKey<T> key) {
            phaseContext.updatedInformation = true;
            return deploymentUnit.removeAttachment(key);
        }

        @Override
        public <T> void addToAttachmentList(AttachmentKey<AttachmentList<T>> key, T value) {
            phaseContext.updatedInformation = true;
            deploymentUnit.addToAttachmentList(key, value);
        }

        @Override
        @Deprecated
        public ModelNode createDeploymentSubModel(String subsystemName, PathElement address) {
            return deploymentUnit.createDeploymentSubModel(subsystemName, address);
        }

        @Override
        @Deprecated
        public ModelNode createDeploymentSubModel(String subsystemName, PathAddress address) {
            return deploymentUnit.createDeploymentSubModel(subsystemName, address);
        }

        @Override
        @Deprecated
        public ModelNode createDeploymentSubModel(String subsystemName, PathAddress address, Resource resource) {
            return deploymentUnit.createDeploymentSubModel(subsystemName, address, resource);
        }
    }

    private static class ProvisioningServiceTarget implements ServiceTarget {
        private final ProvisioningDeploymentPhaseContext phaseContext;
        private final ServiceTarget target;

        public ProvisioningServiceTarget(ProvisioningDeploymentPhaseContext phaseContext, ServiceTarget target) {
            this.phaseContext = phaseContext;
            this.target = target;
        }

        @Override
        public <T> ServiceBuilder<T> addServiceValue(ServiceName name, Value<? extends Service<T>> value) {
            phaseContext.updatedInformation = true;
            return target.addServiceValue(name, value);
        }

        @Override
        public <T> ServiceBuilder<T> addService(ServiceName name, Service<T> service) {
            phaseContext.updatedInformation = true;
            return target.addService(name, service);
        }

        @Override
        public ServiceTarget addMonitor(StabilityMonitor monitor) {
            phaseContext.updatedInformation = true;
            return target.addMonitor(monitor);
        }

        @Override
        public ServiceTarget addMonitors(StabilityMonitor... monitors) {
            phaseContext.updatedInformation = true;
            return target.addMonitors(monitors);
        }

        @Override
        public ServiceTarget addListener(ServiceListener<Object> listener) {
            phaseContext.updatedInformation = true;
            return target.addListener(listener);
        }

        @Override
        public ServiceTarget addListener(ServiceListener<Object>[] listeners) {
            phaseContext.updatedInformation = true;
            return target.addListener(listeners);
        }

        @Override
        public ServiceTarget addListener(Collection<ServiceListener<Object>> listeners) {
            phaseContext.updatedInformation = true;
            return target.addListener(listeners);
        }

        @Override
        public ServiceTarget removeMonitor(StabilityMonitor monitor) {
            phaseContext.updatedInformation = true;
            return target.removeMonitor(monitor);
        }

        @Override
        public ServiceTarget removeListener(ServiceListener<Object> listener) {
            phaseContext.updatedInformation = true;
            return target.removeListener(listener);
        }

        @Override
        public Set<StabilityMonitor> getMonitors() {
            return target.getMonitors();
        }

        @Override
        public Set<ServiceListener<Object>> getListeners() {
            return target.getListeners();
        }

        @Override
        public ServiceTarget addDependency(ServiceName dependency) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependency);
        }

        @Override
        public ServiceTarget addDependency(ServiceName... dependencies) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependencies);
        }

        @Override
        public ServiceTarget addDependency(Collection<ServiceName> dependencies) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependencies);
        }

        @Override
        public ServiceTarget removeDependency(ServiceName dependency) {
            phaseContext.updatedInformation = true;
            return target.removeDependency(dependency);
        }

        @Override
        public Set<ServiceName> getDependencies() {
            return target.getDependencies();
        }

        @Override
        public ServiceTarget subTarget() {
            return new ProvisioningServiceTarget(phaseContext, target.subTarget());
        }

        @Override
        public BatchServiceTarget batchTarget() {
            return new ProvisioningBatchServiceTarget(phaseContext, target.batchTarget());
        }
    }

    private static class ProvisioningBatchServiceTarget implements BatchServiceTarget {

        private final ProvisioningDeploymentPhaseContext phaseContext;
        private final BatchServiceTarget target;

        public ProvisioningBatchServiceTarget(ProvisioningDeploymentPhaseContext phaseContext, BatchServiceTarget target) {
            this.phaseContext = phaseContext;
            this.target = target;
        }

        @Override
        public void removeServices() {
            phaseContext.updatedInformation = true;
            target.removeServices();
        }

        @Override
        public BatchServiceTarget addMonitor(StabilityMonitor monitor) {
            phaseContext.updatedInformation = true;
            return target.addMonitor(monitor);
        }

        @Override
        public BatchServiceTarget addMonitors(StabilityMonitor... monitors) {
            phaseContext.updatedInformation = true;
            return target.addMonitors(monitors);
        }

        @Override
        public BatchServiceTarget removeMonitor(StabilityMonitor monitor) {
            phaseContext.updatedInformation = true;
            return target.removeMonitor(monitor);
        }

        @Override
        public BatchServiceTarget addListener(ServiceListener<Object> listener) {
            phaseContext.updatedInformation = true;
            return target.addListener(listener);
        }

        @Override
        public BatchServiceTarget addListener(ServiceListener<Object>[] listeners) {
            phaseContext.updatedInformation = true;
            return target.addListener(listeners);
        }

        @Override
        public BatchServiceTarget addListener(Collection<ServiceListener<Object>> listeners) {
            phaseContext.updatedInformation = true;
            return target.addListener(listeners);
        }

        @Override
        public BatchServiceTarget removeListener(ServiceListener<Object> listener) {
            phaseContext.updatedInformation = true;
                        return target.removeListener(listener);
        }

        @Override
        public BatchServiceTarget addDependency(ServiceName dependency) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependency);
        }

        @Override
        public BatchServiceTarget addDependency(ServiceName... dependencies) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependencies);
        }

        @Override
        public BatchServiceTarget addDependency(Collection<ServiceName> dependencies) {
            phaseContext.updatedInformation = true;
            return target.addDependency(dependencies);
        }

        @Override
        public BatchServiceTarget removeDependency(ServiceName dependency) {
            phaseContext.updatedInformation = true;
            return target.removeDependency(dependency);
        }

        @Override
        public <T> ServiceBuilder<T> addServiceValue(ServiceName name, Value<? extends Service<T>> value) {
            phaseContext.updatedInformation = true;
            return target.addServiceValue(name, value);
        }

        @Override
        public <T> ServiceBuilder<T> addService(ServiceName name, Service<T> service) {
            phaseContext.updatedInformation = true;
            return target.addService(name, service);
        }

        @Override
        public Set<StabilityMonitor> getMonitors() {
            return target.getMonitors();
        }

        @Override
        public Set<ServiceListener<Object>> getListeners() {
            return target.getListeners();
        }

        @Override
        public Set<ServiceName> getDependencies() {
            return target.getDependencies();
        }

        @Override
        public ServiceTarget subTarget() {
            return new ProvisioningServiceTarget(phaseContext, target.subTarget());
        }

        @Override
        public BatchServiceTarget batchTarget() {
            return new ProvisioningBatchServiceTarget(phaseContext, target.batchTarget());
        }
    }
}
