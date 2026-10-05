package eu.algites.pltf.modustro.builder.model.subscription;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Immutable effective input-subscription definition. */
public record AIcInputSubscription(
        String technologyKind,
        String inputSelector,
        String id,
        boolean enabled,
        String visibility,
        String stability,
        URI subscriptionUri,
        String subscriptionAdapter,
        String subscriptionCredentialProfile,
        int subscriptionOrder,
        Map<String, Object> configuration) {

    /** Validates subscription invariants. */
    public AIcInputSubscription {
        technologyKind = AIcRequireText(technologyKind, "technologyKind");
        inputSelector = AIcRequireText(inputSelector, "inputSelector");
        id = AIcRequireText(id, "id");
        visibility = AIcRequireText(visibility, "visibility");
        stability = AIcNormalize(stability);
        subscriptionAdapter = AIcNormalize(subscriptionAdapter);
        subscriptionCredentialProfile = AIcNormalize(subscriptionCredentialProfile);
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        if (enabled && subscriptionUri == null) {
            throw new IllegalArgumentException("Enabled input subscription requires SubscriptionUri.");
        }
        if (enabled && subscriptionAdapter == null) {
            throw new IllegalArgumentException("Enabled input subscription requires SubscriptionAdapter.");
        }
        if (!visibility.equals("public") && !visibility.equals("private")) {
            throw new IllegalArgumentException("Input subscription visibility must be public or private.");
        }
        if (stability != null && !stability.equals("snapshot") && !stability.equals("release")) {
            throw new IllegalArgumentException("Input subscription stability must be snapshot or release when specified.");
        }
    }

    private static String AIcRequireText(String aValue, String aName) {
        String locValue = AIcNormalize(aValue);
        if (locValue == null) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    private static String AIcNormalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }
}
