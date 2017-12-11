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

/**
 * Created by ajlopez on 20/04/2017.
 */
public class CorsConfiguration {
    private static final Logger logger = LoggerFactory.getLogger("cors");
    private final String header;

    public CorsConfiguration(String header) {
        this.header = header;

        if ("*".equals(header)) {
            logger.warn("CORS header set to '*'");
        }

        if (header != null && (header.contains("\n") || header.contains("\r"))) {
            throw new IllegalArgumentException("corsheader");
        }
    }

    public String getHeader() {
        return this.header;
    }

    public boolean hasHeader() {
        return this.header != null && this.header.length() != 0;
    }
}
