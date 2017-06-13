package org.jboss.as.controller.container.marshaller;

import org.jboss.as.controller.service.MarshallableService;
import org.jboss.marshalling.Externalizer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SimpleService implements MarshallableService<SimpleService> {

    @Override
    public void start(StartContext context) throws StartException {

    }

    @Override
    public void stop(StopContext context) {

    }

    @Override
    public SimpleService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public Externalizer getExternalizer() {
        return new SimpleServiceExternalizer();
    }

    @Override
    public void initialize(ServiceTarget target, ServiceName name) {
        target.addService(name, this)
                .install();
    }
}
