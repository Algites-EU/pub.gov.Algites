package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingOutputKind;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Tests built-in build-output to publishing-output classification. */
public class AItcBuiltinPublishingOutputKindMapperTest {

    /** Verifies all currently supported built-in production primitives. */
    @Test
    public void testPublishingOutputKindMapping() {
        Assert.assertEquals(
            AIcBuiltinPublishingOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_CLASSES_JAR),
            AInPublishingOutputKind.NATIVE_PRODUCT_BINARIES
        );
        Assert.assertEquals(
            AIcBuiltinPublishingOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_SOURCES_JAR),
            AInPublishingOutputKind.NATIVE_PRODUCT_SOURCES
        );
        Assert.assertEquals(
            AIcBuiltinPublishingOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.JAVA_JAVADOC_JAR),
            AInPublishingOutputKind.NATIVE_PRODUCT_DOCUMENTATION
        );
        Assert.assertEquals(
            AIcBuiltinPublishingOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.PYTHON_WHEEL),
            AInPublishingOutputKind.NATIVE_PRODUCT_BINARIES
        );
        Assert.assertEquals(
            AIcBuiltinPublishingOutputKindMapper.publishingOutputKind(AInBuildOutputProductionKind.PYTHON_SDIST),
            AInPublishingOutputKind.NATIVE_PRODUCT_SOURCES
        );
    }
}
