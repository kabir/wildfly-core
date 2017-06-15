/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller.container.marshaller;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.service.MarshallableService;
import org.jboss.marshalling.Externalizer;
import org.jboss.marshalling.MappingClassExternalizerFactory;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.SimpleClassResolver;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class MarshallingTestCase {
    private static final MarshallerFactory MARSHALLER_FACTORY;
    static {
        MARSHALLER_FACTORY = Marshalling.getMarshallerFactory("river", Marshalling.class.getClassLoader());
    }

    @Test
    public void testMarshalling() throws Exception {
        ServiceContainer originalContainer = ServiceContainer.Factory.create(true);
        ServiceTarget originalTarget = originalContainer.subTarget();

        ServiceName simpleName = ServiceName.of("simple");
        MarshallableService<SimpleService> simpleService = new SimpleService();
        simpleService.initialize(originalTarget, simpleName);

        ServiceName dependentName = ServiceName.of("dependent");
        MarshallableService<DependentService> dependentService = new DependentService(5);
        dependentService.initialize(originalTarget, dependentName);

        MarshallingConfiguration configuration = getBaseConfiguration();
        configuration.setClassExternalizerFactory(new MappingClassExternalizerFactory(Collections.singletonMap(SimpleService.class, simpleService.getExternalizer())));
        byte[] simpleBytes = doMarshall(configuration, simpleService);


        configuration = getBaseConfiguration();
        configuration.setClassExternalizerFactory(new MappingClassExternalizerFactory(Collections.singletonMap(DependentService.class, new DependentServiceExternalizer())));
        byte[] dependentBytes = doMarshall(configuration, dependentService);

        originalContainer.awaitStability(5, SECONDS);
        originalContainer.dumpServices();

        System.out.println("-----------");

        ServiceContainer unmarshalledContainer = ServiceContainer.Factory.create(true);
        ServiceTarget unmarshalledTarget = unmarshalledContainer.subTarget();

        configuration = getBaseConfiguration();
        //TODO read this from somewhere
        Map<Class<?>, Externalizer> externalizers = new HashMap<>();
        externalizers.put(SimpleService.class, simpleService.getExternalizer());
        externalizers.put(DependentService.class, dependentService.getExternalizer());
        configuration.setClassExternalizerFactory(new MappingClassExternalizerFactory(externalizers));

        MarshallableService<?> unmarshalledSimpleService = doUnmarshall(configuration, simpleBytes);
        unmarshalledSimpleService.initialize(unmarshalledTarget, simpleName);

        MarshallableService<?> unmarshalledDependentService = doUnmarshall(configuration, dependentBytes);
        unmarshalledDependentService.initialize(unmarshalledTarget, dependentName);

        unmarshalledContainer.awaitStability(5, SECONDS);
        unmarshalledContainer.dumpServices();
    }

    private byte[] doMarshall(MarshallingConfiguration configuration, Service<?> service) {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();) {
            try (Marshaller marshaller = MARSHALLER_FACTORY.createMarshaller(configuration);) {
                marshaller.start(Marshalling.createByteOutput(bout));
                marshaller.writeObject(service);
                marshaller.finish();
            } finally {
                IoUtils.safeClose(bout);
            }
            return bout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MarshallableService<?> doUnmarshall(MarshallingConfiguration configuration, byte[] bytes) {
        try (ByteArrayInputStream bin = new ByteArrayInputStream(bytes)) {
            try (Unmarshaller unmarshaller = MARSHALLER_FACTORY.createUnmarshaller(configuration)) {
                unmarshaller.start(Marshalling.createByteInput(bin));
                Object o = unmarshaller.readObject();
                unmarshaller.finish();
                return (MarshallableService<?>)o;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } finally {
                IoUtils.safeClose(bin);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MarshallingConfiguration getBaseConfiguration() {
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setVersion(4);
        config.setClassResolver(new SimpleClassResolver(this.getClass().getClassLoader()));
        return config;
    }

}
