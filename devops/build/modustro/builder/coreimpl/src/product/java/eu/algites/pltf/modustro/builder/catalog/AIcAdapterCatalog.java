package eu.algites.pltf.modustro.builder.catalog;

import eu.algites.pltf.modustro.builder.publication.*;
import eu.algites.pltf.modustro.builder.subscription.AIiSubscriptionAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Immutable bootstrap catalog for all Modustro adapter extension points. */
public final class AIcAdapterCatalog {
    private final Map<String, AIiSubscriptionAdapter> subscriptionAdapters;
    private final Map<String, AIiPublicationAdapter> publicationAdapters;
    private final Map<String, AIiPublicationFinalizationActionAdapter> publicationFinalizationActionAdapters;
    private final Map<String, AIiOutputPublicationFinalizationActionAdapter> outputPublicationFinalizationActionAdapters;
    private final Map<String, AIiArtifactPublicationFinalizationActionAdapter> artifactPublicationFinalizationActionAdapters;
    private final Map<String, AIiVersionScopePublicationFinalizationActionAdapter> versionScopePublicationFinalizationActionAdapters;

    public AIcAdapterCatalog(
            List<? extends AIiSubscriptionAdapter> aSubscriptionAdapters,
            List<? extends AIiPublicationAdapter> aPublicationAdapters,
            List<? extends AIiPublicationFinalizationActionAdapter> aPublicationFinalizationActionAdapters,
            List<? extends AIiOutputPublicationFinalizationActionAdapter> aOutputPublicationFinalizationActionAdapters,
            List<? extends AIiArtifactPublicationFinalizationActionAdapter> aArtifactPublicationFinalizationActionAdapters,
            List<? extends AIiVersionScopePublicationFinalizationActionAdapter> aVersionScopePublicationFinalizationActionAdapters) {
        subscriptionAdapters = AIcIndex(aSubscriptionAdapters, AIiSubscriptionAdapter::adapterId, "subscription");
        publicationAdapters = AIcIndex(aPublicationAdapters, AIiPublicationAdapter::adapterId, "publication");
        publicationFinalizationActionAdapters = AIcIndex(aPublicationFinalizationActionAdapters, AIiPublicationFinalizationActionAdapter::adapterId, "publication-finalization");
        outputPublicationFinalizationActionAdapters = AIcIndex(aOutputPublicationFinalizationActionAdapters, AIiOutputPublicationFinalizationActionAdapter::adapterId, "output-publication-finalization");
        artifactPublicationFinalizationActionAdapters = AIcIndex(aArtifactPublicationFinalizationActionAdapters, AIiArtifactPublicationFinalizationActionAdapter::adapterId, "artifact-publication-finalization");
        versionScopePublicationFinalizationActionAdapters = AIcIndex(aVersionScopePublicationFinalizationActionAdapters, AIiVersionScopePublicationFinalizationActionAdapter::adapterId, "version-scope-publication-finalization");
    }

    public Map<String, AIiSubscriptionAdapter> subscriptionAdapters() { return subscriptionAdapters; }
    public Map<String, AIiPublicationAdapter> publicationAdapters() { return publicationAdapters; }
    public Map<String, AIiPublicationFinalizationActionAdapter> publicationFinalizationActionAdapters() { return publicationFinalizationActionAdapters; }
    public Map<String, AIiOutputPublicationFinalizationActionAdapter> outputPublicationFinalizationActionAdapters() { return outputPublicationFinalizationActionAdapters; }
    public Map<String, AIiArtifactPublicationFinalizationActionAdapter> artifactPublicationFinalizationActionAdapters() { return artifactPublicationFinalizationActionAdapters; }
    public Map<String, AIiVersionScopePublicationFinalizationActionAdapter> versionScopePublicationFinalizationActionAdapters() { return versionScopePublicationFinalizationActionAdapters; }

    public AIiSubscriptionAdapter requireSubscriptionAdapter(String aId) { return AIcRequire(subscriptionAdapters, aId, "SubscriptionAdapter"); }
    public AIiPublicationAdapter requirePublicationAdapter(String aId) { return AIcRequire(publicationAdapters, aId, "PublicationAdapter"); }
    public AIiPublicationFinalizationActionAdapter requirePublicationFinalizationActionAdapter(String aId) { return AIcRequire(publicationFinalizationActionAdapters, aId, "PublicationFinalizationActionAdapter"); }
    public AIiOutputPublicationFinalizationActionAdapter requireOutputPublicationFinalizationActionAdapter(String aId) { return AIcRequire(outputPublicationFinalizationActionAdapters, aId, "OutputPublicationFinalizationActionAdapter"); }
    public AIiArtifactPublicationFinalizationActionAdapter requireArtifactPublicationFinalizationActionAdapter(String aId) { return AIcRequire(artifactPublicationFinalizationActionAdapters, aId, "ArtifactPublicationFinalizationActionAdapter"); }
    public AIiVersionScopePublicationFinalizationActionAdapter requireVersionScopePublicationFinalizationActionAdapter(String aId) { return AIcRequire(versionScopePublicationFinalizationActionAdapters, aId, "VersionScopePublicationFinalizationActionAdapter"); }

    private static <T> Map<String, T> AIcIndex(List<? extends T> aAdapters, Function<T, String> aId, String aKind) {
        LinkedHashMap<String, T> locResult = new LinkedHashMap<>();
        for (T locAdapter : Objects.requireNonNull(aAdapters, aKind + "Adapters")) {
            String locId = Objects.requireNonNull(aId.apply(locAdapter), "adapterId");
            if (locId.isBlank()) throw new IllegalArgumentException("Blank " + aKind + " adapter id.");
            if (locResult.putIfAbsent(locId, locAdapter) != null) throw new IllegalArgumentException("Duplicate " + aKind + " adapter id '" + locId + "'.");
        }
        return Map.copyOf(locResult);
    }

    private static <T> T AIcRequire(Map<String, T> aMap, String aId, String aProperty) {
        T locAdapter = aMap.get(aId);
        if (locAdapter == null) throw new IllegalArgumentException("Unknown " + aProperty + " '" + aId + "'.");
        return locAdapter;
    }
}
