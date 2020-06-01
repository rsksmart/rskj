package co.rsk.net.light;

import java.util.HashMap;
import java.util.Map;

public enum LightClientMessageCodes {

    TEST(0x00);

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
        return code >= TEST.asByte() && code <= TEST.asByte();
    }

    public static LightClientMessageCodes fromByte(byte i) {
        return intToTypeMap.get((int) i);
    }

    public byte asByte() {
        return (byte) (cmd);
    }
}
