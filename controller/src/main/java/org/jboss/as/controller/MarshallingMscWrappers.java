package org.jboss.as.controller;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.BatchServiceTarget;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StabilityMonitor;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.Value;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MarshallingMscWrappers {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final Map<ServiceName, ServiceName> SEEN_NAMES = new ConcurrentHashMap<>();

    //TODO use some kind of startup-flag for this
    private static final boolean WRAP = Boolean.getBoolean("wildfly.marshall.services");

    public static ServiceTarget wrapTarget(ServiceTarget target) {
        if (WRAP) {

            if (target instanceof CapabilityServiceTarget) {
                return new RecordingCapabilityServiceTarget((CapabilityServiceTarget)target);
            }
            return new MarshallingMscWrappers.RecordingServiceTarget<>(target);
        } else {
            return target;
        }
    }

    public static CapabilityServiceTarget wrapTarget(CapabilityServiceTarget target) {
        if (WRAP) {
            //TODO use some kind of startup-flag for this
            return new RecordingCapabilityServiceTarget((CapabilityServiceTarget)target);
        } else {
            return target;
        }
    }

    static class RecordingServiceTarget<T extends ServiceTarget> implements ServiceTarget {
        protected final T delegate;

        private RecordingServiceTarget(T delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> ServiceBuilder<T> addServiceValue(ServiceName name, Value<? extends Service<T>> value) {
            if (!SEEN_NAMES.containsKey(name)) {
                SEEN_NAMES.put(name, name);
                System.out.println("----> addServiceValue: " + name + ": " + value.getValue() + " " + COUNTER.incrementAndGet());
            }
            return delegate.addServiceValue(name, value);
        }

        @Override
        public <T> ServiceBuilder<T> addService(ServiceName name, Service<T> service) {
            if (!SEEN_NAMES.containsKey(name)) {
                SEEN_NAMES.put(name, name);
                System.out.println("----> addService: " + name + ": " + service + " " + COUNTER.incrementAndGet());
            }
            return delegate.addService(name, new DelegatingService<>(service));
        }

        @Override
        public ServiceTarget addMonitor(StabilityMonitor monitor) {
            return delegate.addMonitor(monitor);
        }

        @Override
        public ServiceTarget addMonitors(StabilityMonitor... monitors) {
            return delegate.addMonitors(monitors);
        }

        @Override
        public ServiceTarget addListener(ServiceListener<Object> listener) {
            return delegate.addListener(listener);
        }

        @Override
        public ServiceTarget addListener(ServiceListener<Object>[] listeners) {
            return delegate.addListener(listeners);
        }

        @Override
        public ServiceTarget addListener(Collection<ServiceListener<Object>> listeners) {
            return delegate.addListener(listeners);
        }

        @Override
        public ServiceTarget removeMonitor(StabilityMonitor monitor) {
            return delegate.removeMonitor(monitor);
        }

        @Override
        public ServiceTarget removeListener(ServiceListener<Object> listener) {
            return delegate.removeListener(listener);
        }

        @Override
        public Set<StabilityMonitor> getMonitors() {
            return delegate.getMonitors();
        }

        @Override
        public Set<ServiceListener<Object>> getListeners() {
            return delegate.getListeners();
        }

        @Override
        public ServiceTarget addDependency(ServiceName dependency) {
            return delegate.addDependency(dependency);
        }

        @Override
        public ServiceTarget addDependency(ServiceName... dependencies) {
            return delegate.addDependency(dependencies);
        }

        @Override
        public ServiceTarget addDependency(Collection<ServiceName> dependencies) {
            return delegate.addDependency(dependencies);
        }

        @Override
        public ServiceTarget removeDependency(ServiceName dependency) {
            return delegate.removeDependency(dependency);
        }

        @Override
        public Set<ServiceName> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public ServiceTarget subTarget() {
            return wrapTarget(delegate.subTarget());
        }

        @Override
        public BatchServiceTarget batchTarget() {
            return delegate.batchTarget();
        }
    }

    private static class RecordingCapabilityServiceTarget extends RecordingServiceTarget<CapabilityServiceTarget> implements CapabilityServiceTarget {
        RecordingCapabilityServiceTarget(CapabilityServiceTarget delegate) {
            super(delegate);
        }

        public <T> CapabilityServiceBuilder<T> addCapability(RuntimeCapability<?> capability, Service<T> service) throws IllegalArgumentException {
            if (capability.isDynamicallyNamed()){
                return (CapabilityServiceBuilder<T>)addService(capability.getCapabilityServiceName(delegate.getTargetAddress()), service);
            }else{
                return (CapabilityServiceBuilder<T>)addService(capability.getCapabilityServiceName(), service);
            }
        }

        @Override
        public PathAddress getTargetAddress() {
            return delegate.getTargetAddress();
        }
    }

    private static class DelegatingService<T> implements Service<T> {
        private final Service<T> delegate;

        public DelegatingService(Service<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start(StartContext context) throws StartException {
            delegate.start(new RecordingStartContext(context));
        }

        @Override
        public void stop(StopContext context) {
            delegate.stop(context);
        }

        @Override
        public T getValue() throws IllegalStateException, IllegalArgumentException {
            return delegate.getValue();
        }
    }

    private static class RecordingStartContext implements StartContext {

        private final StartContext delegate;

        public RecordingStartContext(StartContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public void asynchronous() {
            delegate.asynchronous();
        }

        @Override
        public void failed(StartException reason) throws IllegalStateException {
            delegate.failed(reason);
        }

        @Override
        public void complete() throws IllegalStateException {
            delegate.complete();
        }

        @Override
        public ServiceTarget getChildTarget() {
            return wrapTarget(delegate.getChildTarget());
        }

        @Override
        public long getElapsedTime() {
            return delegate.getElapsedTime();
        }

        @Override
        public ServiceController<?> getController() {
            return delegate.getController();
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }
    }
}
