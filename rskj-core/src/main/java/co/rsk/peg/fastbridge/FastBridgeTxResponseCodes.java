package co.rsk.peg.fastbridge;

public enum FastBridgeTxResponseCodes {
    REFUNDED_USER_ERROR(-100),
    REFUNDED_LP_ERROR(-200),
    UNPROCESSABLE_TX_NOT_CONTRACT_ERROR(-300),
    UNPROCESSABLE_TX_INVALID_SENDER_ERROR(-301),
    UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR(-302),
    UNPROCESSABLE_TX_VALIDATIONS_ERROR(-303),
    UNPROCESSABLE_TX_VALUE_ZERO_ERROR(-304),
    UNPROCESSABLE_TX_AMOUNT_SENT_BELOW_MINIMUM_ERROR(-305),
    UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR(-306),
    GENERIC_ERROR(-900),
    VALID_TX(0);

    private final long value;

    FastBridgeTxResponseCodes(long value) {
        this.value = value;
    }

    public long value() {
        return value;
    }
}