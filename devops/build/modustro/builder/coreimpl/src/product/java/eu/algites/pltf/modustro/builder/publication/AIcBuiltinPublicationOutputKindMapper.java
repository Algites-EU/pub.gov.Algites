package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import java.util.Objects;

/** Maps concrete native build-output production primitives to Modustro Builder publication output kinds. */
public final class AIcBuiltinPublicationOutputKindMapper {

    private AIcBuiltinPublicationOutputKindMapper() {
    }

    /**
     * Resolves the publication output kind for a built-in native production primitive.
     *
     * @param aProductionKind native production primitive
     * @return publication output kind
     */
    public static AInPublicationOutputKind publishingOutputKind(AInBuildOutputProductionKind aProductionKind) {
        Objects.requireNonNull(aProductionKind, "productionKind");
        return switch (aProductionKind) {
            case JAVA_CLASSES_JAR, PYTHON_WHEEL -> AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES;
            case JAVA_SOURCES_JAR, PYTHON_SDIST -> AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES;
            case JAVA_JAVADOC_JAR -> AInPublicationOutputKind.NATIVE_PRODUCT_DOCUMENTATION;
        };
    }
    /** Scope changes publication classification, never the technology's physical format. */
    public static AInPublicationOutputKind publishingOutputKind(AInBuildOutputProductionKind kind,String scope) {
        var product=publishingOutputKind(kind);
        if("product".equals(scope))return product;
        if(!"develop".equals(scope))throw new IllegalArgumentException("Unknown content scope '"+scope+"'.");
        return switch(product){
            case NATIVE_PRODUCT_BINARIES->AInPublicationOutputKind.NATIVE_DEVELOP_BINARIES;
            case NATIVE_PRODUCT_SOURCES->AInPublicationOutputKind.NATIVE_DEVELOP_SOURCES;
            case NATIVE_PRODUCT_DOCUMENTATION->AInPublicationOutputKind.NATIVE_DEVELOP_DOCUMENTATION;
            default->throw new IllegalArgumentException("Not a native production kind.");
        };
    }
}
