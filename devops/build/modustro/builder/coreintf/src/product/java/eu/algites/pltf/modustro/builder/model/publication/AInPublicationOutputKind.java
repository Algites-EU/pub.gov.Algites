package eu.algites.pltf.modustro.builder.model.publication;

/** Built-in Modustro Builder publication output classes. */
public enum AInPublicationOutputKind {
    NATIVE_PRODUCT_BINARIES("native_product_binaries"),
    NATIVE_PRODUCT_SOURCES("native_product_sources"),
    NATIVE_PRODUCT_DOCUMENTATION("native_product_documentation"),
    NATIVE_DEVELOP_SOURCES("native_develop_sources"),
    NATIVE_DEVELOP_BINARIES("native_develop_binaries"),
    NATIVE_DEVELOP_DOCUMENTATION("native_develop_documentation"),
    MODUSTRO_DOCS_SITE("modustro_docs_site"),
    SCHEMA_SITE("schema_site");

    private final String descriptorName;

    AInPublicationOutputKind(String aDescriptorName) {
        descriptorName = aDescriptorName;
    }

    /** Returns the canonical descriptor property name. */
    public String descriptorName() {
        return descriptorName;
    }
}
