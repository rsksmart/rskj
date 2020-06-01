/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.net.light;

import java.util.HashMap;
import java.util.Map;

public enum LightClientMessageCodes {

    STATUS(0x00),

    GET_BLOCK_RECEIPTS(0x01),

    BLOCK_RECEIPTS(0x02),

    GET_TRANSACTION_INDEX(0x03),

    TRANSACTION_INDEX(0x04),

    GET_CODE(0x05),

    CODE(0x06),

    GET_ACCOUNTS(0x07),

    ACCOUNTS(0x08),

    GET_BLOCK_HEADER(0x09),

    BLOCK_HEADER(0x0A),

    GET_BLOCK_BODY(0x0B),

    BLOCK_BODY(0x0C),

    GET_STORAGE(0x0D),

    STORAGE(0x0E);

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
        return code >= STATUS.asByte() && code <= STORAGE.asByte();
    }

    public static LightClientMessageCodes fromByte(byte i) {
        return intToTypeMap.get((int) i);
    }

    public byte asByte() {
        return (byte) (cmd);
    }
}
