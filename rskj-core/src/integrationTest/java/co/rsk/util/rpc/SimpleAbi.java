package co.rsk.util.rpc;
/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import co.rsk.util.HexUtils;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SimpleAbi {

    private SimpleAbi() {}

    /**
     * Encodes a function call with static arguments only.
     *
     * Supported arg types:
     *  - byte[]     -> bytes32
     *  - BigInteger -> uint256
     *  - String     -> address (0x...)
     *
     * @param functionSignature e.g. "acceptUnion(bytes32,uint256)"
     * @param args ABI arguments
     * @return calldata hex string (...) without 0x
     */
    public static String encode(String functionSignature, List<Object> args) {
        byte[] selector = selector(functionSignature);
        byte[] encodedArgs = encodeArgs(args);

        byte[] out = new byte[selector.length + encodedArgs.length];
        System.arraycopy(selector, 0, out, 0, selector.length);
        System.arraycopy(encodedArgs, 0, out, selector.length, encodedArgs.length);

        return ByteUtil.toHexString(out);
    }

    private static byte[] selector(String sig) {
        byte[] hash = HashUtil.keccak256(sig.getBytes(StandardCharsets.UTF_8));
        return new byte[] { hash[0], hash[1], hash[2], hash[3] };
    }

    private static byte[] encodeArgs(List<Object> args) {
        byte[] out = new byte[32 * args.size()];
        for (int i = 0; i < args.size(); i++) {
            byte[] enc = encodeStaticWord(args.get(i));
            System.arraycopy(enc, 0, out, i * 32, 32);
        }
        return out;
    }

    /**
     * Encodes a supported static Solidity ABI value into a 32-byte word.
     * Supported types:
     * - BigInteger → uint256 (non-negative)
     * - String (hex) → address (20 bytes)
     * - byte[] → bytes32 (exactly 32 bytes)
     * This method DOES NOT support:
     * - int256 (signed integers)
     * - bytesN where N &lt; 32
     * - >dynamic types (bytes, string)
     * @throws IllegalArgumentException if the value is invalid or unsupported
     */
    private static byte[] encodeStaticWord(Object arg) {

        if (arg instanceof BigInteger bi) {
            if (bi.signum() < 0) {
                throw new IllegalArgumentException("uint256 cannot be negative");
            }
            return leftPad32(unsignedBigInt(bi));
        }

        if (arg instanceof String hex) {
            byte[] address = HexUtils.stringHexToByteArray(hex);
            if (address.length != 20) throw new IllegalArgumentException("Address must be 20 bytes");
            return leftPad32(address);
        }

        if (arg instanceof byte[] b) {
            if (b.length != 32)  throw new IllegalArgumentException("byte[] must be exactly 32 bytes (bytes32 supported only)");
            return b;
        }
        throw new IllegalArgumentException("Unsupported ABI static type: " + arg.getClass().getName());
    }

    private static byte[] leftPad32(byte[] raw) {
        if (raw.length > 32)  throw new IllegalArgumentException("Value exceeds 32 bytes");
        if (raw.length == 32)  return raw;
        byte[] out = new byte[32];
        System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        return out;
    }

    private static byte[] unsignedBigInt(BigInteger bi) {
        byte[] raw = bi.toByteArray();
        if (raw.length > 1 && raw[0] == 0) {
            byte[] tmp = new byte[raw.length - 1];
            System.arraycopy(raw, 1, tmp, 0, tmp.length);
            return tmp;
        }
        return raw;
    }
}