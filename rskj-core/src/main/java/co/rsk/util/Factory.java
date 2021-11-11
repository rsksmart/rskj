package co.rsk.util;

/**
 * General interface that helps in creation of instances of type {@link T}
 */
@FunctionalInterface
public interface Factory<T> {
    T create();
}
