/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.trie;

import javax.annotation.Nullable;

/**
 * The trie key currently being walked, for diagnostics.
 *
 * <p>A {@link TrieStore} is asked for nodes by hash and never sees the key whose lookup caused the
 * traversal. That is usually fine, but when a store cannot serve a node, the hash on its own does
 * not say what was being looked for, and the key is what makes the failure actionable. This carries
 * it down the one level that separates them.
 *
 * <p>Diagnostic only: nothing depends on it being set, and a store that ignores it behaves exactly
 * as before.
 */
public final class TrieReadContext {

    private static final ThreadLocal<byte[]> CURRENT_KEY = new ThreadLocal<>();

    private TrieReadContext() { /* hidden */ }

    /** Records the key being looked up for the duration of {@code lookup}. */
    public static <T> T walking(byte[] key, java.util.function.Supplier<T> lookup) {
        byte[] previous = CURRENT_KEY.get();
        CURRENT_KEY.set(key);
        try {
            return lookup.get();
        } finally {
            if (previous == null) {
                CURRENT_KEY.remove();
            } else {
                CURRENT_KEY.set(previous);
            }
        }
    }

    /** The key currently being walked, or null if nothing recorded one. */
    @Nullable
    public static byte[] currentKey() {
        return CURRENT_KEY.get();
    }
}
