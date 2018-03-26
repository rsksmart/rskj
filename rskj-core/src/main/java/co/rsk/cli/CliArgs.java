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
import java.util.stream.Collectors;

public class CliArgs<O, F> {

    private final List<String> arguments;
    private final Map<O, String> options;
    private final Set<F> flags;

    private CliArgs(List<String> arguments, Map<O, String> options, Set<F> flags) {
        this.arguments = Collections.unmodifiableList(arguments);
        this.options = Collections.unmodifiableMap(options);
        this.flags = Collections.unmodifiableSet(flags);
    }

    public List<String> getArguments() {
        return arguments;
    }

    public Map<O, String> getOptions() {
        return options;
    }

    public Set<F> getFlags() {
        return flags;
    }

    public static class Parser<O extends Enum<O> & OptionalizableArgument, F extends Enum<F>> {

        private final ArgByNameProvider<O> optionsProvider;
        private final ArgByNameProvider<F> flagsProvider;

        public Parser(ArgByNameProvider<O> optionsProvider, ArgByNameProvider<F> flagsProvider) {
            this.optionsProvider = optionsProvider;
            this.flagsProvider = flagsProvider;
        }

        public CliArgs<O, F> parse(String[] args) {
            List<String> arguments = new LinkedList<>();
            Map<O, String> options = new HashMap<>();
            Set<F> flags = new HashSet<>();

            for (int i = 0; i < args.length; i++) {
                switch (args[i].charAt(0)) {
                    case '-':
                        if (args[i].length() < 2) {
                            throw new IllegalArgumentException("You must provide an option name, e.g. -d");
                        }
                        if (args[i].charAt(1) == '-') {
                            if (args[i].length() < 3) {
                                throw new IllegalArgumentException("You must provide a flag name, e.g. --quiet");
                            }
                            flags.add(flagsProvider.byName(args[i].substring(2, args[i].length())));
                        } else {
                            if (args.length - 1 == i) {
                                throw new IllegalArgumentException(
                                        String.format("A value must be provided after the option -%s", args[i])
                                );
                            }
                            options.put(optionsProvider.byName(args[i].substring(1, args[i].length())), args[i + 1]);
                            i++;
                        }
                        break;
                    default:
                        arguments.add(args[i]);
                        break;
                }
            }

            Set<O> missingOptions = optionsProvider.values()
                    .stream()
                    .filter(arg -> !arg.isOptional())
                    .collect(Collectors.toSet());
            missingOptions.removeAll(options.keySet());
            if (!missingOptions.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Missing configuration options: %s", missingOptions)
                );
            }

            return new CliArgs<>(arguments, options, flags);
        }
    }

    public interface ArgByNameProvider<E extends Enum<E>> {
        E byName(String name);

        Collection<E> values();
    }
}
