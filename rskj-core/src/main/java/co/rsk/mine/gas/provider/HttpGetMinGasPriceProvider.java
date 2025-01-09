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
package co.rsk.mine.gas.provider;

import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.config.mining.HttpGetStableMinGasSystemConfig;
import co.rsk.net.http.SimpleHttpClient;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class HttpGetMinGasPriceProvider extends StableMinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger("GasPriceProvider");
    private final String url;
    private final JsonPointer jsonPath;
    private final SimpleHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpGetMinGasPriceProvider(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallBackProvider) {
        this(config, fallBackProvider, new SimpleHttpClient(config.getHttpGetConfig().getTimeout().toMillis()));
    }

    public HttpGetMinGasPriceProvider(StableMinGasPriceSystemConfig config, MinGasPriceProvider fallBackProvider, SimpleHttpClient httpClient) {
        super(fallBackProvider, config.getMinStableGasPrice(), config.getRefreshRate(), config.getMinValidPrice(), config.getMaxValidPrice());
        HttpGetStableMinGasSystemConfig httpGetConfig = config.getHttpGetConfig();
        this.url = httpGetConfig.getUrl();
        this.jsonPath = JsonPointer.valueOf(httpGetConfig.getJsonPath());
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected Optional<Long> getBtcExchangeRate() {
        String response = getResponseFromWeb();
        if (!StringUtils.isBlank(response)) {
            Long price = parsePrice(response);
            return Optional.ofNullable(price);
        }
        return Optional.empty();
    }

    private Long parsePrice(String response) {
        try {
            JsonNode jObject = objectMapper.readTree(response);
            if (jObject.at(jsonPath).isMissingNode()) {
                return null;
            }
            return objectMapper.readTree(response).at(jsonPath).asLong();
        } catch (Exception e) {
            logger.error("Error parsing min gas price from web provider", e);
            return null;
        }
    }

    private String getResponseFromWeb() {
        try {
            return httpClient.doGet(url);
        } catch (Exception e) {
            logger.error("Error getting min gas price from web provider: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.HTTP_GET;
    }

    public String getUrl() {
        return url;
    }

}

