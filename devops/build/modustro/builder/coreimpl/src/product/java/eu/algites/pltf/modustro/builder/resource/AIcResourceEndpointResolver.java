package eu.algites.pltf.modustro.builder.resource;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointAction_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceEndpointVisibility_1;
import eu.algites.pltf.modustro.builder.model.resource.AIngResourceStability_1;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves declared ResourceEndpoint DTOs into immutable effective Builder definitions.
 *
 * <p>Declarations are applied from lowest to highest precedence. Identity dimensions are immutable;
 * nullable endpoint properties are inherited from the preceding declaration with the same identity.
 * {@code Enabled} defaults to {@code true} only after inheritance has completed.</p>
 */
public final class AIcResourceEndpointResolver {

    private final AIcResourceEndpointValidator validator;

    /**
     * Creates a resolver with the supplied semantic validator.
     *
     * @param aValidator semantic validator applied to every effective endpoint
     */
    public AIcResourceEndpointResolver(AIcResourceEndpointValidator aValidator) {
        validator = Objects.requireNonNull(aValidator, "validator");
    }

    /**
     * Creates a resolver using the built-in ResourceKind definitions.
     *
     * @return built-in ResourceEndpoint resolver
     */
    public static AIcResourceEndpointResolver builtin() {
        return new AIcResourceEndpointResolver(AIcResourceEndpointValidator.builtin());
    }

    /**
     * Merge-composes two declaration layers without applying effective defaults.
     *
     * @param aBase lower-precedence declarations
     * @param aOverride higher-precedence declarations
     * @return immutable merged declaration list
     */
    public List<AIcgdResourceEndpoint_1> mergeDeclarations(
        Iterable<AIcgdResourceEndpoint_1> aBase,
        Iterable<AIcgdResourceEndpoint_1> aOverride
    ) {
        LinkedHashMap<String, AIcgdResourceEndpoint_1> locMerged = new LinkedHashMap<>();
        for (AIcgdResourceEndpoint_1 locDeclaration : aBase) {
            AIcPutDeclaration(locMerged, Objects.requireNonNull(locDeclaration, "base declaration"), false);
        }
        for (AIcgdResourceEndpoint_1 locDeclaration : aOverride) {
            AIcPutDeclaration(locMerged, Objects.requireNonNull(locDeclaration, "override declaration"), true);
        }
        return List.copyOf(locMerged.values());
    }

    /**
     * Resolves one precedence-ordered declaration sequence into effective endpoints.
     *
     * @param aDeclarations declarations ordered from lower to higher precedence
     * @return immutable effective endpoint catalog
     * @throws AIxModelValidationException when inheritance leaves an invalid effective endpoint
     */
    public AIcResourceEndpointCatalog resolve(Iterable<AIcgdResourceEndpoint_1> aDeclarations)
        throws AIxModelValidationException {
        List<AIcgdResourceEndpoint_1> locMerged = mergeDeclarations(List.of(), aDeclarations);
        ArrayList<AIcResourceEndpointDefinition> locEffective = new ArrayList<>();
        for (AIcgdResourceEndpoint_1 locDeclaration : locMerged) {
            if (locDeclaration.id().equals("algites-selector-default")) continue;
            AIcResourceEndpointDefinition locEndpoint = AIcEffective(locDeclaration);
            validator.validate(locEndpoint);
            locEffective.add(locEndpoint);
        }
        try {
            return new AIcResourceEndpointCatalog(locEffective);
        } catch (IllegalArgumentException locException) {
            throw new AIxModelValidationException(locException.getMessage());
        }
    }

    private static void AIcPutDeclaration(
        Map<String, AIcgdResourceEndpoint_1> aTarget,
        AIcgdResourceEndpoint_1 aDeclaration,
        boolean aMerge
    ) {
        aDeclaration = AIcNormalizeLegacyEndpointId(aDeclaration);
        String cell = aDeclaration.technologyKind()+"|"+aDeclaration.resourceKind()+"|"+aDeclaration.visibility()+"|"+aDeclaration.action();
        if(aDeclaration.id().equals("algites-selector-default")) {
            AIcgdResourceEndpoint_1 change=aDeclaration;
            aTarget.replaceAll((key,old)-> sameCell(old,change) ? withEnabled(old,change.enabled()) : old);
        } else if(aDeclaration.enabled()==null) {
            for(var old:aTarget.values())if(old.id().equals("algites-selector-default")&&sameCell(old,aDeclaration))aDeclaration=withEnabled(aDeclaration,old.enabled());
        }
        String locIdentity = AIcIdentity(aDeclaration);
        AIcgdResourceEndpoint_1 locPrevious = aTarget.get(locIdentity);
        aTarget.put(locIdentity, aMerge && locPrevious != null ? AIcMerge(locPrevious, aDeclaration) : aDeclaration);
    }

    private static boolean sameCell(AIcgdResourceEndpoint_1 a,AIcgdResourceEndpoint_1 b){return a.technologyKind().equals(b.technologyKind())&&a.resourceKind().equals(b.resourceKind())&&a.visibility()==b.visibility()&&a.action()==b.action();}
    private static AIcgdResourceEndpoint_1 withEnabled(AIcgdResourceEndpoint_1 a,Boolean enabled){return new AIcgdResourceEndpoint_1(a.technologyKind(),a.resourceKind(),a.visibility(),a.action(),a.id(),a.url(),a.credentialProfile(),enabled,a.stability(),a.resourceEndpointProviderAdapter());}

