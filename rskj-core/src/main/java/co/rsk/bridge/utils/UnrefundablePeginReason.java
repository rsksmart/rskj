package co.rsk.bridge.utils;

public enum UnrefundablePeginReason {
    LEGACY_PEGIN_UNDETERMINED_SENDER(1),
    PEGIN_V1_REFUND_ADDRESS_NOT_SET(2);

    private final int value;

    UnrefundablePeginReason(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
