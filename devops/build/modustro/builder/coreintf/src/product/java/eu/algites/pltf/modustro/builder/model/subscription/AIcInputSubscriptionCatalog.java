package eu.algites.pltf.modustro.builder.model.subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable catalog of effective input subscriptions. */
public final class AIcInputSubscriptionCatalog {

    private final List<AIcInputSubscription> subscriptions;
    private final Map<String, AIcInputSubscription> byIdentity;

    /** Creates an immutable catalog. */
    public AIcInputSubscriptionCatalog(Iterable<AIcInputSubscription> aSubscriptions) {
        Objects.requireNonNull(aSubscriptions, "subscriptions");
        ArrayList<AIcInputSubscription> locSubscriptions = new ArrayList<>();
        LinkedHashMap<String, AIcInputSubscription> locByIdentity = new LinkedHashMap<>();
        for (AIcInputSubscription locSubscription : aSubscriptions) {
            Objects.requireNonNull(locSubscription, "subscription");
            String locIdentity = AIcIdentity(locSubscription);
            if (locByIdentity.putIfAbsent(locIdentity, locSubscription) != null) {
                throw new IllegalArgumentException("Duplicate effective input subscription identity '" + locIdentity + "'.");
            }
            locSubscriptions.add(locSubscription);
        }
        subscriptions = List.copyOf(locSubscriptions);
        byIdentity = Map.copyOf(locByIdentity);
    }

    /** Returns all subscriptions in deterministic declaration order. */
    public List<AIcInputSubscription> all() {
        return subscriptions;
    }

    /** Resolves one subscription by full identity. */
    public Optional<AIcInputSubscription> byIdentity(String aTechnologyKind, String aInputSelector, String aId) {
        return Optional.ofNullable(byIdentity.get(aTechnologyKind + "|" + aInputSelector + "|" + aId));
    }

    /** Selects enabled subscriptions matching one effective input context. */
    public List<AIcInputSubscription> select(
            String aTechnologyKind,
            String aInputSelector,
            java.util.Set<String> aVisibilities,
            java.util.Set<String> aStabilities) {
        Objects.requireNonNull(aVisibilities, "visibilities");
        Objects.requireNonNull(aStabilities, "stabilities");
        return subscriptions.stream()
                .filter(AIcInputSubscription::enabled)
                .filter(locSubscription -> locSubscription.technologyKind().equals(aTechnologyKind))
                .filter(locSubscription -> locSubscription.inputSelector().equals(aInputSelector))
                .filter(locSubscription -> aVisibilities.isEmpty() || aVisibilities.contains(locSubscription.visibility()))
                .filter(locSubscription -> aStabilities.isEmpty()
                        || locSubscription.stability() == null
                        || aStabilities.contains(locSubscription.stability()))
                .sorted(Comparator.comparingInt(AIcInputSubscription::subscriptionOrder).thenComparing(AIcInputSubscription::id))
                .toList();
    }

    private static String AIcIdentity(AIcInputSubscription aSubscription) {
        return aSubscription.technologyKind() + "|" + aSubscription.inputSelector() + "|" + aSubscription.id();
    }
}
