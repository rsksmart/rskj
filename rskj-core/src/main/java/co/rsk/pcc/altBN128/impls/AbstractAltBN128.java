package co.rsk.pcc.altBN128.impls;

import co.rsk.altbn128.cloudflare.Utils;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class AbstractAltBN128 {

    private static final Logger logger = LoggerFactory.getLogger("altbn128");

    public static final int PAIR_SIZE = 192;

    protected byte[] output;

    /**
     * Besides logging information about loaded library, this method has a side effect of enforcing native library loading,
     * if it's not loaded yet. So that it allows to fail early, if, for instance, the loaded library is not compatible
     * with the current environment.
     */
    public static void init() {
        AbstractAltBN128 altBn128 = Objects.requireNonNull(create());

        if (Utils.isLinux() && !(altBn128 instanceof GoAltBN128)) {
            logger.warn("Cannot load {} library due to '{}'. Falling back to {}",
                    GoAltBN128.class.getSimpleName(),
                    Optional.ofNullable(GoAltBN128.getLoadError()).map(Throwable::getMessage).orElse("unknown error"),
                    altBn128.getClass().getSimpleName());
        } else {
            logger.info("Loaded {}", altBn128.getClass().getSimpleName());
        }
    }

    public static AbstractAltBN128 create() {
        return create(Utils::isLinux, GoAltBN128::getLoadError);
    }

    @VisibleForTesting
    protected static AbstractAltBN128 create(@Nonnull Supplier<Boolean> linuxEnvChecker,
                                             @Nonnull Supplier<Throwable> goAltBN128LoadErrorProvider) {
        if (linuxEnvChecker.get()) {
            Throwable loadError = goAltBN128LoadErrorProvider.get();
            if (loadError == null) {
                return new GoAltBN128();
            }
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
