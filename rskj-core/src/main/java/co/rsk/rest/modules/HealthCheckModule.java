/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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
package co.rsk.rest.modules;

import co.rsk.rest.RestUtils;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;

public class HealthCheckModule extends RestModule {

    public HealthCheckModule(String uri, boolean active) {
        super(uri, active);
    }

    @Override
    public DefaultFullHttpResponse processRequest(String uri, HttpMethod method) {
        if (uri.endsWith("/ping") && method.equals(HttpMethod.GET)) {
            return RestUtils.createResponse("pong");
        }

        return null;
    }

}
