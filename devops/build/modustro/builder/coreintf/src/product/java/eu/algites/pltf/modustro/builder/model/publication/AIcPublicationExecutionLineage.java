package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered root-to-parent execution lineage visible to one post-publication action. */
public record AIcPublicationExecutionLineage(List<AIcPublicationExecutionStep> steps) {
    public AIcPublicationExecutionLineage {
        steps = List.copyOf(steps == null ? List.of() : steps);
    }
    public AIcPublicationExecutionStep root() {
        if (steps.isEmpty()) throw new IllegalStateException("Execution lineage is empty.");
        return steps.get(0);
    }
    public AIcPublicationExecutionStep parent() {
        if (steps.isEmpty()) throw new IllegalStateException("Execution lineage is empty.");
        return steps.get(steps.size() - 1);
    }
    public Optional<AIcPublicationExecutionStep> byId(String aId) {
        Objects.requireNonNull(aId, "id");
        return steps.stream().filter(locStep -> locStep.id().equals(aId)).reduce((a, b) -> b);
    }
    public AIcPublicationExecutionLineage append(AIcPublicationExecutionStep aStep) {
        java.util.ArrayList<AIcPublicationExecutionStep> locSteps = new java.util.ArrayList<>(steps);
        locSteps.add(Objects.requireNonNull(aStep, "step"));
        return new AIcPublicationExecutionLineage(locSteps);
    }
}
