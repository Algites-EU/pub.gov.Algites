package eu.algites.pltf.modustro.builder.subscription.adapters;

import eu.algites.pltf.modustro.builder.subscription.AIiSubscriptionAdapter;
import java.util.Objects;

/** Bootstrap subscription-adapter identity until adapters are discovered from external plugins. */
public final class AIcBuiltinSubscriptionAdapter implements AIiSubscriptionAdapter {
    private final String adapterId;
    public AIcBuiltinSubscriptionAdapter(String aAdapterId) {
        adapterId = Objects.requireNonNull(aAdapterId, "adapterId");
        if (adapterId.isBlank()) throw new IllegalArgumentException("Subscription adapter id must not be blank.");
    }
    @Override public String adapterId() { return adapterId; }
}
