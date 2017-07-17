package co.rsk.scoring;

/**
 * EventType is an enumeration to represent the events to be recorded
 * in peer scoring (@see PeerScoring)
 * <p>
 * Created by ajlopez on 27/06/2017.
 */
public enum EventType {
    INVALID_BLOCK,
    VALID_BLOCK,
    INVALID_TRANSACTION,
    VALID_TRANSACTION,
    FAILED_HANDSHAKE,
    SUCCESSFUL_HANDSHAKE,
    INVALID_NETWORK,
    INCOMPATIBLE_PROTOCOL,
    DISCONNECTION,
    REPEATED_MESSAGE
}
