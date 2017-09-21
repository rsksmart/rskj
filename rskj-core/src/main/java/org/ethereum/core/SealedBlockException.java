package org.ethereum.core;

/**
 * Created by ajlopez on 14/08/2017.
 */

public class SealedBlockException extends RuntimeException {
    public SealedBlockException(String message) {
        super("Sealed block: " + message);
    }
}
