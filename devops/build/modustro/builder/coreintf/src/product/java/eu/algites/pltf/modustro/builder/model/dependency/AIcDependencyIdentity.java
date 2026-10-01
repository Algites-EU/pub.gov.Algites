package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.Objects;

/**
 * Identity key used to merge dependency and dependency-constraint declarations.
 */
public final class AIcDependencyIdentity {

    private final String dependencyKind;
    private final String groupId;
    private final String artifactId;
    private final String variantId;

    /**
     * Creates an {@code AIcDependencyIdentity} instance.
     *
     * @param aDependencyKind DependencyKind identifier
     * @param aGroupId optional dependency group identifier
     * @param aArtifactId dependency artifact identifier
     * @param aVariantId optional dependency variant identifier
     */
    public AIcDependencyIdentity(String aDependencyKind, String aGroupId, String aArtifactId, String aVariantId) {
        dependencyKind = requireText(aDependencyKind, "dependencyKind");
        groupId = normalize(aGroupId);
        artifactId = requireText(aArtifactId, "artifactId");
        variantId = normalize(aVariantId);
    }

    private static String requireText(String aValue, String aName) {
        String locValue = normalize(aValue);
        if (locValue == null) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    private static String normalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    /**
     * Returns the DependencyKind identifier handled by this object.
     * @return DependencyKind identifier
     */
    public String dependencyKind() {
        return dependencyKind;
    }

    /**
     * Returns the optional group identifier.
     * @return group identifier, or {@code null}
     */
    public String groupId() {
        return groupId;
    }

    /**
     * Returns the artifact identifier.
     * @return artifact identifier
     */
    public String artifactId() {
        return artifactId;
    }

    /**
     * Returns the optional dependency variant identifier.
     * @return variant identifier, or {@code null}
     */
    public String variantId() {
        return variantId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object aOther) {
        if (this == aOther) {
            return true;
        }
        if (!(aOther instanceof AIcDependencyIdentity locOther)) {
            return false;
        }
        return dependencyKind.equals(locOther.dependencyKind)
            && Objects.equals(groupId, locOther.groupId)
            && artifactId.equals(locOther.artifactId)
            && Objects.equals(variantId, locOther.variantId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(dependencyKind, groupId, artifactId, variantId);
    }
}
