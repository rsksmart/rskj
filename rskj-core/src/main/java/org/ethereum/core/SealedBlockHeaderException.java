package org.ethereum.core;

/**
 * Created by ajlopez on 14/08/2017.
 */

public class SealedBlockHeaderException extends RuntimeException {
    public SealedBlockHeaderException(String message) {
        super("Sealed block header: " + message);
    }
}
