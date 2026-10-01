package eu.algites.pltf.modustro.builder.output;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionInput;
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests the hard-wired Phase-3 build-output producer registry.
 */
public final class AItcBuiltinBuildOutputProducersTest {

    /**
     * Creates a test instance.
     */
    public AItcBuiltinBuildOutputProducersTest() {
    }

    /**
     * Verifies that java defaults produce classes and sources.
     */
    @Test
    public void javaDefaultsProduceClassesAndSources() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet(
            "java",
            List.of("src/product/java"),
            List.of("src/product/java.gen"),
            List.of("src/product/resources")
        );
        var locPlans = new AIcBuiltinBuildOutputProducers().createDefaultProductionPlans("java", locPrepared);
        Assert.assertEquals(locPlans.size(), 2);
        Assert.assertTrue(locPlans.stream().anyMatch(locPlan -> locPlan.productionKind() == AInBuildOutputProductionKind.JAVA_CLASSES_JAR));
        Assert.assertTrue(locPlans.stream().anyMatch(locPlan -> locPlan.productionKind() == AInBuildOutputProductionKind.JAVA_SOURCES_JAR));
        Assert.assertTrue(locPlans.stream().allMatch(locPlan -> locPlan.preparedSourceSet() == locPrepared));
        Assert.assertTrue(locPlans.stream().anyMatch(locPlan -> locPlan.requiredCapabilityIds().contains("dependency_resolution")));
    }

    /**
     * Verifies that java javadoc uses documentation classpath.
     */
    @Test
    public void javaJavadocUsesDocumentationClasspath() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet("java", List.of("src/product/java"), List.of(), List.of());
        var locPlan = new AIcBuiltinBuildOutputProducers()
            .require("java", "java_javadoc_jar")
            .createProductionPlan(locPrepared);
        Assert.assertEquals(locPlan.productionKind(), AInBuildOutputProductionKind.JAVA_JAVADOC_JAR);
        Assert.assertEquals(locPlan.requiredInputs(), Set.of(AInBuildOutputProductionInput.DOCUMENTATION_CLASSPATH));
        Assert.assertEquals(locPlan.requiredCapabilityIds(), Set.of("generation_of_native_documentation"));
    }

    /**
     * Verifies that python defaults produce wheel and sdist.
     */
    @Test
    public void pythonDefaultsProduceWheelAndSdist() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet(
            "python",
            List.of("src/product/python"),
            List.of("src/product/python.gen"),
            List.of("src/product/jsondefs")
        );
        var locPlans = new AIcBuiltinBuildOutputProducers().createDefaultProductionPlans("python", locPrepared);
        Assert.assertEquals(locPlans.size(), 2);
        Assert.assertTrue(locPlans.stream().anyMatch(locPlan -> locPlan.productionKind() == AInBuildOutputProductionKind.PYTHON_WHEEL));
        Assert.assertTrue(locPlans.stream().anyMatch(locPlan -> locPlan.productionKind() == AInBuildOutputProductionKind.PYTHON_SDIST));
    }

    /**
     * Verifies that virtual python distribution has no direct producer.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void virtualPythonDistributionHasNoDirectProducer() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet("python", List.of("src/product/python"), List.of(), List.of());
        new AIcBuiltinBuildOutputProducers().createProductionPlans("python", Set.of("python_distribution"), locPrepared);
    }
}
