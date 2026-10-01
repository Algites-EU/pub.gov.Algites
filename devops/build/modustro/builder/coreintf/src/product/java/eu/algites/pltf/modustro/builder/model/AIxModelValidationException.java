package eu.algites.pltf.modustro.builder.model;

/**
 * Reports an invalid Modustro Builder model definition.
 */
public class AIxModelValidationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a model-validation exception with the supplied message.
     *
     * @param aMessage diagnostic message
     */
    public AIxModelValidationException(String aMessage) {
        super(aMessage);
    }
}
