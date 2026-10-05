package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests built-in build-output to publishing-output classification. */
public class AItcBuiltinPublicationOutputKindMapperTest {

    /** Verifies all currently supported built-in production primitives. */
    @Test
    public void testPublicationOutputKindMapping() {
        Assert.assertEquals(
            AIcBuiltinPublicationOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_CLASSES_JAR),
            AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES
        );
        Assert.assertEquals(
            AIcBuiltinPublicationOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_SOURCES_JAR),
            AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES
        );
        Assert.assertEquals(
            AIcBuiltinPublicationOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_JAVADOC_JAR),
            AInPublicationOutputKind.NATIVE_PRODUCT_DOCUMENTATION
        );
        Assert.assertEquals(
            AIcBuiltinPublicationOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.PYTHON_WHEEL),
            AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES
        );
        Assert.assertEquals(
            AIcBuiltinPublicationOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.PYTHON_SDIST),
            AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES
        );
    }
}
