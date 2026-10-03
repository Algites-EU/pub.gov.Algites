package eu.algites.pltf.modustro.builder.model.publication;

/** Built-in Modustro Builder publishing output classes. */
public enum AInPublishingOutputKind {
    NATIVE_BINARY_OUTPUT("native_binary_output"),
    NATIVE_SOURCE_OUTPUT("native_source_output"),
    NATIVE_DOCUMENTATION_OUTPUT("native_documentation_output"),
    MODUSTRO_DOCS_SITE("modustro_docs_site"),
    SCHEMA_SITE("schema_site");

    private final String descriptorName;

    AInPublishingOutputKind(String aDescriptorName) {
        descriptorName = aDescriptorName;
    }

    /** Returns the canonical descriptor property name. */
    public String descriptorName() {
        return descriptorName;
    }
}
