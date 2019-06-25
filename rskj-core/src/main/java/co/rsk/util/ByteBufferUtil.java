package co.rsk.util;

import java.nio.ByteBuffer;
import javax.annotation.Nonnull;
import org.bouncycastle.util.encoders.Hex;

public class ByteBufferUtil {
    public static byte[] copyToArray(@Nonnull ByteBuffer data) {
        byte[] copy = new byte[data.remaining()];
        data.duplicate().get(copy);
        return copy;
    }

    public static String toHexString(@Nonnull ByteBuffer data) {
        return Hex.toHexString(copyToArray(data));
    }
}
