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
package co.rsk.cli;

import java.util.*;

/**
 * A simple representation of command line arguments, broken into "options", "flags" and "arguments".
 */
public class CliArgs<O, F> {

    private final Map<O, String> options;
    private final Set<F> flags;
    private final Map<String, String> paramValueMap;

    public static <O, F> CliArgs<O, F> of(Map<O, String> options, Set<F> flags, Map<String, String> paramValueMap) {
        return new CliArgs<>(options, flags, paramValueMap);
    }
    private CliArgs(Map<O, String> options, Set<F> flags, Map<String, String> paramValueMap) {
        this.options = Collections.unmodifiableMap(options);
        this.flags = Collections.unmodifiableSet(flags);
        this.paramValueMap = Collections.unmodifiableMap(paramValueMap);
    }

    public static <O, F> CliArgs<O, F> empty() {
        return new CliArgs<>(
                Collections.emptyMap(),
                Collections.emptySet(),
                Collections.emptyMap()
        );
    }

    public Map<O, String> getOptions() {
        return options;
    }

    public Set<F> getFlags() {
        return flags;
    }

    public Map<String, String> getParamValueMap() {
        return paramValueMap;
    }
}
