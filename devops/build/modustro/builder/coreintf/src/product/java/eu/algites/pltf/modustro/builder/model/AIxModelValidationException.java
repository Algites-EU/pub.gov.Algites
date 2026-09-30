package eu.algites.pltf.modustro.builder.model;

/**
 * Reports an invalid Modustro Builder model definition.
 */
public class AIxModelValidationException extends IllegalArgumentException {

    public AIxModelValidationException(String aMessage) {
        super(aMessage);
    }
}
