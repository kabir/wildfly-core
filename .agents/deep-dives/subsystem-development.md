# Subsystem Development Guide

This guide covers how to work with WildFly subsystems: adding and modifying resources, attributes, operations, and managing versioning across releases.

## Three patterns

Existing subsystems use one of three patterns. When modifying a subsystem, follow whichever pattern it already uses -- don't mix patterns within a subsystem.

| Pattern | Extension base | XML parsing | Example subsystems |
|---------|---------------|-------------|-------------------|
| [**New**](subsystem-pattern-new.md) | `SubsystemExtension` | `SubsystemResourceXMLSchema` enum with `ResourceXMLParticleFactory` | IO (`wildfly-core/io/`) |
| [**Intermediate**](subsystem-pattern-intermediate.md) | `implements Extension` (manual `initialize`/`initializeParsers`) | `PersistentSubsystemSchema` enum with `PersistentResourceXMLDescription` | Undertow (`wildfly/undertow/`) |
| [**Legacy**](subsystem-pattern-legacy.md) | `implements Extension` (manual `initialize`/`initializeParsers`) | Separate per-version parser classes with manual StAX code | Deployment Scanner, Logging (`wildfly-core/`) |

The pattern-specific docs cover: extension wiring, root resource definitions, XML parsing, and transformer registration. Everything below applies to all three patterns.

## Attributes

### Common attribute types

```java
// Simple string attribute (optional, expression-allowed)
static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING, true)
        .setAllowExpression(true)
        .build();

// Integer with default and range validation
static final SimpleAttributeDefinition MAX_THREADS = new SimpleAttributeDefinitionBuilder("max-threads", ModelType.INT, true)
        .setDefaultValue(new ModelNode(16))
        .setValidator(IntRangeValidator.NON_NEGATIVE)
        .setAllowExpression(true)
        .build();

// Boolean with restart flag
static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder("enabled", ModelType.BOOLEAN, true)
        .setDefaultValue(ModelNode.TRUE)
        .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
        .setAllowExpression(true)
        .build();

// Capability reference (links to another resource's capability)
static final CapabilityReferenceAttributeDefinition<XnioWorker> DEFAULT_WORKER =
    new CapabilityReferenceAttributeDefinition.Builder<>("default-worker",
        CapabilityReference.builder(MY_CAPABILITY, IOServiceDescriptor.NAMED_WORKER).build())
        .setRequired(false)
        .build();
```

### Child resources

Child resources typically extend `PersistentResourceDefinition`:

```java
class SomeChildResourceDefinition extends PersistentResourceDefinition {
    static final PathElement PATH = pathElement(PathElement.WILDCARD_VALUE);

    static final SimpleAttributeDefinition MY_ATTR = new SimpleAttributeDefinitionBuilder("my-attr", ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1))
            .build();

    SomeChildResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PATH, RESOLVER.createChildResolver(PATH))
                .setAddHandler(new SomeChildAddHandler())
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .addCapabilities(CAPABILITY));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return List.of(MY_ATTR);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        resourceRegistration.registerSubModel(new NestedChildDefinition());
    }
}
```

### Operations

Operations are registered on the `ManagementResourceRegistration`:

```java
@Override
public void registerOperations(ManagementResourceRegistration registration) {
    super.registerOperations(registration);
    registration.registerOperationHandler(
        new SimpleOperationDefinitionBuilder("my-operation", RESOLVER)
            .setParameters(PARAM1, PARAM2)
            .setReplyType(ModelType.STRING)
            .setRuntimeOnly()
            .build(),
        new MyOperationHandler());
}
```

### Description resources (i18n)

Add descriptions for new attributes, resources, and operations in `LocalDescriptions.properties`:

```properties
my-subsystem=My Subsystem
my-subsystem.add=Adds the subsystem
my-subsystem.remove=Removes the subsystem
my-subsystem.some-attr=Description of the attribute
my-subsystem.child=Description of the child resource
my-subsystem.child.add=Adds a child
my-subsystem.child.remove=Removes a child
my-subsystem.child.my-attr=Description of the child attribute
```

Naming convention: `<subsystem-name>[.<child-path-value>]*.<attribute-or-operation-name>`

### XSD files

Each schema version has a corresponding XSD file in `src/main/resources/schema/`:
- `wildfly-my-subsystem_1_0.xsd`
- `wildfly-my-subsystem_2_0.xsd`

The XSD namespace follows the pattern: `urn:jboss:domain:my-subsystem:2.0`

When adding a new schema version, create a new XSD file by copying the previous version and adding the new elements/attributes. Update the namespace in the XSD to match.

## Versioning

There are two independent version numbers:

- **Model version** (`SubsystemModel` enum or raw `ModelVersion.create()`) -- the version of the management model structure. Bump when adding attributes, resources, or changing defaults.
- **XML schema version** (`SubsystemSchema` enum or `Namespace` enum) -- the version of the XML namespace. Bump when the XML structure changes (new elements/attributes in XML).

Both typically need bumping when adding a new attribute that appears in XML. A purely runtime attribute or operation may only need a model version bump.

### When to bump versions

**If the subsystem has already been changed since the last Final release**, the versions have already been bumped and you don't need to change them again. Just add your changes gated behind the existing `CURRENT` version.

**If this is the first change since the last Final release**, you need to:

1. Bump the model version (add a new enum constant or increment the version ints).
2. Bump the XML schema version if the XML structure changed (add a new enum constant or parser class).
3. Create a new XSD file for the new schema version.
4. Gate new attributes/elements behind the new version.
5. Add transformers and transformer tests (see below).
6. Add a test XML file for the new schema version.

### How to check if versions have already been bumped

