package eu.algites.pltf.modustro.builder.subscription;

import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription;
import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscriptionCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges hierarchical input-subscription declarations by TechnologyKind/InputSelector/Id. */
public final class AIcInputSubscriptionResolver {

    /** Merges a base list with one descendant declaration list. */
    public List<AIcInputSubscription> merge(List<AIcInputSubscription> aBase, List<AIcInputSubscription> aOverride) {
        LinkedHashMap<String, AIcInputSubscription> locMerged = new LinkedHashMap<>();
        for (AIcInputSubscription locSubscription : aBase) {
            locMerged.put(AIcIdentity(locSubscription), locSubscription);
        }
        for (AIcInputSubscription locOverride : aOverride) {
            String locIdentity = AIcIdentity(locOverride);
            AIcInputSubscription locBase = locMerged.get(locIdentity);
            locMerged.put(locIdentity, locBase == null ? locOverride : AIcMerge(locBase, locOverride));
        }
        return List.copyOf(locMerged.values());
    }

    /** Resolves a catalog after hierarchical merge. */
    public AIcInputSubscriptionCatalog resolve(List<AIcInputSubscription> aSubscriptions) {
        return new AIcInputSubscriptionCatalog(aSubscriptions);
    }

    private static AIcInputSubscription AIcMerge(AIcInputSubscription aBase, AIcInputSubscription aOverride) {
        return new AIcInputSubscription(
                aBase.technologyKind(),
                aBase.inputSelector(),
                aBase.id(),
                aOverride.enabled(),
                aOverride.visibility(),
                aOverride.stability() != null ? aOverride.stability() : aBase.stability(),
                aOverride.subscriptionUri() != null ? aOverride.subscriptionUri() : aBase.subscriptionUri(),
                aOverride.subscriptionAdapter() != null ? aOverride.subscriptionAdapter() : aBase.subscriptionAdapter(),
                aOverride.subscriptionCredentialProfile() != null
                        ? aOverride.subscriptionCredentialProfile()
                        : aBase.subscriptionCredentialProfile(),
                aOverride.subscriptionOrder(),
                AIcMergeConfiguration(aBase.configuration(), aOverride.configuration()));
    }

    private static Map<String, Object> AIcMergeConfiguration(Map<String, Object> aBase, Map<String, Object> aOverride) {
        LinkedHashMap<String, Object> locMerged = new LinkedHashMap<>(aBase);
        locMerged.putAll(aOverride);
        return locMerged;
    }

    private static String AIcIdentity(AIcInputSubscription aSubscription) {
        return aSubscription.technologyKind() + "|" + aSubscription.inputSelector() + "|" + aSubscription.id();
    }
}
