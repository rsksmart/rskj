package co.rsk.peg.utils;

public enum NonRefundablePeginReason {
    LEGACY_PEGIN_UNDETERMINED_SENDER(1),
    PEGIN_V1_REFUND_ADDRESS_NOT_SET(2),
    INVALID_AMOUNT(3);

    private final int value;

    NonRefundablePeginReason(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
