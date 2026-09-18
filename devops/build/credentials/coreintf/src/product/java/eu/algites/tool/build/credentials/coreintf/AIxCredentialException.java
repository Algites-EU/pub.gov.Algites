package eu.algites.tool.build.credentials.coreintf;

/**
 * Signals credential resolution or secure-store failures.
 */
public class AIxCredentialException extends RuntimeException {
    public AIxCredentialException(String aMessage) {
        super(aMessage);
    }

    public AIxCredentialException(String aMessage, Throwable aCause) {
        super(aMessage, aCause);
    }
}
