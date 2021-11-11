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

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Uses an analysis of registry metadata to provide a default description of an operation that adds a resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DefaultResourceAddDescriptionProvider implements DescriptionProvider {


    private static AttributeDefinition INDEX = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.ADD_INDEX, ModelType.INT, true).build();


    private final ImmutableManagementResourceRegistration registration;
    final ResourceDescriptionResolver descriptionResolver;
    final boolean orderedChildResource;

    public DefaultResourceAddDescriptionProvider(final ImmutableManagementResourceRegistration registration,
                                                 final ResourceDescriptionResolver descriptionResolver) {
        this(registration, descriptionResolver, false);
    }

    public DefaultResourceAddDescriptionProvider(final ImmutableManagementResourceRegistration registration,
            final ResourceDescriptionResolver descriptionResolver,
            final boolean orderedChildResource) {
        this.registration = registration;
        this.descriptionResolver = descriptionResolver;
        this.orderedChildResource = orderedChildResource;
    }

    public Collection<? extends AttributeDefinition> mergeOperationParametersWithResourceAttributes(
            Collection<? extends AttributeDefinition> operationParams) {
        return new LazyCollection(operationParams == null ? Collections.emptySet() : operationParams);
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        final ResourceBundle bundle = descriptionResolver.getResourceBundle(locale);
        final Map<AttributeDefinition.NameAndGroup, ModelNode> sortedDescriptions = new TreeMap<>();

        AttributeCollector collector = new AttributeCollector() {
            @Override
            public void attributeFound(AttributeDefinition def) {
                addAttributeDescriptionToMap(def, locale, bundle);
            }

            @Override
            public void attributeWithNoDefinition(String attrName) {
                sortedDescriptions.put(new AttributeDefinition.NameAndGroup(attrName), new ModelNode());
            }

            @Override
            public void addIndexAttribute() {
                addAttributeDescriptionToMap(INDEX, locale, bundle);
            }

            private void addAttributeDescriptionToMap(AttributeDefinition def, Locale locale, ResourceBundle bundle) {
                ModelNode attrDesc = new ModelNode();
                // def will add the description to attrDesc under "request-properties" => { attr
                def.addOperationParameterDescription(attrDesc, ADD, descriptionResolver, locale, bundle);
                sortedDescriptions.put(new AttributeDefinition.NameAndGroup(def), attrDesc.get(REQUEST_PROPERTIES, def.getName()));
            }
        };

        iterateResourceParameters(collector);

        ModelNode result = new ModelNode();
        result.get(OPERATION_NAME).set(ADD);
        result.get(DESCRIPTION).set(descriptionResolver.getOperationDescription(ADD, locale, bundle));


        // Store the sorted descriptions into the overall result
        final ModelNode params = result.get(REQUEST_PROPERTIES).setEmptyObject();
        for (Map.Entry<AttributeDefinition.NameAndGroup, ModelNode> entry : sortedDescriptions.entrySet()) {
            params.get(entry.getKey().getName()).set(entry.getValue());
        }

        //This is auto-generated so don't add any access constraints

        result.get(REPLY_PROPERTIES).setEmptyObject();


        return result;

    }


    private void iterateResourceParameters(AttributeCollector attributeCollector) {
        // Sort the attribute descriptions based on attribute group and then attribute name
        Set<String> attributeNames = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);

        for (String attr : attributeNames)  {
            AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
            if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                AttributeDefinition def = attributeAccess.getAttributeDefinition();
                if (def != null) {
                    if (!def.isResourceOnly()){
                        attributeCollector.attributeFound(def);
                    }
                } else {
                    // We may want to put in a placeholder
                    attributeCollector.attributeWithNoDefinition(attr);
                }
            }
        }

        if (orderedChildResource) {
            //Add the index property to the add operation
            attributeCollector.addIndexAttribute();
        }
    }

    private interface AttributeCollector {
        void attributeFound(AttributeDefinition attrDef);

        void attributeWithNoDefinition(String attrName);

        void addIndexAttribute();
    }

    class LazyCollection implements Collection<AttributeDefinition> {
        private final Collection<? extends AttributeDefinition> opDescParameters;
        volatile int size = -1;
        private final AtomicBoolean merged = new AtomicBoolean();

        public LazyCollection(Collection<? extends AttributeDefinition> opDescParameters) {
            this.opDescParameters = opDescParameters;
        }

        @Override
        public int size() {
            if (size == -1) {
                synchronized (this) {
                    if (size == -1) {
                        size = merge().size();
                    }
                }
            }
            return size;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean contains(Object o) {
            return false;
        }

        @Override
        public Iterator<AttributeDefinition> iterator() {
            return merge().iterator();
        }

        @Override
        public Object[] toArray() {
            Collection<? extends AttributeDefinition> merged = merge();
            return merged.toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            Collection<? extends AttributeDefinition> merged = merge();
            int size = this.size();
            T[] r = a.length >= size ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size);
            return merged.toArray(r);
        }

        @Override
        public boolean add(AttributeDefinition attributeDefinition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            Collection<? extends AttributeDefinition> merged = merge();
            return merged.containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends AttributeDefinition> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        private Collection<AttributeDefinition> merge() {
            final Map<AttributeDefinition.NameAndGroup, AttributeDefinition> sortedDescriptions = new TreeMap<>();
            if (opDescParameters != null && opDescParameters.size() > 0) {
                for (AttributeDefinition def : opDescParameters) {
                    sortedDescriptions.put(new AttributeDefinition.NameAndGroup(def), def);
                }
            }

            AttributeCollector collector = new AttributeCollector() {
                @Override
                public void attributeFound(AttributeDefinition attrDef) {
                    addAttributeIfNotAlreadyDefinedByOperation(attrDef);
                }

                @Override
                public void attributeWithNoDefinition(String attrName) {
                    // Hopefully we can skip this one
                }

                @Override
                public void addIndexAttribute() {
                    addAttributeIfNotAlreadyDefinedByOperation(INDEX);
                }

                private void addAttributeIfNotAlreadyDefinedByOperation(AttributeDefinition definition) {
                    AttributeDefinition.NameAndGroup ng = new AttributeDefinition.NameAndGroup(definition);
                    if (!sortedDescriptions.containsKey(ng)) {
                        sortedDescriptions.put(ng, definition);
                    }
                }
            };

            iterateResourceParameters(collector);
            return sortedDescriptions.values();
        }
    }
}
