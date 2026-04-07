/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by ajlopez on 06/10/2017.
 */
public class OriginValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger("jsonrpc");

    private URI[] origins;
    private boolean allowAllOrigins;

    public OriginValidator() {
        this.origins = new URI[0];
    }

    public OriginValidator(String uriList) {
        if (uriList == null) {
            this.origins = new URI[0];
        } else if ("*".equals(uriList.trim())) {
            this.allowAllOrigins = true;
        } else {
            try {
                this.origins = toUris(uriList);
            } catch (URISyntaxException e) {
                LOGGER.error("Error creating OriginValidator, origins {}, {}", uriList, e);

                // no origin
                this.origins = new URI[0];
            }
        }
    }

    public boolean isValidOrigin(String origin) {
        if (this.allowAllOrigins) {
            return true;
        }

        URI originUri = null;

        try {
            originUri = new URI(origin);
        } catch (URISyntaxException e) {
            return false;
        }

        for (URI uri : origins) {
            if (originUri.equals(uri)) {
                return true;
            }
        }

        return false;
    }

    public boolean isValidReferer(String referer) {
        if (this.allowAllOrigins) {
            return true;
        }

        URL refererUrl = null;

        try {
            refererUrl = new URL(referer);
        } catch (MalformedURLException e) {
            return false;
        }

        String refererProtocol = refererUrl.getProtocol();

        if (refererProtocol == null) {
            return false;
        }

        String refererHost = refererUrl.getHost();

        if (refererHost == null) {
            return false;
        }

        int refererPort = refererUrl.getPort();

        for (int k = 0; k < origins.length; k++) {
            if (refererProtocol.equals(origins[k].getScheme()) &&
                    refererHost.equals(origins[k].getHost()) &&
                    refererPort == origins[k].getPort()) {
                return true;
            }
        }

        return false;
    }

    private static URI[] toUris(@Nonnull String list) throws URISyntaxException {
        String[] elements = list.split(" ");
        URI[] uris = new URI[elements.length];

        for (int k = 0; k < elements.length; k++) {
            uris[k] = new URI(elements[k].trim());
        }

        return uris;
    }
}
