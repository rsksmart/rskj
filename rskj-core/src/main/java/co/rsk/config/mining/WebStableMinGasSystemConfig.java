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

import com.typesafe.config.Config;

public class WebStableMinGasSystemConfig {
    public static final String WEB_STABLE_GAS_PRICE_CONFIG_PATH = "web";
    private static final String URL_PROPERTY = "url";
    private static final String REQUEST_PATH = "requestPath";
    private static final String TIMEOUT_PROPERTY = "timeout";

    private final String url;
    private final String requestPath;
    private final int timeout;

    public WebStableMinGasSystemConfig(Config config) {
        this.url = config.getString(URL_PROPERTY);
        this.requestPath = config.getString(REQUEST_PATH);
        this.timeout = config.getInt(TIMEOUT_PROPERTY);
    }

    public String getUrl() {
        return url;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public int getTimeout() {
        return timeout;
    }

}
