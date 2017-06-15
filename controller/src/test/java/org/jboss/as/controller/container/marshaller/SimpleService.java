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
