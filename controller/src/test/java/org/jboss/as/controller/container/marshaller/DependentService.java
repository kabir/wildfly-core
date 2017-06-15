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

import java.io.IOException;
import java.io.ObjectOutput;

import org.jboss.as.controller.service.MarshallableService;
import org.jboss.marshalling.Externalizer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DependentService implements MarshallableService<DependentService> {
    private final int number;
    private final InjectedValue<SimpleService> injectedSimple = new InjectedValue<SimpleService>();

    public DependentService(int number) {
        this.number = number;
    }

    @Override
    public void start(StartContext context) throws StartException {

    }

    @Override
    public void stop(StopContext context) {

    }

    @Override
    public DependentService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    public Externalizer getExternalizer() {
        return new DependentServiceExternalizer();
    }

    @Override
    public void initialize(ServiceTarget target, ServiceName name) {
        target.addService(name, this)
                .addDependency(ServiceName.of("simple"), SimpleService.class, injectedSimple)
                .install();
    }

    void marshall(ObjectOutput output) throws IOException {
        output.writeInt(number);
    }
}
