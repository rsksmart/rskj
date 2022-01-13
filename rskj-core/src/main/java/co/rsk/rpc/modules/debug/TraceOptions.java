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
import java.util.stream.Collectors;

public class TraceOptions {

    private final List<String> supportedOptions;
    private final Set<String> disabledFields;
    private final Set<String> unsupportedOptions;

    public TraceOptions() {
        supportedOptions = Arrays.stream(DisableOption.values()).map(option -> option.option)
                .collect(Collectors.toList());

        this.disabledFields = new HashSet<>();
        this.unsupportedOptions = new HashSet<>();
    }

    public TraceOptions(Map<String, String> traceOptions) {
        this();

        if (traceOptions == null || traceOptions.isEmpty()) return;

        // Disabled Fields Parsing

        for (DisableOption disableOption : DisableOption.values()) {
            if (Boolean.parseBoolean(traceOptions.get(disableOption.option))) {
                this.disabledFields.add(disableOption.value);
            }
        }

        // Unsupported Options

        traceOptions.keySet()
                .stream()
                .filter(key -> supportedOptions.stream().noneMatch(option -> option.equals(key)))
                .forEach(unsupportedOptions::add);
    }

    public Set<String> getDisabledFields() {
        return Collections.unmodifiableSet(disabledFields);
    }

    public Set<String> getUnsupportedOptions() {
        return Collections.unmodifiableSet(unsupportedOptions);
    }

}
