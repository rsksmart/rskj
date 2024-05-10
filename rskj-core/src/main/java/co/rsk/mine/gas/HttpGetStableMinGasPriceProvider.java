package co.rsk.mine.gas;

public class HttpGetStableMinGasPriceProvider extends StableMinGasPriceProvider {
    private final String url;
    private final String jsonPath;
    private final String apiKey;
    private final int timeout;
    private final int refreshRate;

    HttpGetStableMinGasPriceProvider(DefaultMinGasPriceProvider fallBackProvider, HttpGetStableMinGasPriceConfig config) {
        super(fallBackProvider);
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
        return MinGasPriceProviderType.HTTP_GET;
    }
}

