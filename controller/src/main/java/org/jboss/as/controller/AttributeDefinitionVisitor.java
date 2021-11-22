/*
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2021 Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jboss.as.controller;

import java.util.List;
import java.util.Map;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface AttributeDefinitionVisitor<T> {

    interface Context<T> {
        boolean isTopLevel();
        Long getMin();
        Long getMax();
        Long getMinSize();
        Long getMaxSize();
        List<ModelNode> getAllowedValues();
        ChildResultType getChildResultType();
        T getChildResult();
        Map<String, T> getChildResults();
    }

    enum ChildResultType {
        NONE,
        SINGLE,
        MULTIPLE
    }

    default boolean isIncludeAllowedValues() {
        return false;
    }

    default boolean isVisitValueTypes() {
        return false;
    }

    default boolean isIncludeMaxMin() {
        return false;
    }

    T visitSimpleValueType(ModelType type, Context<T> context);

    T visitSimpleType(AttributeDefinition attr, Context<T> context);

    T visitListType(ListAttributeDefinition attr, Context<T> context);

    T visitObjectType(ObjectTypeAttributeDefinition attr, Context<T> context);

    T visitObjectType(MapAttributeDefinition attr, Context<T> context);

    T visitPropertiesType(PropertiesAttributeDefinition attr, Context<T> context);
}
