package eu.algites.pltf.modustro.builder.model.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Technology-neutral description of source and resource roots prepared for downstream build-output producers.
 */
public final class AIcPreparedSourceSet {

    private final String technologyKind;
    private final List<String> nativeSourceRoots;
    private final List<String> generatedSourceRoots;
    private final List<String> resourceRoots;

    public AIcPreparedSourceSet(
        String aTechnologyKind,
        List<String> aNativeSourceRoots,
        List<String> aGeneratedSourceRoots,
        List<String> aResourceRoots
    ) {
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        nativeSourceRoots = normalizedRoots(aNativeSourceRoots, "nativeSourceRoots");
        generatedSourceRoots = normalizedRoots(aGeneratedSourceRoots, "generatedSourceRoots");
        resourceRoots = normalizedRoots(aResourceRoots, "resourceRoots");
    }

    private static List<String> normalizedRoots(List<String> aRoots, String aName) {
        Objects.requireNonNull(aRoots, aName);
        List<String> locResult = new ArrayList<>();
        for (String locRoot : aRoots) {
            String locNormalized = requireText(locRoot, aName + " item").replace('\\', '/');
            if (!locResult.contains(locNormalized)) {
                locResult.add(locNormalized);
            }
        }
        return List.copyOf(locResult);
    }

    private static String requireText(String aValue, String aName) {
        Objects.requireNonNull(aValue, aName);
        String locValue = aValue.trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    public String technologyKind() {
        return technologyKind;
    }

    public List<String> nativeSourceRoots() {
        return nativeSourceRoots;
    }

    public List<String> generatedSourceRoots() {
        return generatedSourceRoots;
    }

    public List<String> resourceRoots() {
        return resourceRoots;
    }

    public List<String> allSourceRoots() {
        List<String> locResult = new ArrayList<>(nativeSourceRoots);
        generatedSourceRoots.forEach(locRoot -> {
            if (!locResult.contains(locRoot)) {
                locResult.add(locRoot);
            }
        });
        return List.copyOf(locResult);
    }
}
