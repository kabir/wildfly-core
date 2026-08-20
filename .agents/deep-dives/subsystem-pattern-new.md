# New Pattern (SubsystemExtension)

Reference: IO subsystem in `wildfly-core/io/`

## File layout

```
my-subsystem/
  src/main/java/.../
    MyExtension.java                              # extends SubsystemExtension
    MySubsystemResourceDefinitionRegistrar.java   # implements SubsystemResourceDefinitionRegistrar
    MySubsystemModel.java                         # enum implements SubsystemModel
    MySubsystemSchema.java                        # enum implements SubsystemResourceXMLSchema
    MyExtensionTransformerRegistration.java        # extends SubsystemExtensionTransformerRegistration
    MySubsystemTransformationDescriptionFactory.java  # enum implements Function<ModelVersion, TransformationDescription>
    SomeChildResourceDefinition.java
  src/main/resources/
    META-INF/services/
      org.jboss.as.controller.Extension
      org.jboss.as.controller.transform.ExtensionTransformerRegistration
    schema/
      wildfly-my-subsystem_1_0.xsd
    org/.../LocalDescriptions.properties
```

## Extension

```java
public class MyExtension extends SubsystemExtension<MySubsystemSchema> {
    public MyExtension() {
        super(
            SubsystemConfiguration.of(
                MySubsystemResourceDefinitionRegistrar.REGISTRATION,
                MySubsystemModel.CURRENT,
                MySubsystemResourceDefinitionRegistrar::new),
            SubsystemPersistence.of(MySubsystemSchema.CURRENT));
    }
}
```

Register in `META-INF/services/org.jboss.as.controller.Extension`.

## Root resource definition

Implements `SubsystemResourceDefinitionRegistrar`. Attributes and capabilities are declared via `ResourceDescriptor.builder()`:

```java
class MySubsystemResourceDefinitionRegistrar implements SubsystemResourceDefinitionRegistrar {

    static final SubsystemResourceRegistration REGISTRATION = SubsystemResourceRegistration.of("my-subsystem");
    static final ParentResourceDescriptionResolver RESOLVER =
        new SubsystemResourceDescriptionResolver(REGISTRATION.getName(), MySubsystemResourceDefinitionRegistrar.class);

    @Override
    public ManagementResourceRegistration register(SubsystemRegistration parent, ManagementResourceRegistrationContext context) {
        ManagementResourceRegistration registration = parent.registerSubsystemModel(
            ResourceDefinition.builder(REGISTRATION, RESOLVER).build());

        ResourceDescriptor descriptor = ResourceDescriptor.builder(RESOLVER)
                .addAttributes(List.of(SOME_ATTRIBUTE))
                .addCapabilities(List.of(SOME_CAPABILITY))
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureParentService(this))
                .build();
        ManagementResourceRegistrar.of(descriptor).register(registration);

        registration.registerSubModel(new SomeChildResourceDefinition());
        return registration;
    }
}
```

## Model version

```java
public enum MySubsystemModel implements SubsystemModel {
    VERSION_1_0_0(1),
    VERSION_2_0_0(2),
    ;
    static final MySubsystemModel CURRENT = VERSION_2_0_0;

    private final ModelVersion version;

    MySubsystemModel(int major) {
        this.version = ModelVersion.create(major);
    }

    @Override
    public ModelVersion getVersion() { return this.version; }
}
```

## XML schema

Implements `SubsystemResourceXMLSchema`. Uses `ResourceXMLParticleFactory` and builds with `ResourceRegistration` references. Gate version-specific content with `this.since()`:

```java
public enum MySubsystemSchema implements SubsystemResourceXMLSchema<MySubsystemSchema> {
    VERSION_1_0(1, 0),
    VERSION_2_0(2, 0),
    ;
    static final MySubsystemSchema CURRENT = VERSION_2_0;

    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);
    private final VersionedNamespace<IntVersion, MySubsystemSchema> namespace;

    MySubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(
            MySubsystemResourceDefinitionRegistrar.REGISTRATION.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, MySubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        SubsystemResourceRegistrationXMLElement.Builder builder =
            this.factory.subsystemElement(MySubsystemResourceDefinitionRegistrar.REGISTRATION);

        NamedResourceRegistrationXMLElement.Builder childBuilder =
            this.factory.namedElement(ResourceRegistration.of(SomeChildResourceDefinition.PATH))
                .addAttributes(List.of(SomeChildResourceDefinition.MY_ATTR));

        if (this.since(VERSION_2_0)) {
            childBuilder.addAttribute(SomeChildResourceDefinition.NEW_ATTR);
        }

        builder.withContent(childBuilder.build());
        return builder.build();
    }
}
```

## Transformer registration

Uses `SubsystemExtensionTransformerRegistration` with a `Function<ModelVersion, TransformationDescription>` factory:

```java
public class MyExtensionTransformerRegistration extends SubsystemExtensionTransformerRegistration {
    public MyExtensionTransformerRegistration() {
        super(
            MySubsystemResourceDefinitionRegistrar.REGISTRATION,
            MySubsystemModel.CURRENT,
            MySubsystemTransformationDescriptionFactory.INSTANCE);
    }
}
```

```java
public enum MySubsystemTransformationDescriptionFactory implements Function<ModelVersion, TransformationDescription> {
    INSTANCE;

    @Override
    public TransformationDescription apply(ModelVersion version) {
        ResourceTransformationDescriptionBuilder builder =
            TransformationDescriptionBuilder.Factory.createSubsystemInstance();

        if (MySubsystemModel.VERSION_2_0_0.requiresTransformation(version)) {
            builder.getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, NEW_ATTR)
                .addRejectCheck(RejectAttributeChecker.DEFINED, NEW_ATTR)
                .end();
        }

        return builder.build();
    }
}
```

Register in `META-INF/services/org.jboss.as.controller.transform.ExtensionTransformerRegistration`.
