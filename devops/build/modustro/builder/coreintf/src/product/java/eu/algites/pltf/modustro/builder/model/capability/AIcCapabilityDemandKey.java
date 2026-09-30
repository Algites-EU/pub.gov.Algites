package eu.algites.pltf.modustro.builder.model.capability;

import eu.algites.pltf.modustro.builder.model.AInModelScope;
import java.util.Objects;

/**
 * Stable identity of one capability demand in a concrete model scope.
 */
public final class AIcCapabilityDemandKey {

    private final String technologyKind;
    private final String capabilityId;
    private final AInModelScope scope;
    private final String scopeIdentity;

    public AIcCapabilityDemandKey(
        String aTechnologyKind,
        String aCapabilityId,
        AInModelScope aScope,
        String aScopeIdentity
    ) {
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        capabilityId = requireText(aCapabilityId, "capabilityId");
        scope = Objects.requireNonNull(aScope, "scope");
        scopeIdentity = requireText(aScopeIdentity, "scopeIdentity");
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

    public String capabilityId() {
        return capabilityId;
    }

    public AInModelScope scope() {
        return scope;
    }

    public String scopeIdentity() {
        return scopeIdentity;
    }

    @Override
    public boolean equals(Object aOther) {
        if (this == aOther) {
            return true;
        }
        if (!(aOther instanceof AIcCapabilityDemandKey locOther)) {
            return false;
        }
        return technologyKind.equals(locOther.technologyKind)
            && capabilityId.equals(locOther.capabilityId)
            && scope == locOther.scope
            && scopeIdentity.equals(locOther.scopeIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(technologyKind, capabilityId, scope, scopeIdentity);
    }

    @Override
    public String toString() {
        return technologyKind + ":" + capabilityId + "@" + scope.name().toLowerCase() + ":" + scopeIdentity;
    }
}
