package co.rsk.cli;

import java.util.*;
import java.util.stream.Collectors;

public class CliArgsParser<O extends Enum<O> & OptionalizableArgument, F extends Enum<F>> {

    private final List<String> arguments;
    private final Map<O, String> options;
    private final Set<F> flags;

    public CliArgsParser(String[] args, ArgByNameProvider<O> optionsProvider, ArgByNameProvider<F> flagsProvider) {
        arguments = new LinkedList<>();
        options = new HashMap<>();
        flags = new HashSet<>();

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
                            throw new IllegalArgumentException("A value must be provided after the option -" + args[i]);
                        }
                        options.put(optionsProvider.byName(args[i].substring(1, args[i].length())), args[i+1]);
                        i++;
                    }
                    break;
                default:
                    arguments.add(args[i]);
                    break;
            }
        }

        Set<O> missingOptions = optionsProvider.values().stream().filter(arg -> !arg.isOptional()).collect(Collectors.toSet());
        missingOptions.removeAll(options.keySet());
        if (!missingOptions.isEmpty()) {
            throw new IllegalArgumentException(String.format("Missing configuration options: %s", missingOptions));
        }

    }

    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public Map<O, String> getOptions() {
        return Collections.unmodifiableMap(options);
    }

    public Set<F> getFlags() {
        return Collections.unmodifiableSet(flags);
    }

    public interface ArgByNameProvider<E extends Enum<E>> {
        E byName(String name);

        Collection<E> values();
    }
}
