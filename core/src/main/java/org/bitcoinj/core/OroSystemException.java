package org.bitcoinj.core;

/**
 * Thrown when something goes wrong with storing a block. Examples: out of disk space.
 */
public class OroSystemException extends Exception {
    public OroSystemException(String message) {
        super(message);
    }
    public OroSystemException(String message, Throwable t) { super(message, t); }
    public OroSystemException(Throwable t) {
        super(t);
    }
}
