package co.rsk.net.light;

import java.util.HashMap;
import java.util.Map;

public enum LightClientMessageCodes {

    TEST(0x00),

    GET_BLOCK_RECEIPTS(0x01),

    BLOCK_RECEIPTS(0x02),

    GET_TRANSACTION_INDEX(0x03),

    TRANSACTION_INDEX(0x04),

    GET_CODE(0x05),

    CODE(0x06),

    GET_ACCOUNTS(0x07),

    ACCOUNTS(0x08),

    GET_BLOCK_HEADER(0x09),

    BLOCK_HEADER(0x0A);

    private final int cmd;

    private static final Map<Integer, LightClientMessageCodes> intToTypeMap = new HashMap<>();

    static {
        for (LightClientMessageCodes type : LightClientMessageCodes.values()) {
            intToTypeMap.put(type.cmd, type);
        }
    }

    LightClientMessageCodes(int cmd) {
        this.cmd = cmd;
    }

    public static boolean inRange(byte code) {
        return code >= TEST.asByte() && code <= BLOCK_HEADER.asByte();
    }

    public static LightClientMessageCodes fromByte(byte i) {
        return intToTypeMap.get((int) i);
    }

    public byte asByte() {
        return (byte) (cmd);
    }
}
