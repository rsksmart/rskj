package co.rsk.mine.gas;

public class HttpGetStableMinGasPriceConfig {
    private final String url;
    private final String jsonPath;
    private final int timeout;
    private final String apiKey;
    private final long minStableGasPrice;
    private final int refreshRate;

    public HttpGetStableMinGasPriceConfig(String url, String jsonPath, int timeout, String apiKey, long minStableGasPrice, int refreshRate) {
        this.url = url;
        this.jsonPath = jsonPath;
        this.timeout = timeout;
        this.apiKey = apiKey;
        this.minStableGasPrice = minStableGasPrice;
        this.refreshRate = refreshRate;
    }

    public String getUrl() {
        return url;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public int getTimeout() {
        return timeout;
    }

    public String getApiKey() {
        return apiKey;
    }

    public long getMinStableGasPrice() {
        return minStableGasPrice;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url;
        private String jsonPath;
        private int timeout;
        private String apiKey;
        private long minStableGasPrice;
        private int refreshRate;

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setJsonPath(String jsonPath) {
            this.jsonPath = jsonPath;
            return this;
        }

        public Builder setTimeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder setMinStableGasPrice(long minStableGasPrice) {
            this.minStableGasPrice = minStableGasPrice;
            return this;
        }

        public Builder setRefreshRate(int refreshRate) {
            this.refreshRate = refreshRate;
            return this;
        }

        public HttpGetStableMinGasPriceConfig build() {
            return new HttpGetStableMinGasPriceConfig(url, jsonPath, timeout, apiKey, minStableGasPrice, refreshRate);
        }
    }


}
