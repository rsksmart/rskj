package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;

import java.time.Duration;

import static co.rsk.mine.minGasPrice.ExchangeRateProvider.XRSourceType.HTTP_GET;

public class HttpGetXRProvider extends ExchangeRateProvider {
    private final String url;
    private final String apiKey;
    private final String jsonPath;
    private final Duration timeout;

    public HttpGetXRProvider(StableMinGasPriceSourceConfig sourceConfig) {
        this(
                sourceConfig.sourceUrl(),
                sourceConfig.sourceApiKey(),
                sourceConfig.sourceJsonPath(),
                sourceConfig.sourceTimeout()
        );
    }

    public HttpGetXRProvider(
            String url, String apiKey,
            String jsonPath,
            Duration timeout
    ) {
        super(HTTP_GET);
        this.url = url;
        this.apiKey = apiKey;
        this.jsonPath = jsonPath;
        this.timeout = timeout;
    }

    public String getUrl() {
        return url;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public long getPrice(MinGasPriceProvider.ProviderContext context) {
        return 0;
    }
}
