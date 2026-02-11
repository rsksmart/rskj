package co.rsk.util.rpc;

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
     * @return calldata hex string (0x...)
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
            byte[] enc = encodeOne(args.get(i));
            System.arraycopy(enc, 0, out, i * 32, 32);
        }
        return out;
    }

    private static byte[] encodeOne(Object arg) {
        if (arg instanceof byte[] b) {
            return pad32(b);
        }
        if (arg instanceof BigInteger bi) {
            return pad32(unsignedBigInt(bi));
        }
        if (arg instanceof String hex) {
            // address
            return pad32(HexUtils.stringToByteArray(hex));
        }
        throw new IllegalArgumentException("Unsupported ABI arg type: " + arg.getClass());
    }

    private static byte[] pad32(byte[] raw) {
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
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