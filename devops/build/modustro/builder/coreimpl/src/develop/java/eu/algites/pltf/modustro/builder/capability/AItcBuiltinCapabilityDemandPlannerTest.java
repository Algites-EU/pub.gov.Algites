package eu.algites.pltf.modustro.builder.capability;

import eu.algites.pltf.modustro.builder.model.AInModelScope;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemand;
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandKey;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import eu.algites.pltf.modustro.builder.output.AIcBuiltinBuildOutputProducers;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests Phase-4 capability demand planning and deduplication.
 */
public final class AItcBuiltinCapabilityDemandPlannerTest {

    /**
     * Creates a test instance.
     */
    public AItcBuiltinCapabilityDemandPlannerTest() {
    }

    /**
     * Verifies that java classes and sources deduplicate source processing demand.
     */
    @Test
    public void javaClassesAndSourcesDeduplicateSourceProcessingDemand() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet(
            "java",
            List.of("src/product/java"),
            List.of("src/product/java.gen"),
            List.of("src/product/resources")
        );
        var locPlans = new AIcBuiltinBuildOutputProducers().createProductionPlans(
            "java",
            Set.of("java_classes_jar", "java_sources_jar"),
            locPrepared
        );
        var locGraph = new AIcBuiltinCapabilityDemandPlanner().createDemandGraph(
            "devops/build/example",
            locPlans,
            List.of()
        );

        Assert.assertEquals(locGraph.demands().size(), 2);
        Assert.assertEquals(
            locGraph.demands().stream().filter(locDemand -> locDemand.key().capabilityId().equals("source_native_processing")).count(),
            1L
        );
        Assert.assertEquals(
            locGraph.demands().stream().filter(locDemand -> locDemand.key().capabilityId().equals("dependency_resolution")).count(),
            1L
        );
    }

    /**
     * Verifies that native documentation expands source and dependency prerequisites.
     */
    @Test
    public void nativeDocumentationExpandsSourceAndDependencyPrerequisites() {
        AIcPreparedSourceSet locPrepared = new AIcPreparedSourceSet("java", List.of("src/product/java"), List.of(), List.of());
        var locPlan = new AIcBuiltinBuildOutputProducers()
            .require("java", "java_javadoc_jar")
            .createProductionPlan(locPrepared);
        var locGraph = new AIcBuiltinCapabilityDemandPlanner().createDemandGraph(
            "devops/build/example",
            List.of(locPlan),
            List.of()
        );

        Assert.assertEquals(locGraph.demands().size(), 3);
        Assert.assertEquals(locGraph.dependencies().size(), 2);
        Assert.assertEquals(locGraph.topologicalOrder().get(2).key().capabilityId(), "generation_of_native_documentation");
    }

    /**
     * Verifies that additional repository demand is retained.
     */
    @Test
    public void additionalRepositoryDemandIsRetained() {
        AIcCapabilityDemand locDemand = new AIcCapabilityDemand(
            new AIcCapabilityDemandKey(
                "modustro",
                "publication_of_docs_site",
                AInModelScope.REPOSITORY,
                "pub.gov.Algites"
            ),
            Set.of("task:generateAlgitesDocsSite")
        );
        var locGraph = new AIcBuiltinCapabilityDemandPlanner().createDemandGraph(
            "devops/build/example",
            List.of(),
            List.of(locDemand)
        );
        Assert.assertEquals(locGraph.demands().size(), 1);
        Assert.assertEquals(locGraph.demands().get(0).key(), locDemand.key());
    }

    /**
     * Verifies that repository only publication capability rejects artifact scope.
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void repositoryOnlyPublicationCapabilityRejectsArtifactScope() {
        AIcCapabilityDemand locDemand = new AIcCapabilityDemand(
            new AIcCapabilityDemandKey(
                "modustro",
                "publication_of_docs_site",
                AInModelScope.ARTIFACT,
                "devops/build/example"
            ),
            Set.of("invalid")
        );
        new AIcBuiltinCapabilityDemandPlanner().createDemandGraph(
            "devops/build/example",
            List.of(),
            List.of(locDemand)
        );
    }
}
