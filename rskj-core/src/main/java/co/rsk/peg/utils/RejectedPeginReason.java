package co.rsk.peg.utils;

public enum RejectedPeginReason {
    PEGIN_CAP_SURPASSED(1),
    LEGACY_PEGIN_MULTISIG_SENDER(2),
    LEGACY_PEGIN_UNDETERMINED_SENDER(3),
    PEGIN_V1_INVALID_PAYLOAD(4),
    NO_UTXO_OR_UTXO_BELOW_MINIMUM(5),
    ;

    private final int value;

    RejectedPeginReason(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
