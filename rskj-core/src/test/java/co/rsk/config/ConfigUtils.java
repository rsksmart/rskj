package co.rsk.config;

import co.rsk.core.RskAddress;

public class ConfigUtils {
    public static MiningConfig getDefaultMiningConfig() {
        final byte[] coinbaseAddress = new byte[]{-120, 95, -109, -18, -43, 119, -14, -4, 52, 30, -69, -102, 92, -101, 44, -28, 70, 93, -106, -60};
        return new MiningConfig(
                new RskAddress(coinbaseAddress),
                0.0,
                0.0,
                0,
                10,
                7,
                new GasLimitConfig(3000000, 500000, true)
        );
    }
}
