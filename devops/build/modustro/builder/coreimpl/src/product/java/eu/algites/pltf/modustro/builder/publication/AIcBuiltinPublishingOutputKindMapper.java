package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingOutputKind;
import java.util.Objects;

/** Maps concrete native build-output production primitives to Modustro Builder publishing output kinds. */
public final class AIcBuiltinPublishingOutputKindMapper {

    private AIcBuiltinPublishingOutputKindMapper() {
    }

    /**
     * Resolves the publishing output kind for a built-in native production primitive.
     *
     * @param aProductionKind native production primitive
     * @return publishing output kind
     */
    public static AInPublishingOutputKind publishingOutputKind(AInBuildOutputProductionKind aProductionKind) {
        Objects.requireNonNull(aProductionKind, "productionKind");
        return switch (aProductionKind) {
            case JAVA_CLASSES_JAR, PYTHON_WHEEL -> AInPublishingOutputKind.NATIVE_BINARY_OUTPUT;
            case JAVA_SOURCES_JAR, PYTHON_SDIST -> AInPublishingOutputKind.NATIVE_SOURCE_OUTPUT;
            case JAVA_JAVADOC_JAR -> AInPublishingOutputKind.NATIVE_DOCUMENTATION_OUTPUT;
        };
    }
}
