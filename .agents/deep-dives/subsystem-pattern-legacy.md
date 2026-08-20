# Legacy Pattern (Per-Version Parser Classes)

Reference: Deployment Scanner (`wildfly-core/deployment-scanner/`), Logging (`wildfly-core/logging/`)

## Extension

Implements `Extension` directly. Each XML version is mapped to its own parser class:

```java
public class MyExtension implements Extension {
    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(2, 0, 0);

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);
        subsystem.registerXMLElementWriter(MyParser_2_0::new);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new MySubsystemDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        registration.registerSubModel(new SomeChildDefinition());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MY_SUBSYSTEM_1_0.getUriString(), MyParser_1_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MY_SUBSYSTEM_1_1.getUriString(), MyParser_1_1::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.MY_SUBSYSTEM_2_0.getUriString(), MyParser_2_0::new);
    }
}
```

Register in `META-INF/services/org.jboss.as.controller.Extension`.

## Model version

Raw `int` constants and `ModelVersion.create()` instead of a `SubsystemModel` enum:

```java
private static final int MANAGEMENT_API_MAJOR_VERSION = 2;
private static final int MANAGEMENT_API_MINOR_VERSION = 0;
private static final int MANAGEMENT_API_MICRO_VERSION = 0;
private static final ModelVersion CURRENT_VERSION = ModelVersion.create(
    MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);
```

## Root resource definition

Same as the intermediate pattern -- extends `SimpleResourceDefinition` with explicit `registerAttributes()`/`registerChildren()` overrides.

## Namespace enum

A custom enum mapping URI strings to versions, replacing the schema enum of newer patterns:

```java
public enum Namespace {
    UNKNOWN(null),
    MY_SUBSYSTEM_1_0("urn:jboss:domain:my-subsystem:1.0"),
    MY_SUBSYSTEM_1_1("urn:jboss:domain:my-subsystem:1.1"),
    MY_SUBSYSTEM_2_0("urn:jboss:domain:my-subsystem:2.0");

    public static final Namespace CURRENT = MY_SUBSYSTEM_2_0;

    private final String uri;

    Namespace(String uri) { this.uri = uri; }

    public String getUriString() { return this.uri; }
}
```

## XML parsing -- per-version parser classes

Each schema version has its own parser class implementing `XMLElementReader` and `XMLElementWriter` with manual StAX code:

```java
class MyParser_2_0 implements XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        // Create the subsystem ADD operation
        ModelNode subsystemAdd = Util.createAddOperation(PathAddress.pathAddress(SUBSYSTEM, SUBSYSTEM_NAME));
        list.add(subsystemAdd);

        // Parse child elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            switch (reader.getLocalName()) {
                case "child":
                    parseChild(reader, list);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void parseChild(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        ModelNode addOp = Util.createAddOperation(...);

        // Each attribute handled manually with switch/case
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String value = reader.getAttributeValue(i);
            switch (reader.getAttributeLocalName(i)) {
                case "my-attr":
                    SomeChildDefinition.MY_ATTR.parseAndSetParameter(value, addOp, reader);
                    break;
                case "other-attr":
                    SomeChildDefinition.OTHER_ATTR.parseAndSetParameter(value, addOp, reader);
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);
        list.add(addOp);
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(Namespace.CURRENT.getUriString(), false);
        ModelNode model = context.getModelNode();

        // Marshal each child element manually
        if (model.hasDefined("child")) {
            for (Property child : model.get("child").asPropertyList()) {
                writer.writeStartElement("child");
                writer.writeAttribute("name", child.getName());
                SomeChildDefinition.MY_ATTR.marshallAsAttribute(child.getValue(), writer);
                SomeChildDefinition.OTHER_ATTR.marshallAsAttribute(child.getValue(), writer);
                writer.writeEndElement();
            }
        }
        writer.writeEndElement();
    }
}
```

When adding a new attribute: add a new `case` in `readElement`/`parseChild` and a new `marshallAsAttribute` call in `writeContent` of the latest parser class.

When adding a new schema version: create a new parser class (often extending the previous one and overriding specific methods), add the new namespace to the `Namespace` enum, and register it in `initializeParsers()`.

## Transformer registration -- chained transformers

For subsystems with many model versions (like logging), chained transformers compose step-by-step to avoid redundancy:

```java
public static class TransformerRegistration implements ExtensionTransformerRegistration {
    @Override
    public String getSubsystemName() { return SUBSYSTEM_NAME; }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder =
            TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(CURRENT_VERSION);

        // Each step transforms from one version to the next older
        registerTransformers(chainedBuilder, CURRENT_VERSION, VERSION_3_0_0, defs);
        registerTransformers(chainedBuilder, VERSION_3_0_0, VERSION_2_0_0, defs);
        registerTransformers(chainedBuilder, VERSION_2_0_0, VERSION_1_0_0, defs);

        chainedBuilder.buildAndRegister(registration,
            new ModelVersion[] { /* versions needing direct transforms */ },
            new ModelVersion[] { /* all target versions */ });
    }
}
```

Each resource definition can provide its own `TransformerDefinition` inner class that contributes to the appropriate step. See `LoggingExtension.TransformerRegistration` for the full example.

For simpler legacy subsystems (like deployment-scanner) that have few model versions, the transformer may be a flat `ExtensionTransformerRegistration` with version checks, similar to the intermediate pattern.
