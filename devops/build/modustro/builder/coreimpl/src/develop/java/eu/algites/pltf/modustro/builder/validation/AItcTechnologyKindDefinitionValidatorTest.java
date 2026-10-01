package eu.algites.pltf.modustro.builder.validation;

import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinTechnologyKindDefinitions;
import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests TechnologyKind and BuildOutputType definition invariants.
 */
public final class AItcTechnologyKindDefinitionValidatorTest {

    /**
     * Creates a test instance.
     */
    public AItcTechnologyKindDefinitionValidatorTest() {
    }

    /**
     * Verifies that built in definitions are valid.
     */
    @Test
    public void builtInDefinitionsAreValid() {
        AIcTechnologyKindDefinitionValidator locValidator = new AIcTechnologyKindDefinitionValidator();
        locValidator.validate(AIcBuiltinTechnologyKindDefinitions.javaDefinition());
        locValidator.validate(AIcBuiltinTechnologyKindDefinitions.pythonDefinition());
        locValidator.validate(AIcBuiltinTechnologyKindDefinitions.modustroDefinition());
    }

    /**
     * Verifies that python distribution is virtual consumable output.
     */
    @Test
    public void pythonDistributionIsVirtualConsumableOutput() {
        AIcTechnologyKindDefinition locPython = AIcBuiltinTechnologyKindDefinitions.pythonDefinition();
        AIcBuildOutputTypeDefinition locDistribution = locPython.buildOutputTypes().stream()
            .filter(locOutput -> locOutput.buildOutputType().equals("python_distribution"))
            .findFirst()
            .orElseThrow();
        Assert.assertFalse(locDistribution.canBeProduced());
        Assert.assertTrue(locDistribution.canBeUsedInDependency());
        Assert.assertEquals(locDistribution.dependencyOutputAlternatives(), Set.of("python_wheel", "python_sdist"));
    }

    /**
     * Verifies that non producible dependency output requires alternatives.
     */
    @Test(expectedExceptions = AIxModelValidationException.class)
    public void nonProducibleDependencyOutputRequiresAlternatives() {
        AIcTechnologyKindDefinition locDefinition = new AIcTechnologyKindDefinition(
            "invalid",
            List.of(),
            List.of(new AIcBuildOutputTypeDefinition("virtual", false, true, Set.of(), null)),
            Set.of(),
            Set.of("virtual")
        );
        new AIcTechnologyKindDefinitionValidator().validate(locDefinition);
    }
}
