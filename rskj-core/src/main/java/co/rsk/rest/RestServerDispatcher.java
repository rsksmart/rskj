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
package co.rsk.rest;

import co.rsk.rest.modules.RestModule;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RestServerDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(RestServerDispatcher.class);

    private final List<RestModule> moduleList;

    public RestServerDispatcher(List<RestModule> moduleList) {
        Objects.requireNonNull(moduleList, "Module List can not be null");
        this.moduleList = Collections.unmodifiableList(moduleList);
    }

    public DefaultFullHttpResponse dispatch(HttpRequest request) throws URISyntaxException {

        String uri = new URI(request.uri()).getPath();

        RestModule restModule = moduleList.stream()
                .filter(module -> uri.startsWith(module.getUri())).findFirst().orElse(null);

        if (restModule == null) {
            logger.debug("Handler Not Found.");
            return RestUtils.createNotFoundResponse();
        }

        if (restModule.isActive()) {
            logger.debug("Dispatching request.");
            DefaultFullHttpResponse response = restModule.processRequest(uri, request.method());

            if (response != null) {
                logger.debug("Returning response.");
                return response;
            }

            logger.debug("Request received but module could not process it.");
            return RestUtils.createNotFoundResponse();
        }

        logger.debug("Request received but module is disabled.");
        return RestUtils.createNotFoundResponse();

    }

}
