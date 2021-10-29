/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk.rpc.modules.debug;

import java.util.*;

public class TraceOptions {

    private Set<String> disabledFields;
    private Set<String> unsupportedOptions;

    private TraceOptions() {}

    public TraceOptions(Map<String, String> traceOptions) {
        disabledFields = new HashSet<>();
        unsupportedOptions = new HashSet<>();
        if (traceOptions != null) {
            if (traceOptions.containsKey("disableStorage")) {
                if (Boolean.parseBoolean(traceOptions.get("disableStorage"))) {
                    disabledFields.add("storage");
                }
                traceOptions.remove("disableStorage");
            }
            if (traceOptions.containsKey("disableMemory")) {
                if (Boolean.parseBoolean(traceOptions.get("disableMemory"))) {
                    disabledFields.add("memory");
                }
                traceOptions.remove("disableMemory");
            }
            if (traceOptions.containsKey("disableStack")) {
                if (Boolean.parseBoolean(traceOptions.get("disableStack"))) {
                    disabledFields.add("stack");
                }
                traceOptions.remove("disableStack");
            }
            unsupportedOptions = traceOptions.keySet();
        }
    }

    public Set<String> getDisabledFields() {
        return disabledFields;
    }

    public Set<String> getUnsupportedOptions() {
        return unsupportedOptions;
    }

}
