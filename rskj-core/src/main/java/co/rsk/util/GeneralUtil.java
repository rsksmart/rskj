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
package co.rsk.util;


import co.rsk.rpc.ModuleDescription;

import java.util.List;
import java.util.Optional;

public final class GeneralUtil {
    private GeneralUtil() {

    }

    public static int getTimeoutByModuleAndMethod(String method, int timeout, List<ModuleDescription> modules) {
        if (method.isEmpty()) {
            return timeout;
        }

        String[] methodParts = method.split("_");

        if (methodParts.length < 2) {
            return timeout;
        }

        String moduleName = methodParts[0];
        String methodName = methodParts[1];
        Optional<ModuleDescription> optModule = modules.stream()
                .filter(m -> m.getName().equals(moduleName) && m.getTimeout() > 0)
                .findFirst();

        Optional<Integer> optMethodTimeout = optModule.map(m -> m.getMethodTimeout(methodName));

        return optMethodTimeout.orElseGet(
                () -> optModule.map(ModuleDescription::getTimeout).orElse(timeout)
        );

    }
}
