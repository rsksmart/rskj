package co.rsk.pcc.secp256k1.impls;

import co.rsk.altbn128.cloudflare.Utils;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class AbstractSecp256k1 {

    private static final Logger logger = LoggerFactory.getLogger("secp256k1");

    protected byte[] output;

    /**
     * Besides logging information about loaded library, this method has a side effect of enforcing native library loading,
     * if it's not loaded yet. So that it allows to fail early, if, for instance, the loaded library is not compatible
     * with the current environment.
     */
    public static void init() {
        AbstractSecp256k1 altSecp256k1 = Objects.requireNonNull(create());
        /*
            Load the library

         */
    }

    public static AbstractSecp256k1 create() {
        return create(Utils::isLinux, ExternalSecp256k1::getLoadError);
    }

    @VisibleForTesting
    protected static AbstractSecp256k1 create(@Nonnull Supplier<Boolean> linuxEnvChecker,
                                              @Nonnull Supplier<Throwable> extLoadErrorProvider) {
        /*if (linuxEnvChecker.get()) {
            Throwable loadError = extLoadErrorProvider.get();
            if (loadError == null) {
                return new ExternalSecp256k1();
            }
        }
         */
        return new JavaSecp256k1();
    }

    protected AbstractSecp256k1() {
    }

    public abstract int add(byte[] data, int length);

    public abstract int mul(byte[] data, int length);


    public byte[] getOutput() {
        return output.clone();
    }
}
