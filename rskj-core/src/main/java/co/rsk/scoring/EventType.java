package co.rsk.scoring;

/**
 * Created by ajlopez on 27/06/2017.
 */
public enum EventType {
    INVALID_BLOCK,
    VALID_BLOCK,
    INVALID_TRANSACTION,
    VALID_TRANSACTION,
    FAILED_HANDSHAKE,
    SUCCESSFUL_HANDSHAKE,
    BAD_NETWORK,
    DISCONNECTION
}
