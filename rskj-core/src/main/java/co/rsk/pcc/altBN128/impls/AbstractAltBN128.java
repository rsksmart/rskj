package co.rsk.pcc.altBN128.impls;

import co.rsk.altbn128.cloudflare.Utils;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public abstract class AbstractAltBN128 {

    private static final Logger logger = LoggerFactory.getLogger("altbn128");

    public static final int PAIR_SIZE = 192;

    protected byte[] output;

    public static AbstractAltBN128 init() {
        return init(Utils::isLinux, GoAltBN128::getLoadError);
    }

    @VisibleForTesting
    protected static AbstractAltBN128 init(@Nonnull Supplier<Boolean> linuxEnvChecker,
                                           @Nonnull Supplier<Throwable> goAltBN128LoadErrorProvider) {
        if (linuxEnvChecker.get()) {
            Throwable loadError = goAltBN128LoadErrorProvider.get();
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