Look at the git history for the subsystem's model and schema enums since the last Final release. If someone has already added a new version constant, you don't need to add another -- just use `this.since(THAT_VERSION)` to gate your changes.

## Transformers

Transformers handle domain mode compatibility by converting the current model to older model versions. When you add a new model version, you need transformers so that older domain controllers can still manage newer hosts.

The wiring for transformer registration differs by pattern -- see the pattern-specific docs. The transformer operations themselves are the same:

### Common transformer operations

| Operation | Use case |
|-----------|----------|
| `setDiscard(DiscardAttributeChecker.UNDEFINED, attr)` | Silently drop attribute if not set |
| `setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(defaultVal), attr)` | Discard if attribute equals the legacy default |
| `addRejectCheck(RejectAttributeChecker.DEFINED, attr)` | Reject transformation if attribute IS defined (can't represent in older model) |
| `addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, attr)` | Reject if attribute uses expressions (older version doesn't support them) |
| `rejectChildResource(path)` | Reject a child resource that doesn't exist in older version |
| `setValueConverter(AttributeConverter.DEFAULT_VALUE, attr)` | Convert attribute values |

The general pattern is: if the attribute has a safe default or is unset, discard it silently. If the user has set a non-default value that can't be represented in the older model, reject the transformation so the user knows it's incompatible.

## Testing

### Subsystem parsing/boot test

Parameterized over all schema versions. The framework parses the test XML, boots the subsystem, and validates the model:

```java
@RunWith(Parameterized.class)
public class MySubsystemTestCase extends AbstractSubsystemSchemaTest<MySubsystemSchema> {

    @Parameters
    public static Iterable<MySubsystemSchema> parameters() {
        return EnumSet.allOf(MySubsystemSchema.class);
    }

    public MySubsystemTestCase(MySubsystemSchema schema) {
        super(MySubsystemResourceDefinitionRegistrar.REGISTRATION.getName(),
              new MyExtension(), schema, MySubsystemSchema.CURRENT);
    }
}
```

Test XML files are named by schema version (e.g. `my-subsystem-1.0.xml`, `my-subsystem-2.0.xml`) and placed in `src/test/resources` under the test class's package path.

### Transformer test

Parameterized over target controller versions. Tests both successful transformation and rejection of incompatible configurations:

```java
@RunWith(Parameterized.class)
public class MySubsystemTransformerTestCase extends AbstractSubsystemTest {

    @Parameters
    public static Iterable<ModelTestControllerVersion> parameters() {
        return EnumSet.of(
            ModelTestControllerVersion.EAP_7_4_0,
            ModelTestControllerVersion.EAP_8_0_0);
    }

    private final ModelTestControllerVersion controller;
    private final ModelVersion version;

    public MySubsystemTransformerTestCase(ModelTestControllerVersion controller) {
        super(MySubsystemResourceDefinitionRegistrar.REGISTRATION.getName(), new MyExtension());
        this.controller = controller;
        this.version = this.getModelVersion().getVersion();
    }

    @Test
    public void testTransformation() throws Exception {
        String subsystemXmlResource = String.format("my-subsystem-transform-%d.%d.%d.xml",
            this.version.getMajor(), this.version.getMinor(), this.version.getMicro());

        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT)
                .setSubsystemXmlResource(subsystemXmlResource);
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, this.controller, this.version)
                .addMavenResourceURL(getDependencies())
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(services.isSuccessfulBoot());
        Assert.assertTrue(services.getLegacyServices(this.version).isSuccessfulBoot());
        checkSubsystemModelTransformation(services, this.version, null, false);
    }

    @Test
    public void testRejections() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT);
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, this.controller, this.version)
                .addMavenResourceURL(this.getDependencies())
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(services.isSuccessfulBoot());

        List<ModelNode> operations = builder.parseXmlResource(
            String.format("my-subsystem-transform-reject-%d.%d.%d.xml",
                this.version.getMajor(), this.version.getMinor(), this.version.getMicro()));
        ModelTestUtils.checkFailedTransformedBootOperations(services, this.version, operations,
            this.createFailedOperationTransformationConfig());
    }
}
```

Test XML files for transformer tests:
- `my-subsystem-transform-X.Y.Z.xml` -- named by **model version**, contains only the attributes valid for the target version
- `my-subsystem-transform-reject-X.Y.Z.xml` -- contains attributes that should be rejected

## Checklist: adding a new attribute

1. Define the `SimpleAttributeDefinition` (or other attribute type) in the resource definition class.
2. Add it to the resource's attribute collection.
3. Add the description to `LocalDescriptions.properties`.
4. If the attribute appears in XML:
   - Gate it with `this.since(NEW_SCHEMA_VERSION)` in the schema enum (or add to the latest parser class for legacy).
   - Create a new XSD file if this is a new schema version.
5. If this is the first subsystem change since the last Final release:
   - Bump the model version.
   - Bump the schema version (if XML changed).
6. Add transformer logic to discard/reject the attribute for older model versions.
7. Update the subsystem parsing test XML for the new schema version.
8. Update transformer test XMLs (transform and reject variants) for the new model version.
9. Run `mvn clean install` on the subsystem module to verify everything passes.

## Checklist: adding a new child resource

1. Create a new class extending `PersistentResourceDefinition`.
2. Define its `PathElement`, attributes, and capabilities.
3. Register it in the parent resource's `register()` or `registerChildren()` method.
4. Add descriptions to `LocalDescriptions.properties`.
5. Add the XML element to the schema, gated behind the new version.
6. Create the XSD element in the new schema version's XSD file.
7. Handle versioning, transformers, and testing as above.
   - For a new resource that didn't exist in older versions, use `rejectChildResource(path)` in the transformer.
