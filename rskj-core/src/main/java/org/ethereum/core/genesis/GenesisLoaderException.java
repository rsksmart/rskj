package org.ethereum.core.genesis;

public class GenesisLoaderException extends RuntimeException {
    GenesisLoaderException(String message, Throwable error) {
        super(message, error);
    }
}
