package co.rsk.net.notifications.panics;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/***
 * Represents a current panic status, which is a set of
 * {@link PanicFlag} instances.
 *
 * @author Ariel Mendelzon
 *
 */
public class PanicStatus {
    final Set<PanicFlag> flags;

    public PanicStatus() {
        flags = new HashSet<>();
    }

    public void set(PanicFlag flag) {
        flags.add(flag);
    }

    public void unset(PanicFlag flag) {
        flags.remove(flag);
    }

    public void clear() {
        flags.clear();
    }

    public Set<PanicFlag> getFlags() {
        // Return a copy
        return new HashSet<>(flags);
    }

    public boolean has(PanicFlag.Reason reason) {
        return flags.contains(PanicFlag.of(reason));
    }

    public PanicFlag get(PanicFlag.Reason reason) {
        if (!has(reason)) {
            throw new IllegalArgumentException(String.format("No panic flag of reason %s set", reason));
        }

        return flags.stream().filter(f -> f.getReason().equals(reason)).findFirst().get();
    }

    public boolean inPanic() {
        return !flags.isEmpty();
    }

    @Override
    public String toString() {
        String commaSeparatedFlags = flags.stream().map(f -> f.toString()).collect(Collectors.joining(", "));
        return String.format("[%s]", commaSeparatedFlags);
    }
}
