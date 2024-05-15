package co.rsk.config.mining;

import com.typesafe.config.Config;

public class WebStableMinGasSystemConfig {
    public static final String WEB_STABLE_GAS_PRICE_CONFIG_PATH = "web";
    private static final String URL_PROPERTY = "url";
    private static final String JSON_PATH_PROPERTY = "jsonPath";
    private static final String TIMEOUT_PROPERTY = "timeout";
    private static final String API_KEY_PROPERTY = "apiKey";

    private final String url;
    private final String jsonPath;
    private final int timeout;
    private final String apiKey;

    public WebStableMinGasSystemConfig(Config config) {
        this.url = config.getString(URL_PROPERTY);
        this.jsonPath = config.getString(JSON_PATH_PROPERTY);
        this.timeout = config.getInt(TIMEOUT_PROPERTY);
        this.apiKey = config.getString(API_KEY_PROPERTY);
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
}
