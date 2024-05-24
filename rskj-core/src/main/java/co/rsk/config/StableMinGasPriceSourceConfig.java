package co.rsk.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

import java.time.Duration;
import java.util.List;

public class StableMinGasPriceSourceConfig
{
    Config sourceConfig;

    public StableMinGasPriceSourceConfig(ConfigObject sourceConfig) {
        this.sourceConfig = sourceConfig.toConfig();
    }

    public String sourceType() {
        return sourceConfig.getString("type");
    }

    public String sourceUrl() {
        return sourceConfig.getString("url");
    }

    public String sourceApiKey() {
        return sourceConfig.getString("apiKey");
    }

    public String sourceJsonPath() {
        return sourceConfig.getString("path");
    }

    public Duration sourceTimeout() {
        return sourceConfig.getDuration("timeout");
    }

    public String sourceContract() {
        return sourceConfig.getString("contract");
    }

    public String sourceContractMethod() {
        return sourceConfig.getString("method");
    }

    public List<String> sourceContractMethodParams() {
        return sourceConfig.getStringList("params");
    }
}
