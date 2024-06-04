package co.rsk.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;

public class StableMinGasPriceSourceConfig
{
    Config sourceConfig;

    public StableMinGasPriceSourceConfig(@Nonnull ConfigObject sourceConfig) {
        this.sourceConfig = sourceConfig.toConfig();
    }

    public String sourceType() {
        return sourceConfig.hasPath("type") ? sourceConfig.getString("type") : null;
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
        if (!sourceConfig.hasPath("contract")) {
            throw new IllegalArgumentException("Contract config is required");
        }
        return sourceConfig.getString("contract");
    }

    public String sourceContractMethod() {
        return sourceConfig.getString("method");
    }

    public List<String> sourceContractMethodParams() {
        return sourceConfig.getStringList("params");
    }
}
