package co.rsk.config.mining;

import co.rsk.mine.gas.MinGasPriceProviderType;
import com.typesafe.config.Config;

public class StableMinGasPriceSystemConfig {
    public static final String STABLE_GAS_PRICE_CONFIG_PATH_PROPERTY = "miner.stableGasPrice";
    private static final String ENABLED_PROPERTY = "enabled";
    private static final String REFRESH_RATE_PROPERTY = "refreshRate";
    private static final String MIN_STABLE_GAS_PRICE_PROPERTY = "minStableGasPrice";
    private static final String METHOD_PROPERTY = "method";

    private final Integer refreshRate;
    private final Integer minStableGasPrice;
    private final boolean enabled;
    private final MinGasPriceProviderType method;
    private final Config config;

    public StableMinGasPriceSystemConfig(Config config) {
        enabled = config.getBoolean(ENABLED_PROPERTY);
        refreshRate = config.getInt(REFRESH_RATE_PROPERTY);
        minStableGasPrice = config.getInt(MIN_STABLE_GAS_PRICE_PROPERTY);
        method = config.getEnum(MinGasPriceProviderType.class, METHOD_PROPERTY);
        this.config = config;
    }

    public boolean isValid() {
        return true;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public int getMinStableGasPrice() {
        return minStableGasPrice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public HttpGetStableMinGasSystemConfig getHttpGetConfig() {
        return new HttpGetStableMinGasSystemConfig(config.getConfig(HttpGetStableMinGasSystemConfig.HTTP_GET_STABLE_GAS_PRICE_CONFIG_PATH));
    }

    public MinGasPriceProviderType getMethod() {
        return method;
    }
}
