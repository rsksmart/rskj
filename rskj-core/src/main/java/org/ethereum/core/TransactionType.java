package org.ethereum.core;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum TransactionType {

    LEGACY((byte) 0);

    private static final Map<Byte, TransactionType> lookup = new HashMap<>();
    private final byte byteCode;

    static {
        for (TransactionType s : EnumSet.allOf(TransactionType.class)) {
            lookup.put(s.getByteCode(), s);
        }
    }

    TransactionType(byte b) {
        this.byteCode = b;
    }

    public byte getByteCode() {
        return this.byteCode;
    }

    // Reverse Lookup
    public static TransactionType getByByte(byte type) {
        return lookup.get(type);
    }

}
