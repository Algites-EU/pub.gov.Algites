package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import java.util.regex.Pattern;

/**
 * Validates author-controlled GlobalPublicationPathId values at the publication trust boundary.
 */
public final class AIcGlobalPublicationPathValidator {

    private static final Pattern VALID_PATTERN = Pattern.compile("^(?!/)(?!.*(?:^|/)\\.\\.(?:/|$))(?!.*//)[A-Za-z0-9._/-]+$");

    /**
     * Validates and normalizes one GlobalPublicationPathId.
     *
     * @param aValue author-controlled path id
     * @return normalized path id
     */
    public String validate(String aValue) {
        String locValue = aValue == null ? null : aValue.trim();
        if (locValue == null || locValue.isEmpty()) {
            throw new AIxModelValidationException("GlobalPublicationPathId must not be blank.");
        }
        if (!VALID_PATTERN.matcher(locValue).matches()) {
            throw new AIxModelValidationException("Invalid GlobalPublicationPathId '" + locValue + "'.");
        }
        return locValue;
    }
}
