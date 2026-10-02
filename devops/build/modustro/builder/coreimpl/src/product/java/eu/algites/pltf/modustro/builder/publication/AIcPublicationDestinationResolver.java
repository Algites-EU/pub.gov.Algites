package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationDestinationSelection;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointCatalog;
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceEndpointAction;
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Resolves publication capability destination ids against the effective ResourceEndpoint catalog.
 */
public final class AIcPublicationDestinationResolver {

    /**
     * Selects effective publication endpoints.
     *
     * <p>An empty destination-id collection selects every enabled endpoint matching the publication context.
     * Explicit destination ids are strict: every id must exist, be enabled, and match the requested context.</p>
     *
     * @param aCatalog effective ResourceEndpoint catalog
     * @param aResourceKind required ResourceKind
     * @param aVisibility required endpoint visibility
     * @param aStability required stability, or {@code null} for ResourceKinds that forbid Stability
     * @param aPublicationDestinationIds optional explicit ResourceEndpoint ids
     * @return immutable publication destination selection
     */
    public AIcPublicationDestinationSelection selectUploadDestinations(
        AIcResourceEndpointCatalog aCatalog,
        String aResourceKind,
        String aVisibility,
        AInResourceStability aStability,
        Collection<String> aPublicationDestinationIds
    ) {
        Objects.requireNonNull(aCatalog, "catalog");
        String locResourceKind = AIcRequireText(aResourceKind, "resourceKind");
        String locVisibility = AIcRequireText(aVisibility, "visibility");
        List<String> locRequestedIds = AIcNormalizeIds(aPublicationDestinationIds);

        List<AIcResourceEndpointDefinition> locCandidates = aCatalog.select(
            "modustro",
            locResourceKind,
            locVisibility,
            AInResourceEndpointAction.UPLOAD,
            aStability,
            true
        );

        if (locRequestedIds.isEmpty()) {
            return new AIcPublicationDestinationSelection(locResourceKind, locCandidates);
        }

        ArrayList<AIcResourceEndpointDefinition> locSelected = new ArrayList<>();
        for (String locId : locRequestedIds) {
            AIcResourceEndpointDefinition locEndpoint = aCatalog.byId(locId)
                .orElseThrow(() -> new AIxModelValidationException(
                    "PublicationDestinations references unknown ResourceEndpoint '" + locId + "'."
                ));
            boolean locMatches = locEndpoint.enabled()
                && locEndpoint.technologyKind().equals("modustro")
                && locEndpoint.resourceKind().equals(locResourceKind)
                && locEndpoint.visibility().equals(locVisibility)
                && locEndpoint.action() == AInResourceEndpointAction.UPLOAD
                && Objects.equals(locEndpoint.stability(), aStability);
            if (!locMatches) {
                throw new AIxModelValidationException(
                    "PublicationDestinations ResourceEndpoint '" + locId + "' does not match the required publication context "
                        + "modustro/" + locResourceKind + "/" + locVisibility + "/upload"
                        + (aStability == null ? " without Stability." : "/" + aStability.wireValue() + ".")
                );
            }
            locSelected.add(locEndpoint);
        }
        return new AIcPublicationDestinationSelection(locResourceKind, locSelected);
    }

    private static List<String> AIcNormalizeIds(Collection<String> aIds) {
        if (aIds == null) {
            return List.of();
        }
        LinkedHashSet<String> locResult = new LinkedHashSet<>();
        for (String locValue : aIds) {
            String locId = AIcRequireText(locValue, "PublicationDestinations item");
            if (!locResult.add(locId)) {
                throw new AIxModelValidationException("PublicationDestinations contains duplicate ResourceEndpoint id '" + locId + "'.");
            }
        }
        return List.copyOf(locResult);
    }

    private static String AIcRequireText(String aValue, String aName) {
        String locValue = aValue == null ? null : aValue.trim();
        if (locValue == null || locValue.isEmpty()) {
            throw new AIxModelValidationException(aName + " must not be blank.");
        }
        return locValue;
    }
}
