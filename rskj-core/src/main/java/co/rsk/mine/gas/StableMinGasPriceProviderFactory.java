package co.rsk.mine.gas;

import co.rsk.config.mining.HttpGetStableMinGasSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StableMinGasPriceProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger("StableMinGasPrice");

    private StableMinGasPriceProviderFactory() {}

    public static MinGasPriceProvider create(StableMinGasPriceSystemConfig config, DefaultMinGasPriceProvider fallBackProvider) {
        MinGasPriceProviderType method = config.getMethod();
        if (method == null) {
            logger.error("Could not finde valid mehtod in config, using fallback provider: {}", fallBackProvider.getType().name());
            return fallBackProvider;
        }
        switch (method) {
            case HTTP_GET:
                return createHttpGetStableGasPriceProvider(config, fallBackProvider);
            case ETH_CALL:
                throw new UnsupportedOperationException("ETH_CALL method is not supported yet");
            default:
                logger.debug("Could not find a valid implementation for the method {}. Returning fallback provider {}", method, fallBackProvider.getType().name());
                return fallBackProvider;
        }
    }

    private static HttpGetStableMinGasPriceProvider createHttpGetStableGasPriceProvider(StableMinGasPriceSystemConfig config, DefaultMinGasPriceProvider fallBackProvider) {
        HttpGetStableMinGasSystemConfig httpGetSystemConfig = config.getHttpGetConfig();
        HttpGetStableMinGasPriceConfig httpGetStableMinGasPriceConfig = HttpGetStableMinGasPriceConfig.builder().setUrl(httpGetSystemConfig.getUrl())
                .setJsonPath(httpGetSystemConfig.getJsonPath())
                .setTimeout(httpGetSystemConfig.getTimeout())
                .setApiKey(httpGetSystemConfig.getApiKey())
                .setMinStableGasPrice(config.getMinStableGasPrice())
                .setRefreshRate(config.getRefreshRate())
                .build();
        return new HttpGetStableMinGasPriceProvider(fallBackProvider, httpGetStableMinGasPriceConfig);
    }
}
