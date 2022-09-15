/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk;

import com.google.common.annotations.VisibleForTesting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Injector {

    private static Injector instance;

    private final RskContext rskContext;

    private final Map<Class<?>, Object> dependencies;

    private Injector(RskContext rskContext) {
        if (rskContext == null) {
            throw new IllegalStateException("RskContext must not be null");
        }
        this.rskContext = rskContext;
        this.dependencies = new HashMap<>();
    }

    /**
     * Creates a singleton instance for the Injector.<br>
     * Should be called whenever {@code RskContext} gets instantiated.
     *
     * @param rskContext {@code RskContext} to get dependencies from
     */
    public static void init(RskContext rskContext) {
        if (instance != null) {
            throw new IllegalStateException("Already initialised Injector");
        }

        instance = new Injector(rskContext);
    }

    /**
     * Closes the Injector instance, dependencies can no longer be retrieved after this method gets called.<br>
     * Should be called when {@code RskContext} gets closed.
     */
    public static void close() {
        instance = null;
    }

    /**
     * Gets an implementation of the requested {@code clazz} from {@code RskContext} defined singletons.
     * Every retrieved dependency is stored on a cache for future requests.<br><br>
     * init() should be called before calling this method
     *
     * @param clazz Interface or Class for which to get a singleton instance from {@code RskContext}
     *
     * @return An instance of the requested {@code clazz}
     *
     * @throws IllegalStateException if no singleton instance in {@code RskContext} matches requested {@code clazz} or
     * any error occurs searching it
     */
    public static <T> T getService(Class<T> clazz) {
        checkInitialised();

        T dep = (T) instance.dependencies.get(clazz);
        if (dep != null) {
            return dep;
        }

        for (Method method : instance.rskContext.getClass().getMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.getReturnType().isAssignableFrom(clazz)) {
                try {
                    // can return null, but that's ok
                    dep = (T) method.invoke(instance.rskContext);
                    instance.dependencies.put(clazz, dep);
                    return dep;
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw new IllegalStateException(getErrorMessage(clazz.toString(), "error"), e);
                }
            }
        }

        throw new IllegalStateException(getErrorMessage(clazz.toString(), "not found"));
    }

    /**
     * This method is for testing purposes, and it should only be called from tests.<br>
     * It forces a dependency to be stored in the injector cache, regardless it was already contained or not
     * and regardless it exists on {@code RskContext}.<br>
     * It will be the dependency returned by the injector after this call.
     *
     * @param clazz Interface or Class for which to store the given dependency {@code dependency}
     * @param dependency Implementation of {@code clazz} to store into the injector's cache
     */
    @VisibleForTesting
    public static <T> void forceDependency(Class<T> clazz, T dependency) {
        checkInitialised();
        instance.dependencies.put(clazz, dependency);
    }

    private static void checkInitialised() {
        if (instance == null) {
            throw new IllegalStateException("Injector not yet initialized");
        }
    }

    private static String getErrorMessage(String className, String extra) {
        String additional = extra != null ? String.format(",%s", extra) : "";
        return String.format("Could not get dependency %s%s", className, additional);
    }

}
