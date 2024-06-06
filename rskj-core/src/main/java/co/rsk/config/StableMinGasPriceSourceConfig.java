package co.rsk.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.ArrayList;
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

    public String sourceFrom() {
        if (!sourceConfig.hasPath("from")) {
            throw new IllegalArgumentException("From address config is required");
        }
        return sourceConfig.getString("from");
    }

    public String sourceContract() {
        if (!sourceConfig.hasPath("contract")) { // TODO: I suspect that there is better way to handle this?
            throw new IllegalArgumentException("Contract config is required");
        }
        return sourceConfig.getString("contract");
    }

    public String sourceContractData() {
        if (!sourceConfig.hasPath("data")) {
            throw new IllegalArgumentException("Data config is required");
        }
        return sourceConfig.getString("data");
    }
}
