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

import static org.jboss.as.controller.AttributeDefinitionVisitor.ChildResultType.MULTIPLE;
import static org.jboss.as.controller.AttributeDefinitionVisitor.ChildResultType.NONE;
import static org.jboss.as.controller.AttributeDefinitionVisitor.ChildResultType.SINGLE;
import static org.jboss.as.controller.AttributeDefinitionVisitorTest.TestVisitor.MAX_SIZE;
import static org.jboss.as.controller.AttributeDefinitionVisitorTest.TestVisitor.MIN_SIZE;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AttributeDefinitionVisitorTest {

    protected static class TestVisitor implements AttributeDefinitionVisitor<TestVisitorReturn> {
        protected static final String MAX = "max";
        protected static final String MAX_SIZE = "max-size";
        protected static final String MIN = "min";
        protected static final String MIN_SIZE = "min-size";
        protected static final String ALLOWED_VALUES = "allowed0values";

        @Override
        public boolean isVisitValueTypes() {
            return true;
        }

        @Override
        public boolean isIncludeMaxMin() {
            return true;
        }

        @Override
        public boolean isIncludeAllowedValues() {
            return true;
        }

        @Override
        public TestVisitorReturn visitSimpleValueType(ModelType type, Context<TestVisitorReturn> context) {
            return new TestVisitorReturn(type, context);
        }

        @Override
        public TestVisitorReturn visitSimpleType(AttributeDefinition attr, Context<TestVisitorReturn> context) {
            Assert.assertNull(context.getChildResult());
            return new TestVisitorReturn(attr, context);
        }

        @Override
        public TestVisitorReturn visitListType(ListAttributeDefinition attr, Context<TestVisitorReturn> context) {
            return new TestVisitorReturn(attr, context);
        }

        @Override
        public TestVisitorReturn visitObjectType(MapAttributeDefinition attr, Context<TestVisitorReturn> context) {
            return new TestVisitorReturn(attr, context);
        }

        @Override
        public TestVisitorReturn visitObjectType(ObjectTypeAttributeDefinition attr, Context<TestVisitorReturn> context) {
            return new TestVisitorReturn(attr, context);
        }

        @Override
        public TestVisitorReturn visitPropertiesType(PropertiesAttributeDefinition attr, Context<TestVisitorReturn> context) {
            Assert.assertNull(context.getChildResult());
            return new TestVisitorReturn(attr, context);
        }

        void checkContextEmpty(Context<TestVisitorReturn> context, String... exclusions) {

            Set<String> exclSet = new HashSet<>(Arrays.asList(exclusions));
            checkSetOrNull(context, exclSet, MAX, () -> context.getMax());

            checkSetOrNull(context, exclSet, MAX_SIZE, () -> context.getMaxSize());
            checkSetOrNull(context, exclSet, MIN, () -> context.getMin());
            checkSetOrNull(context, exclSet, MIN_SIZE, () -> context.getMinSize());
            checkEmptyOrNot(context, exclSet, ALLOWED_VALUES, () -> context.getAllowedValues());
        }

        void checkSetOrNull(Context<TestVisitorReturn> context, Set<String> exclSet, String name, Supplier<Object> supplier) {
            if (exclSet.contains(name)) {
                Assert.assertNotNull(name, supplier.get());
            } else {
                Assert.assertNull(name, supplier.get());
            }
        }

        private void checkEmptyOrNot(Context<TestVisitorReturn> context, Set<String> exclSet, String name, Supplier<Collection<?>> supplier) {
            Collection<?> collection = supplier.get();
            if (exclSet.contains(name)) {
                Assert.assertFalse(name, collection.isEmpty());
            } else {
                Assert.assertTrue(name, collection.isEmpty());
            }
        }

        void checkAllowedValues(Context<TestVisitorReturn> context, String... values) {
            Assert.assertEquals(values.length, context.getAllowedValues().size());
            Iterator<ModelNode> it = context.getAllowedValues().iterator();
            for (String value : values) {
                Assert.assertEquals(value, it.next().asString());
            }
        }
    }

    protected static class TestVisitorReturn {
        final AttributeDefinition attr;
        final AttributeDefinitionVisitor.Context<TestVisitorReturn> context;
        final ModelType simpleType;
        TestVisitorReturn valueType;

        public TestVisitorReturn(AttributeDefinition attr, AttributeDefinitionVisitor.Context<TestVisitorReturn> context) {
            this.attr = attr;
            this.context = context;
            this.simpleType = null;
            this.valueType = null;
        }

        public TestVisitorReturn(ModelType simpleType, AttributeDefinitionVisitor.Context<TestVisitorReturn> context) {
            this.simpleType = simpleType;
            this.context = context;
            this.valueType = null;
            this.attr = null;
        }
    }


    enum TestEnum {
        A, B, C
    }

    @Test
    public void testSimpleAttributeNoValidatorString() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        visitor.checkContextEmpty(rtn.context, TestVisitor.MAX_SIZE, TestVisitor.MIN_SIZE);
        Assert.assertEquals((long)Integer.MAX_VALUE, rtn.context.getMaxSize().longValue());
        Assert.assertEquals(1L, rtn.context.getMinSize().longValue());
    }

    @Test
    public void testSimpleAttributeNoValidatorInt() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.INT)
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context);
    }

    @Test
    public void testSimpleAttributeAllowedValuesWithValidator() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setValidator(new EnumValidator<>(TestEnum.class, false, false, TestEnum.A, TestEnum.B))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context, TestVisitor.ALLOWED_VALUES);
        visitor.checkAllowedValues(rtn.context, "A", "B");
    }

    @Test
    public void testSimpleAttributeAllowedValues() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.INT)
                .setAllowedValues("A","B")
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context, TestVisitor.ALLOWED_VALUES);
        visitor.checkAllowedValues(rtn.context, "A", "B");
    }

    @Test
    public void testSimpleAttributeMinMax() {
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.INT)
                .setValidator(new IntRangeValidator(10, 30))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context, TestVisitor.MAX, TestVisitor.MIN);
        Assert.assertEquals(30L, rtn.context.getMax().longValue());
        Assert.assertEquals(10L, rtn.context.getMin().longValue());
    }

    @Test
    public void testSimpleAttributeMinMaxSize() {

        ModelNode toValidate = new ModelNode();
        ModelNode paramVal = toValidate.get("test");

        // Test that explicitly configured validator controls.
        // This isn't some sort of ideal behavior, so if we can make setMin/MaxSize take precedence
        // that's fine and this part of the test can be changed. This is more a sanity check
        // that the current code is doing what's expected.
        SimpleAttributeDefinition ad = new SimpleAttributeDefinitionBuilder("test", ModelType.STRING)
                .setValidator(new StringLengthValidator(2, 3, false, false))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ad.accept(visitor);

        Assert.assertSame(ad, rtn.attr);
        Assert.assertNull(rtn.valueType);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context, TestVisitor.MAX_SIZE, TestVisitor.MIN_SIZE);
        Assert.assertEquals(3L, rtn.context.getMaxSize().longValue());
        Assert.assertEquals(2L, rtn.context.getMinSize().longValue());
    }

    @Test
    public void testProperties() throws OperationFailedException {
        ModelNode defaultValue = new ModelNode();
        defaultValue.add("key","value");
        defaultValue.add("key2","value");
        PropertiesAttributeDefinition ld = new PropertiesAttributeDefinition.Builder("test", true)
                .setAllowExpression(true)
                .setDefaultValue(defaultValue)
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ld.accept(visitor);
        Assert.assertEquals(NONE, rtn.context.getChildResultType());
        Assert.assertEquals(ld, rtn.attr);
        Assert.assertNull(rtn.valueType);
        visitor.checkContextEmpty(rtn.context);
    }


    @Test
    public void testObjectType() throws OperationFailedException {

        AttributeDefinition a = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
        AttributeDefinition b = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();

        ObjectTypeAttributeDefinition ld = new ObjectTypeAttributeDefinition.Builder("test", a, b)
                .setAllowExpression(true)
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn rtn = ld.accept(visitor);
        Assert.assertSame(ld, rtn.attr);
        Assert.assertEquals(MULTIPLE, rtn.context.getChildResultType());
        visitor.checkContextEmpty(rtn.context);

        TestVisitorReturn rtnA = rtn.context.getChildResults().get("a");
        Assert.assertEquals(a, rtnA.attr);
        Assert.assertNull(rtnA.valueType);
        Assert.assertEquals(NONE, rtnA.context.getChildResultType());

        TestVisitorReturn rtnB = rtn.context.getChildResults().get("b");
        Assert.assertEquals(a, rtnA.attr);
    }

    @Test
    public void testPrimitiveList() {

        PrimitiveListAttributeDefinition ld = PrimitiveListAttributeDefinition.Builder.of("test", ModelType.INT)
                .setAllowExpression(true)
                .setElementValidator(new IntRangeValidator(1, false, true))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = ld.accept(visitor);
        Assert.assertEquals(ld, result.attr);
        visitor.checkContextEmpty(result.context, MAX_SIZE, MIN_SIZE);
        Assert.assertEquals((long)Integer.MAX_VALUE, result.context.getMaxSize().longValue());
        // The minimum size here is the list length rather than the parameter length
        Assert.assertEquals(0L, result.context.getMinSize().longValue());
        Assert.assertEquals(SINGLE, result.context.getChildResultType());
        Assert.assertEquals(ModelType.INT, result.context.getChildResult().simpleType);
    }


    @Test
    public void testStringList() {

        StringListAttributeDefinition ld = StringListAttributeDefinition.Builder.of("test")
                .setAllowExpression(true)
                .setElementValidator(new IntRangeValidator(1, false, true))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = ld.accept(visitor);
        Assert.assertEquals(ld, result.attr);
        visitor.checkContextEmpty(result.context, MAX_SIZE, MIN_SIZE);
        Assert.assertEquals((long)Integer.MAX_VALUE, result.context.getMaxSize().longValue());
        // The minimum size here is the list length rather than the parameter length
        Assert.assertEquals(0L, result.context.getMinSize().longValue());
        Assert.assertEquals(SINGLE, result.context.getChildResultType());
        Assert.assertEquals(ModelType.STRING, result.context.getChildResult().simpleType);
    }

    @Test
    public void testSimpleList() {

        AttributeDefinition ad = SimpleAttributeDefinitionBuilder.create("x", ModelType.INT).setAllowExpression(true).build();
        SimpleListAttributeDefinition ld = SimpleListAttributeDefinition.Builder.of("test", ad)
                .setAllowExpression(true)
                .setValidator(new IntRangeValidator(1, false, true))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = ld.accept(visitor);
        Assert.assertEquals(ld, result.attr);
        visitor.checkContextEmpty(result.context, MAX_SIZE, MIN_SIZE);
        Assert.assertEquals((long)Integer.MAX_VALUE, result.context.getMaxSize().longValue());
        // The minimum size here is the list length rather than the parameter length
        Assert.assertEquals(0L, result.context.getMinSize().longValue());
        Assert.assertEquals(SINGLE, result.context.getChildResultType());
        Assert.assertEquals(ad, result.context.getChildResult().attr);
    }


    @Test
    public void testObjectList() {

        AttributeDefinition a = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
        AttributeDefinition b = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();

        ObjectTypeAttributeDefinition otad = new ObjectTypeAttributeDefinition.Builder("", a, b)
                .setAllowExpression(true)
                .build();

        ObjectListAttributeDefinition ld = ObjectListAttributeDefinition.Builder.of("test", otad).build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = ld.accept(visitor);
        Assert.assertEquals(ld, result.attr);
        visitor.checkContextEmpty(result.context, MAX_SIZE, MIN_SIZE);
        Assert.assertEquals((long)Integer.MAX_VALUE, result.context.getMaxSize().longValue());
        // The minimum size here is the list length rather than the parameter length
        Assert.assertEquals(0L, result.context.getMinSize().longValue());
        Assert.assertEquals(SINGLE, result.context.getChildResultType());

        TestVisitorReturn otaReturn = result.context.getChildResult();
        Assert.assertEquals(otad, otaReturn.attr);
        visitor.checkContextEmpty(otaReturn.context);
        Assert.assertEquals(MULTIPLE, otaReturn.context.getChildResultType());

        Map<String, TestVisitorReturn> map = otaReturn.context.getChildResults();

        TestVisitorReturn aReturn = map.get("a");
        Assert.assertEquals(a, aReturn.attr);
        visitor.checkContextEmpty(aReturn.context);
        Assert.assertEquals(NONE, aReturn.context.getChildResultType());

        TestVisitorReturn bReturn = map.get("b");
        Assert.assertEquals(b, bReturn.attr);
        visitor.checkContextEmpty(bReturn.context);
        Assert.assertEquals(NONE, bReturn.context.getChildResultType());
    }

    @Test
    public void testSimpleMap() {

        SimpleMapAttributeDefinition ld = new SimpleMapAttributeDefinition.Builder("test", false)
                .setAllowExpression(true)
                .setValidator(new IntRangeValidator(1, false, true))
                .build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = ld.accept(visitor);
        Assert.assertEquals(ld, result.attr);
        visitor.checkContextEmpty(result.context);
        Assert.assertEquals(SINGLE, result.context.getChildResultType());
        Assert.assertEquals(ModelType.STRING, result.context.getChildResult().simpleType);
        Assert.assertEquals(NONE, result.context.getChildResult().context.getChildResultType());
    }

    @Test
    public void testObjectMap() {
        AttributeDefinition attribute1 = SimpleAttributeDefinitionBuilder.create("a", ModelType.INT).build();
        AttributeDefinition attribute2 = SimpleAttributeDefinitionBuilder.create("b", ModelType.BOOLEAN).setAllowExpression(true).build();
        AttributeDefinition attribute3 = SimpleAttributeDefinitionBuilder.create("c", ModelType.STRING).setAllowExpression(true).build();
        ObjectTypeAttributeDefinition complex = ObjectTypeAttributeDefinition.create("complex", attribute1, attribute2, attribute3).build();
        ObjectMapAttributeDefinition map = ObjectMapAttributeDefinition.create("map", complex).build();

        TestVisitor visitor = new TestVisitor();
        TestVisitorReturn result = map.accept(visitor);
        Assert.assertEquals(map, result.attr);
        visitor.checkContextEmpty(result.context);
        Assert.assertEquals(SINGLE, result.context.getChildResultType());

        TestVisitorReturn complexReturn = result.context.getChildResult();
        Assert.assertEquals(complex, complexReturn.attr);
        visitor.checkContextEmpty(complexReturn.context);
        Assert.assertEquals(MULTIPLE, complexReturn.context.getChildResultType());


    }
}
