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
import java.util.Optional;
import java.util.Properties;

public class Config {

    private final Properties properties;

    private Config(Properties properties) {
        this.properties = (Properties) properties.clone();
    }

    public static Config create(String name) throws IllegalStateException {
        InputStream inputStream = Config.class.getClassLoader().getResourceAsStream("conf/" + name + ".conf");
        if (inputStream == null) {
            throw new IllegalStateException("Config not found for name: " + name);
        }

        Properties props = new Properties();
        try {
            props.load(inputStream);
        } catch (IOException e) { // NOSONAR
            throw new IllegalStateException("Could not load config for name: " + name);
        }

        return new Config(props);
    }

    public String getNullableProperty(String key) {
        return Optional.ofNullable(properties.getProperty(key)).filter(p -> !"null".equals(p)).orElse(null);
    }

    public String getString(String name) {
        return Optional.ofNullable(properties.getProperty(name)).orElseThrow(() -> missingPropertyException(name));
    }

    public Integer getInt(String name) {
        return Optional.ofNullable(properties.getProperty(name)).map(Integer::parseInt).orElseThrow(() -> missingPropertyException(name));
    }

    public Long getLong(String name) {
        return Optional.ofNullable(properties.getProperty(name)).map(Long::parseLong).orElseThrow(() -> missingPropertyException(name));
    }

    private IllegalStateException missingPropertyException(String name) {
        return new IllegalStateException("Property " + name + " not found in config");
    }

}
