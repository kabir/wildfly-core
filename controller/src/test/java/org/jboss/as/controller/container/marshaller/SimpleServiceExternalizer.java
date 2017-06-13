package org.jboss.as.controller.container.marshaller;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jboss.marshalling.Externalizer;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SimpleServiceExternalizer implements Externalizer {
    @Override
    public void writeExternal(Object subject, ObjectOutput output) throws IOException {
    }

    @Override
    public Object createExternal(Class<?> subjectType, ObjectInput input) throws IOException, ClassNotFoundException {
        return new SimpleService();
    }
}
