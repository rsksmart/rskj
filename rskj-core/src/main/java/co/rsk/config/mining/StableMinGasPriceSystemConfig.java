/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.config.mining;

import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import com.typesafe.config.Config;

import java.time.Duration;

public class StableMinGasPriceSystemConfig {
    public static final String STABLE_GAS_PRICE_CONFIG_PATH_PROPERTY = "miner.stableGasPrice";

    private static final String ENABLED_PROPERTY = "enabled";
    private static final String REFRESH_RATE_PROPERTY = "refreshRate";
    private static final String MIN_STABLE_GAS_PRICE_PROPERTY = "minStableGasPrice";
    private static final String METHOD_PROPERTY = "source.method";
    private static final String PARAMS_PROPERTY = "source.params";

    private static final String PARAM_MIN_VALID_PRICE = "minValidPrice";
    private static final String PARAM_MAX_VALID_PRICE = "maxValidPrice";

    private final Duration refreshRate;
    private final Long minStableGasPrice;
    private final boolean enabled;
    private final MinGasPriceProviderType method;
    private final Config config;

    public StableMinGasPriceSystemConfig(Config config) {
        this.enabled = config.hasPath(ENABLED_PROPERTY) && config.getBoolean(ENABLED_PROPERTY);
        this.refreshRate = this.enabled && config.hasPath(REFRESH_RATE_PROPERTY) ? config.getDuration(REFRESH_RATE_PROPERTY) : Duration.ZERO;
        this.minStableGasPrice = this.enabled && config.hasPath(MIN_STABLE_GAS_PRICE_PROPERTY) ? config.getLong(MIN_STABLE_GAS_PRICE_PROPERTY) : 0;
        this.method = this.enabled ? config.getEnum(MinGasPriceProviderType.class, METHOD_PROPERTY) : MinGasPriceProviderType.FIXED;
        this.config = config;
    }

    public Duration getRefreshRate() {
        return refreshRate;
    }

    public Long getMinStableGasPrice() {
        return minStableGasPrice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public HttpGetStableMinGasSystemConfig getHttpGetConfig() {
        return new HttpGetStableMinGasSystemConfig(config.getConfig(PARAMS_PROPERTY));
    }

    public EthCallMinGasPriceSystemConfig getEthCallConfig() {
        return new EthCallMinGasPriceSystemConfig(config.getConfig(PARAMS_PROPERTY));
    }

    public MinGasPriceProviderType getMethod() {
        return method;
    }

    public Long getMinValidPrice() {
        return config.hasPath(PARAM_MIN_VALID_PRICE) ? config.getLong(PARAM_MIN_VALID_PRICE) : 0;
    }

    public Long getMaxValidPrice() {
        return config.hasPath(PARAM_MAX_VALID_PRICE) ? config.getLong(PARAM_MAX_VALID_PRICE) : Long.MAX_VALUE;
    }

}
