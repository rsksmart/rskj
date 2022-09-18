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
import com.fasterxml.jackson.databind.JsonMappingException;
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

    private ObjectMapper mapper;
    private String configPath;

    public RemascConfigFactory(String remascConfigFile) {
        this.mapper = new ObjectMapper();
        this.configPath = remascConfigFile;
    }

    public RemascConfig createRemascConfig(String config) {
        RemascConfig remascConfig;

        try (InputStream is = RemascConfigFactory.class.getClassLoader().getResourceAsStream(this.configPath)) {
            JsonNode node = mapper.readTree(is);

            if (node.isEmpty()) {
                throw JsonMappingException.from(node.traverse(), "Json node is empty");
            }

            remascConfig = mapper.treeToValue(node.get(config), RemascConfig.class);

            if (remascConfig == null) {
                throw new NullPointerException();
            }
        } catch (Exception ex) {
            logger.error("Error reading REMASC configuration[{}]: {}", config, ex);
            throw new RemascException("Error reading REMASC configuration[" + config + "]: ", ex);
        }

        return remascConfig;
    }
}
