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

import co.rsk.rest.dto.RestModuleConfigDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RestModuleLoader {

    private final List<RestModule> restModuleList;

    public RestModuleLoader(RestModuleConfigDTO restModuleConfigDTO) {
        Objects.requireNonNull(restModuleConfigDTO, "REST Module Config can not be null");

        restModuleList = new ArrayList<>();

        restModuleList.add(new HealthCheckModule("/health-check",
                restModuleConfigDTO.isHealthCheckModuleEnabled()));
    }

    public List<RestModule> getRestModules() {
        return Collections.unmodifiableList(restModuleList);
    }

}
