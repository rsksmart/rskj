package co.rsk.pcc.altBN128.impls;

import co.rsk.altbn128.cloudflare.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAltBN128 {

    private static final Logger logger = LoggerFactory.getLogger("altbn128");

    public static final int PAIR_SIZE = 192;

    protected byte[] output;

    public static AbstractAltBN128 init() {
        if (Utils.isLinux()) {
            Throwable loadError = GoAltBN128.getLoadError();
            if (loadError == null) {
                return new GoAltBN128();
            }

            logger.warn("Cannot load GoAltBN128 library due to '{}'. Falling back on JavaAltBN128", loadError.getMessage());
        }
        return new JavaAltBN128();
    }

    protected AbstractAltBN128() {
    }

    public abstract int add(byte[] data, int length);

    public abstract int mul(byte[] data, int length);

    public abstract int pairing(byte[] data, int length);

    public byte[] getOutput() {
        return output.clone();
    }
}
