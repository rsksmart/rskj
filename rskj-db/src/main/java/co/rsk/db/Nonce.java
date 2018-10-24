package co.rsk.db;

public interface Nonce {
    Nonce ONE = () -> null;
    Nonce ZERO = () -> ONE;

    Nonce next();
}
