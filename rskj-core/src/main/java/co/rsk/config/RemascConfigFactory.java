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

import co.rsk.remasc.RemascException;
import co.rsk.util.ConfigFileLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Created by mario on 12/12/16.
 */
public class RemascConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger("RemascConfigFactory");

    private final ObjectMapper mapper;
    private final String configPath;

    private static final ConfigFileLoader.ResourceLoader RESOURCE_LOADER = RemascConfigFactory.class.getClassLoader()::getResourceAsStream;

    public RemascConfigFactory(String remascConfigFile) {
        this.mapper = new ObjectMapper();
        this.configPath = remascConfigFile;
    }

    public RemascConfig createRemascConfig(String config) {
        RemascConfig remascConfig;

        // configPath is actually currently a filename. The field name does not help.
        try (InputStream is = ConfigFileLoader.loadConfigurationFile(this.configPath, RESOURCE_LOADER, ConfigFileLoader.ConfigRemap.REMASC)) {
            JsonNode node = mapper.readTree(is);
            remascConfig = mapper.treeToValue(node.get(config), RemascConfig.class);
        } catch (Exception ex) {
            logger.error("Error reading REMASC configuration[{}]: {}", config, ex);
            throw new RemascException("Error reading REMASC configuration[" + config + "]: ", ex);
        }

        return remascConfig;
    }
}
