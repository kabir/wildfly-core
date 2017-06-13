package org.jboss.as.controller.service;

import org.jboss.marshalling.Externalizer;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface MarshallableService<T> extends Service<T> {
    Externalizer getExternalizer();
    void initialize(ServiceTarget target, ServiceName name);
}
