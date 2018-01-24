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

package co.rsk.config;

import com.typesafe.config.Config;

/**
 * Represents the configuration for a signer.
 * Mainly has an identifier for the signer, the
 * type of signer and additional configuration options.
 *
 * @author Ariel Mendelzon
 */
public class SignerConfig {
    private final String id;
    private final String type;
    private final Config config;

    public SignerConfig(String id, Config config) {
        this.id = id;
        this.type = config.getString("type");
        this.config = config.withoutPath("type");
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Config getConfig() {
        return config;
    }
}
