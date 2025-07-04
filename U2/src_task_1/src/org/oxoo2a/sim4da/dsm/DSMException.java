package org.oxoo2a.sim4da.dsm;

/**
 * Exception thrown during DSM operations.
 */
public class DSMException extends Exception {
    public DSMException(String message) {
        super(message);
    }
    
    public DSMException(String message, Throwable cause) {
        super(message, cause);
    }
}
