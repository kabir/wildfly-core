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
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jboss.marshalling.Externalizer;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class DependentServiceExternalizer implements Externalizer {
    @Override
    public void writeExternal(Object subject, ObjectOutput output) throws IOException {
        ((DependentService)subject).marshall(output);
    }

    @Override
    public Object createExternal(Class<?> subjectType, ObjectInput input) throws IOException, ClassNotFoundException {
        int number = input.readInt();
        return new DependentService(number);
    }
}
