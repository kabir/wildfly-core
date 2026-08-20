# Intermediate Pattern (PersistentSubsystemSchema)

Reference: Undertow subsystem in `wildfly/undertow/`

## Extension

Implements `Extension` directly with manual `initialize()` and `initializeParsers()`:

```java
public class MyExtension implements Extension {
    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, MySubsystemModel.CURRENT.getVersion());
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new MySubsystemRootDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        registration.registerSubModel(new SomeChildDefinition());
        subsystem.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(MySubsystemSchema.CURRENT));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(SUBSYSTEM_NAME, EnumSet.allOf(MySubsystemSchema.class));
    }
}
```

Register in `META-INF/services/org.jboss.as.controller.Extension` (or use `@MetaInfServices`).

## Root resource definition

Extends `SimpleResourceDefinition`. Attributes and children are registered via explicit overrides:

```java
class MySubsystemRootDefinition extends SimpleResourceDefinition {
    static final PathElement PATH_ELEMENT = PathElement.pathElement(SUBSYSTEM, "my-subsystem");

    MySubsystemRootDefinition() {
        super(new Parameters(PATH_ELEMENT, MyExtension.getResolver())
                .setAddHandler(new MySubsystemAddHandler())
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addCapabilities(MY_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        for (AttributeDefinition attr : ATTRIBUTES) {
            registration.registerReadWriteAttribute(attr, null, new ReloadRequiredWriteAttributeHandler(attr));
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration registration) {
        registration.registerSubModel(new SomeChildDefinition());
    }
}
```

## Model version

Same `SubsystemModel` enum as the new pattern:

```java
public enum MySubsystemModel implements SubsystemModel {
    VERSION_1_0_0(1),
    VERSION_2_0_0(2),
    ;
    static final MySubsystemModel CURRENT = VERSION_2_0_0;
    // ...
}
```

## XML schema

Implements `PersistentSubsystemSchema` (not `SubsystemResourceXMLSchema`). Overrides `getXMLDescription()` (not `getSubsystemXMLElement()`). Uses `PersistentResourceXMLDescription.factory(this)` and builds with `PathElement` references:

```java
public enum MySubsystemSchema implements PersistentSubsystemSchema<MySubsystemSchema> {
    VERSION_1_0(1, 0),
    VERSION_2_0(2, 0),
    ;
    static final MySubsystemSchema CURRENT = VERSION_2_0;

    private final PersistentResourceXMLDescription.Factory factory = PersistentResourceXMLDescription.factory(this);

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.Builder builder = this.factory.builder(MySubsystemRootDefinition.PATH_ELEMENT);

        // Attributes via stream -- can filter by version
        Stream<AttributeDefinition> attributes = MySubsystemRootDefinition.ATTRIBUTES.stream();
        if (!this.since(VERSION_2_0)) {
            attributes = attributes.filter(Predicate.isEqual(MySubsystemRootDefinition.NEW_ATTR).negate());
        }
        attributes.forEach(builder::addAttribute);

        // Child elements
        builder.addChild(this.factory.builder(SomeChildDefinition.PATH_ELEMENT)
            .addAttributes(SomeChildDefinition.ATTRIBUTES.stream())
            .build());

        return builder.build();
    }
}
```

Key API differences from `SubsystemResourceXMLSchema` (new pattern):

| Aspect | New pattern | Intermediate pattern |
|--------|------------|---------------------|
| Interface | `SubsystemResourceXMLSchema` | `PersistentSubsystemSchema` |
| Override method | `getSubsystemXMLElement()` | `getXMLDescription()` |
| Factory | `ResourceXMLParticleFactory.newInstance(this)` | `PersistentResourceXMLDescription.factory(this)` |
| Builder entry | `this.factory.subsystemElement(REGISTRATION)` | `this.factory.builder(PATH_ELEMENT)` |
| Builder takes | `ResourceRegistration` | `PathElement` |
| Attributes | `addAttributes(List.of(...))` | `addAttributes(Stream)` or `addAttribute(attr)` |

Additional builder features: `.setXmlElementName(...)`, `.setXmlWrapperElement(...)`, `.setNoAddOperation(true)`, `.setAdditionalOperationsGenerator(...)`

## Transformer registration

Implements `ExtensionTransformerRegistration` directly. Loops over all non-current model versions:

```java
public class MyExtensionTransformerRegistration implements ExtensionTransformerRegistration {
    @Override
    public String getSubsystemName() { return MyExtension.SUBSYSTEM_NAME; }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        for (MySubsystemModel model : EnumSet.complementOf(EnumSet.of(MySubsystemModel.CURRENT))) {
            ModelVersion version = model.getVersion();
            ResourceTransformationDescriptionBuilder builder =
                TransformationDescriptionBuilder.Factory.createSubsystemInstance();

            if (MySubsystemModel.VERSION_2_0_0.requiresTransformation(version)) {
                builder.getAttributeBuilder()
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, NEW_ATTR)
                    .addRejectCheck(RejectAttributeChecker.DEFINED, NEW_ATTR)
                    .end();
            }

            // Navigate child paths for child resource transformations
            if (MySubsystemModel.VERSION_1_0_0.requiresTransformation(version)) {
                builder.addChildResource(SomeChildDefinition.PATH_ELEMENT)
                    .getAttributeBuilder()
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, SomeChildDefinition.ANOTHER_ATTR)
                    .addRejectCheck(RejectAttributeChecker.DEFINED, SomeChildDefinition.ANOTHER_ATTR)
                    .end();
            }

            TransformationDescription.Tools.register(builder.build(), registration, version);
        }
    }
}
```

Register in `META-INF/services/org.jboss.as.controller.transform.ExtensionTransformerRegistration` (or use `@MetaInfServices`).
