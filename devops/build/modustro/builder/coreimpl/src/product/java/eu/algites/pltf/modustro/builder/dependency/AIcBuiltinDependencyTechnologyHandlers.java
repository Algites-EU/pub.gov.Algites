package eu.algites.pltf.modustro.builder.dependency;

import eu.algites.pltf.modustro.builder.dependency.java.AIcJavaDependencyTechnologyHandler;
import eu.algites.pltf.modustro.builder.dependency.python.AIcPythonDependencyTechnologyHandler;
import eu.algites.pltf.modustro.builder.model.dependency.resolution.AIiDependencyTechnologyHandler;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in Phase-2 handlers keyed by DependencyKind x TechnologyKind.
 */
public final class AIcBuiltinDependencyTechnologyHandlers {

    private final Map<String, Map<String, AIiDependencyTechnologyHandler>> handlers;

    public AIcBuiltinDependencyTechnologyHandlers() {
        LinkedHashMap<String, Map<String, AIiDependencyTechnologyHandler>> locByDependencyKind = new LinkedHashMap<>();
        List.of(
            new AIcJavaDependencyTechnologyHandler("modustro"),
            new AIcJavaDependencyTechnologyHandler("java"),
            new AIcPythonDependencyTechnologyHandler("modustro"),
            new AIcPythonDependencyTechnologyHandler("python")
        ).forEach(locHandler -> {
            LinkedHashMap<String, AIiDependencyTechnologyHandler> locByTechnology = new LinkedHashMap<>(
                locByDependencyKind.getOrDefault(locHandler.dependencyKind(), Map.of())
            );
            if (locByTechnology.putIfAbsent(locHandler.technologyKind(), locHandler) != null) {
                throw new IllegalStateException(
                    "Duplicate dependency technology handler for '" + locHandler.dependencyKind() + "' x '" +
                        locHandler.technologyKind() + "'."
                );
            }
            locByDependencyKind.put(locHandler.dependencyKind(), Map.copyOf(locByTechnology));
        });
        LinkedHashMap<String, Map<String, AIiDependencyTechnologyHandler>> locImmutable = new LinkedHashMap<>();
        locByDependencyKind.forEach((locDependencyKind, locByTechnology) ->
            locImmutable.put(locDependencyKind, Map.copyOf(locByTechnology))
        );
        handlers = Map.copyOf(locImmutable);
    }

    public AIiDependencyTechnologyHandler require(String aDependencyKind, String aTechnologyKind) {
        AIiDependencyTechnologyHandler locHandler = handlers
            .getOrDefault(aDependencyKind, Map.of())
            .get(aTechnologyKind);
        if (locHandler == null) {
            throw new IllegalArgumentException(
                "No dependency technology handler is registered for DependencyKind '" + aDependencyKind +
                    "' and TechnologyKind '" + aTechnologyKind + "'."
            );
        }
        return locHandler;
    }
}
