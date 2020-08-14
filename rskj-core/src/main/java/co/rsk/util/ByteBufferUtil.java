package co.rsk.util;

import org.ethereum.util.ByteUtil;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;

public class ByteBufferUtil {
    public static byte[] copyToArray(@Nonnull ByteBuffer data) {
        byte[] copy = new byte[data.remaining()];
        data.duplicate().get(copy);
        return copy;
    }

    public static String toHexString(@Nonnull ByteBuffer data) {
        return ByteUtil.toHexString(copyToArray(data));
    }
}
