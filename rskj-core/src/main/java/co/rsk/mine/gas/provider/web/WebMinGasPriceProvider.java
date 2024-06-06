package co.rsk.mine.gas.provider.web;

import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.mine.gas.provider.StableMinGasPriceProvider;

public class WebMinGasPriceProvider extends StableMinGasPriceProvider {
    private final String url;
    private final String jsonPath;
    private final String apiKey;
    private final int timeout;
    private final int refreshRate;

    WebMinGasPriceProvider(MinGasPriceProvider fallBackProvider, WebStableMinGasPriceConfig config, StableMinGasPriceProvider.GetContextCallback getContextCallback) {
        super(fallBackProvider, getContextCallback);
        url = config.getUrl();
        jsonPath = config.getJsonPath();
        apiKey = config.getApiKey();
        timeout = config.getTimeout();
        refreshRate = config.getRefreshRate();
    }

    @Override
    public Long getStableMinGasPrice() {
        return null;
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.WEB;
    }

    public String getUrl() {
        return url;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getRefreshRate() {
        return refreshRate;
    }
}

