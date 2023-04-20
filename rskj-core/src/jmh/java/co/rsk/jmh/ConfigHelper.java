/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigHelper {

    private ConfigHelper() {
    }

    public static Properties build(String network) throws IllegalStateException {
        InputStream inputStream = ConfigHelper.class.getClassLoader().getResourceAsStream("conf/" + network + ".conf");
        if (inputStream == null) {
            throw new IllegalStateException("Config not found for network: " + network);
        }

        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load config for network: " + network);
        }

        return props;
    }

}
