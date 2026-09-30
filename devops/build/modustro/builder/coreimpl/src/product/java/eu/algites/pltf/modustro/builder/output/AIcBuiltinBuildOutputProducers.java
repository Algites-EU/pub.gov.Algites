package eu.algites.pltf.modustro.builder.output;

import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinTechnologyKindDefinitions;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import eu.algites.pltf.modustro.builder.model.output.AIiBuildOutputProducer;
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import eu.algites.pltf.modustro.builder.model.technology.AIcTechnologyKindDefinition;
import eu.algites.pltf.modustro.builder.output.java.AIcJavaClassesJarBuildOutputProducer;
import eu.algites.pltf.modustro.builder.output.java.AIcJavaJavadocJarBuildOutputProducer;
import eu.algites.pltf.modustro.builder.output.java.AIcJavaSourcesJarBuildOutputProducer;
import eu.algites.pltf.modustro.builder.output.python.AIcPythonSdistBuildOutputProducer;
import eu.algites.pltf.modustro.builder.output.python.AIcPythonWheelBuildOutputProducer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Hard-wired Phase-3 producer registry. Later plugin phases replace this registry with capability/provider discovery.
 */
public final class AIcBuiltinBuildOutputProducers {

    private final Map<String, AIiBuildOutputProducer> producers;
    private final Map<String, AIcTechnologyKindDefinition> technologyDefinitions;

    public AIcBuiltinBuildOutputProducers() {
        this(
            List.of(
                new AIcJavaClassesJarBuildOutputProducer(),
                new AIcJavaSourcesJarBuildOutputProducer(),
                new AIcJavaJavadocJarBuildOutputProducer(),
                new AIcPythonWheelBuildOutputProducer(),
                new AIcPythonSdistBuildOutputProducer()
            ),
            List.of(
                AIcBuiltinTechnologyKindDefinitions.javaDefinition(),
                AIcBuiltinTechnologyKindDefinitions.pythonDefinition(),
                AIcBuiltinTechnologyKindDefinitions.modustroDefinition()
            )
        );
    }

    public AIcBuiltinBuildOutputProducers(
        List<AIiBuildOutputProducer> aProducers,
        List<AIcTechnologyKindDefinition> aTechnologyDefinitions
    ) {
        Objects.requireNonNull(aProducers, "producers");
        Objects.requireNonNull(aTechnologyDefinitions, "technologyDefinitions");

        Map<String, AIiBuildOutputProducer> locProducers = new LinkedHashMap<>();
        for (AIiBuildOutputProducer locProducer : aProducers) {
            String locKey = key(locProducer.technologyKind(), locProducer.buildOutputType());
            if (locProducers.putIfAbsent(locKey, locProducer) != null) {
                throw new IllegalArgumentException("Duplicate build-output producer for '" + locKey + "'.");
            }
        }
        producers = Map.copyOf(locProducers);

        Map<String, AIcTechnologyKindDefinition> locTechnologyDefinitions = new LinkedHashMap<>();
        for (AIcTechnologyKindDefinition locDefinition : aTechnologyDefinitions) {
            if (locTechnologyDefinitions.putIfAbsent(locDefinition.technologyKind(), locDefinition) != null) {
                throw new IllegalArgumentException("Duplicate TechnologyKind definition for '" + locDefinition.technologyKind() + "'.");
            }
        }
        technologyDefinitions = Map.copyOf(locTechnologyDefinitions);
    }

    public AIiBuildOutputProducer require(String aTechnologyKind, String aBuildOutputType) {
        AIiBuildOutputProducer locProducer = producers.get(key(aTechnologyKind, aBuildOutputType));
        if (locProducer == null) {
            throw new IllegalArgumentException(
                "No built-in Phase-3 producer exists for TechnologyKind '" + aTechnologyKind + "' and BuildOutputType '" + aBuildOutputType + "'."
            );
        }
        return locProducer;
    }

    public Set<String> defaultBuildOutputTypes(String aTechnologyKind) {
        return technologyDefinition(aTechnologyKind).defaultBuildOutputTypes();
    }

    public List<AIcBuildOutputProductionPlan> createDefaultProductionPlans(
        String aTechnologyKind,
        AIcPreparedSourceSet aPreparedSourceSet
    ) {
        return createProductionPlans(aTechnologyKind, defaultBuildOutputTypes(aTechnologyKind), aPreparedSourceSet);
    }

    public List<AIcBuildOutputProductionPlan> createProductionPlans(
        String aTechnologyKind,
        Set<String> aBuildOutputTypes,
        AIcPreparedSourceSet aPreparedSourceSet
    ) {
        Objects.requireNonNull(aBuildOutputTypes, "buildOutputTypes");
        AIcTechnologyKindDefinition locTechnologyDefinition = technologyDefinition(aTechnologyKind);
        Map<String, AIcBuildOutputTypeDefinition> locOutputDefinitions = new LinkedHashMap<>();
        for (AIcBuildOutputTypeDefinition locDefinition : locTechnologyDefinition.buildOutputTypes()) {
            locOutputDefinitions.put(locDefinition.buildOutputType(), locDefinition);
        }

        List<AIcBuildOutputProductionPlan> locPlans = new ArrayList<>();
        for (String locBuildOutputType : new LinkedHashSet<>(aBuildOutputTypes)) {
            AIcBuildOutputTypeDefinition locDefinition = locOutputDefinitions.get(locBuildOutputType);
            if (locDefinition == null) {
                throw new IllegalArgumentException(
                    "TechnologyKind '" + aTechnologyKind + "' does not define BuildOutputType '" + locBuildOutputType + "'."
                );
            }
            if (!locDefinition.canBeProduced()) {
                throw new IllegalArgumentException(
                    "BuildOutputType '" + locBuildOutputType + "' of TechnologyKind '" + aTechnologyKind + "' is dependency-only and cannot be produced."
                );
            }
            locPlans.add(require(aTechnologyKind, locBuildOutputType).createProductionPlan(aPreparedSourceSet));
        }
        return List.copyOf(locPlans);
    }

    private AIcTechnologyKindDefinition technologyDefinition(String aTechnologyKind) {
        String locTechnologyKind = Objects.requireNonNull(aTechnologyKind, "technologyKind").trim();
        AIcTechnologyKindDefinition locDefinition = technologyDefinitions.get(locTechnologyKind);
        if (locDefinition == null) {
            throw new IllegalArgumentException("No built-in TechnologyKind definition exists for '" + locTechnologyKind + "'.");
        }
        return locDefinition;
    }

    private static String key(String aTechnologyKind, String aBuildOutputType) {
        return Objects.requireNonNull(aTechnologyKind, "technologyKind").trim() + "|" + Objects.requireNonNull(aBuildOutputType, "buildOutputType").trim();
    }
}
