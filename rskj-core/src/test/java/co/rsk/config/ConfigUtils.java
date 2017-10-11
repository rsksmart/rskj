package co.rsk.config;

public class ConfigUtils {
    public static MiningConfig getDefaultMiningConfig() {
        return new MiningConfig(
                false,
                new byte[] {-120, 95, -109, -18, -43, 119, -14, -4, 52, 30, -69, -102, 92, -101, 44, -28, 70, 93, -106, -60},
                0.0,
                0.0,
                0,
                3000000,
                5000000,
                true,
                10,
                7
        );
    }
}