    /** Migrates the former upload/download naming convention before inheritance and validation. */
    private static AIcgdResourceEndpoint_1 AIcNormalizeLegacyEndpointId(AIcgdResourceEndpoint_1 aDeclaration) {
        boolean locSourceOutput = "native_product_sources".equals(aDeclaration.resourceKind());
        boolean locDocsSite = "modustro_docs_site".equals(aDeclaration.resourceKind())
            && "modustro".equals(aDeclaration.technologyKind());
        if ((!locSourceOutput && !locDocsSite) || aDeclaration.visibility() == null
            || aDeclaration.action() == null || aDeclaration.technologyKind() == null || aDeclaration.id() == null) {
            return aDeclaration;
        }
        for (String locStability : List.of("release", "snapshot")) {
            String locCurrentSuffix = "-" + aDeclaration.technologyKind() + "-"
                + aDeclaration.resourceKind().replace('_', '-') + "-" + aDeclaration.visibility().wireValue()
                + "-" + locStability + "-" + aDeclaration.action().wireValue();
            if (aDeclaration.id().endsWith(locCurrentSuffix)) return aDeclaration;
            String locLegacySuffix = "-" + aDeclaration.technologyKind()
                + (locDocsSite ? "-docs-site" : "") + "-" + aDeclaration.visibility().wireValue()
                + "-" + locStability + "-" + aDeclaration.action().wireValue();
            if (!aDeclaration.id().endsWith(locLegacySuffix)) continue;
            String locPrefix = aDeclaration.id().substring(0, aDeclaration.id().length() - locLegacySuffix.length());
            String locId = locPrefix + "-" + aDeclaration.technologyKind() + "-"
                + aDeclaration.resourceKind().replace('_', '-') + "-"
                + aDeclaration.visibility().wireValue() + "-" + locStability + "-" + aDeclaration.action().wireValue();
            return new AIcgdResourceEndpoint_1(aDeclaration.technologyKind(), aDeclaration.resourceKind(),
                aDeclaration.visibility(), aDeclaration.action(), locId, aDeclaration.url(),
                aDeclaration.credentialProfile(), aDeclaration.enabled(), aDeclaration.stability(),
                aDeclaration.resourceEndpointProviderAdapter());
        }
        return aDeclaration;
    }

    private static String AIcIdentity(AIcgdResourceEndpoint_1 aDeclaration) {
        String locTechnologyKind = AIcRequireText(aDeclaration.technologyKind(), "TechnologyKind");
        String locResourceKind = AIcRequireText(aDeclaration.resourceKind(), "ResourceKind");
        AIngResourceEndpointVisibility_1 locVisibility = Objects.requireNonNull(aDeclaration.visibility(), "Visibility");
        AIngResourceEndpointAction_1 locAction = Objects.requireNonNull(aDeclaration.action(), "Action");
        String locId = AIcRequireText(aDeclaration.id(), "Id");
        return locTechnologyKind + "|" + locResourceKind + "|" + locVisibility.wireValue() + "|" + locAction.wireValue() + "|" + locId;
    }

    private static AIcgdResourceEndpoint_1 AIcMerge(
        AIcgdResourceEndpoint_1 aBase,
        AIcgdResourceEndpoint_1 aOverride
    ) {
        if (!AIcIdentity(aBase).equals(AIcIdentity(aOverride))) {
            throw new AIxModelValidationException("Cannot merge ResourceEndpoint declarations with different identities.");
        }
        return new AIcgdResourceEndpoint_1(
            aBase.technologyKind(),
            aBase.resourceKind(),
            aBase.visibility(),
            aBase.action(),
            aBase.id(),
            aOverride.url() != null ? aOverride.url() : aBase.url(),
            aOverride.credentialProfile() != null ? aOverride.credentialProfile() : aBase.credentialProfile(),
            aOverride.enabled() != null ? aOverride.enabled() : aBase.enabled(),
            aOverride.stability() != null ? aOverride.stability() : aBase.stability(),
            aOverride.resourceEndpointProviderAdapter() != null
                ? aOverride.resourceEndpointProviderAdapter()
                : aBase.resourceEndpointProviderAdapter()
        );
    }

    private static AIcResourceEndpointDefinition AIcEffective(AIcgdResourceEndpoint_1 aDeclaration) {
        String locUrlText = AIcRequireText(aDeclaration.url(), "Url");
        URI locUrl;
        try {
            locUrl = new URI(locUrlText);
        } catch (URISyntaxException locException) {
            throw new AIxModelValidationException(
                "ResourceEndpoint '" + aDeclaration.id() + "' has invalid URL '" + locUrlText + "'."
            );
        }
        if (!locUrl.isAbsolute()) {
            throw new AIxModelValidationException(
                "ResourceEndpoint '" + aDeclaration.id() + "' URL must be absolute: '" + locUrlText + "'."
            );
        }

        return new AIcResourceEndpointDefinition(
            AIcRequireText(aDeclaration.technologyKind(), "TechnologyKind"),
            AIcRequireText(aDeclaration.resourceKind(), "ResourceKind"),
            Objects.requireNonNull(aDeclaration.visibility(), "Visibility").wireValue(),
            AInResourceEndpointAction.fromWireValue(Objects.requireNonNull(aDeclaration.action(), "Action").wireValue()),
            AIcRequireText(aDeclaration.id(), "Id"),
            locUrl,
            AIcNormalize(aDeclaration.credentialProfile()),
            aDeclaration.enabled() == null || aDeclaration.enabled(),
            aDeclaration.stability() == null
                ? null
                : AInResourceStability.fromWireValue(aDeclaration.stability().wireValue()),
            AIcNormalize(aDeclaration.resourceEndpointProviderAdapter())
        );
    }

    private static String AIcRequireText(String aValue, String aName) {
        String locValue = AIcNormalize(aValue);
        if (locValue == null) {
            throw new AIxModelValidationException("ResourceEndpoint " + aName + " must not be blank.");
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
