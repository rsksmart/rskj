package co.rsk.mine.gas.provider.web;

import co.rsk.config.mining.WebStableMinGasSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.StableMinGasPriceProvider;

public class WebStableMinGasPriceProviderFactory {

    private WebStableMinGasPriceProviderFactory() {
    }

    public static StableMinGasPriceProvider create(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallbackProvider, StableMinGasPriceProvider.GetContextCallback getContextCallback) {
        WebStableMinGasSystemConfig httpGetSystemConfig = config.getWebConfig();
        WebStableMinGasPriceConfig httpGetStableMinGasPriceConfig = WebStableMinGasPriceConfig.builder().setUrl(httpGetSystemConfig.getUrl())
                .setJsonPath(httpGetSystemConfig.getRequestPath())
                .setTimeout(httpGetSystemConfig.getTimeout())
                .setApiKey(httpGetSystemConfig.getApiKey())
                .setMinStableGasPrice(config.getMinStableGasPrice())
                .setRefreshRate(config.getRefreshRate())
                .build();
        return new WebMinGasPriceProvider(fallbackProvider, httpGetStableMinGasPriceConfig, getContextCallback);
    }
}

