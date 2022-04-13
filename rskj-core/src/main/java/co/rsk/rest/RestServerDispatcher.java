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
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RestServerDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(RestServerDispatcher.class);

    private static final String NOT_FOUND = "Not Found";

    private final List<RestModule> moduleList;

    public RestServerDispatcher(List<RestModule> moduleList) {
        Objects.requireNonNull(moduleList, "Module List can not be null");
        this.moduleList = Collections.unmodifiableList(moduleList);
    }

    public DefaultFullHttpResponse dispatch(HttpRequest request) throws URISyntaxException {

        String uri = new URI(request.getUri()).getPath();

        RestModule restModule = moduleList.stream()
                .filter(module -> uri.startsWith(module.getUri())).findFirst().orElse(null);

        if (restModule == null) {
            logger.info("Handler Not Found.");
            return RestUtils.createResponse(NOT_FOUND, HttpResponseStatus.NOT_FOUND);
        }

        if (restModule.isActive()) {
            logger.info("Dispatching request.");
            DefaultFullHttpResponse response = restModule.processRequest(uri, request.getMethod());

            if (response != null) {
                logger.info("Returning response.");
                return response;
            }

            logger.info("Request received but module could not process it.");
            return RestUtils.createResponse(NOT_FOUND, HttpResponseStatus.NOT_FOUND);
        }

        logger.info("Request received but module is disabled.");
        return RestUtils.createResponse(NOT_FOUND, HttpResponseStatus.NOT_FOUND);

    }

}
