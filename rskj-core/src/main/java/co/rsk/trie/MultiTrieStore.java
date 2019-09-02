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

package co.rsk.trie;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Implements TrieStore with multiple backing stores as specified on RSKIP64.
 * <p>
 * There will be generally 2 active databases at the same time for states, although when a new frontier block is being
 * executed or during the epoch migration process, which is fully asynchronous, the number of active databases can be up
 * to 3. This is done to allow garbage collection with no long-lived write locks.
 // TODO(mc) handle concurrency with a RW Lock
 */
public class MultiTrieStore implements TrieStore {
    private static final Logger logger = LoggerFactory.getLogger(MultiTrieStore.class);

    private final String databaseDir;

    private long currentEpoch;

    // Weak references are removed when the tries are garbage collected by the JVM
    private Set<Trie> epochNSavedTries = Collections.newSetFromMap(new WeakHashMap<>());
    private Set<Trie> epochN1SavedTries = Collections.newSetFromMap(new WeakHashMap<>());
    private Set<Trie> epochN2SavedTries = Collections.newSetFromMap(new WeakHashMap<>());

    private KeyValueDataSource epochN;
    private KeyValueDataSource epochN1;
    private KeyValueDataSource epochN2;

    public MultiTrieStore(String databaseDir, long currentEpoch) {
        this.databaseDir = databaseDir;
        openStores(currentEpoch);
    }

    /**
     * Recursively saves all unsaved nodes of this trie to d(N), unless they're found in d(N - 1).
     */
    @Override
    public synchronized void save(Trie trie) {
        save(trie, true);
    }

    /**
     * @param forceSaveRoot allows saving the root node even if it's embeddable
     */
    private void save(Trie trie, boolean forceSaveRoot) {
        // Invariant: a trie saved in epoch N is fully contained in d(N) and d(N - 1).
        // This ensures all epoch N tries survive after discarding d(N - 2).
        if (epochNSavedTries.contains(trie) || epochN1SavedTries.contains(trie)) {
            // TODO(mc): can we return? could its children be in d(N - 2)?
            return;
        }

        trie.getLeft().getNode().ifPresent(t -> save(t, false));
        trie.getRight().getNode().ifPresent(t -> save(t, false));

        if (trie.hasLongValue()) {
            epochN.put(trie.getValueHash().getBytes(), trie.getValue());
        }

        if (trie.isEmbeddable() && !forceSaveRoot) {
            return;
        }

        epochN.put(trie.getHash().getBytes(), trie.toMessage());
        epochNSavedTries.add(trie);
    }

    @Override
    public synchronized Trie retrieve(byte[] hash) {
        byte[] epochNData = epochN.get(hash);
        if (epochNData != null) {
            Trie trie = Trie.fromMessage(epochNData, this);
            epochNSavedTries.add(trie);
            return trie;
        }

        byte[] epochN1Data = epochN1.get(hash);
        if (epochN1Data != null) {
            Trie trie = Trie.fromMessage(epochN1Data, this);
            epochN1SavedTries.add(trie);
            return trie;
        }

        byte[] epochN2Data = epochN2.get(hash);
        if (epochN2Data != null) {
            Trie trie = Trie.fromMessage(epochN2Data, this);
            epochN2SavedTries.add(trie);
            return trie;
        }

        throw new IllegalArgumentException(String.format(
                "The trie with root %s is missing in this store", Hex.toHexString(hash)
        ));
    }

    @Override
    public synchronized byte[] retrieveValue(byte[] hash) {
        byte[] epochNData = epochN.get(hash);
        if (epochNData != null) {
            return epochNData;
        }

        byte[] epochN1Data = epochN1.get(hash);
        if (epochN1Data != null) {
            return epochN1Data;
        }

        return epochN2.get(hash);
    }

    @Override
    public synchronized void flush() {
        epochN.flush();
    }

    // TODO(mc) collect in the background
    @Override
    public synchronized void collect(Trie lastFrontierTrie, long lastEpoch) {
        // TODO(mc) could it be greater?
        if (lastEpoch < currentEpoch) {
            logger.warn("Skipping collection with epoch {} lower than current epoch {}", lastEpoch, currentEpoch);
            return;
        }

        // TODO(mc) is this necessary with the save invariant?
        ensureTrieForCollection(lastFrontierTrie, true);

        epochN.close();
        epochN1.close();
        epochN2.close();

        openStores(currentEpoch + 1);
        // TODO(mc) delete old databases

        epochN2SavedTries = epochN1SavedTries;
        epochN1SavedTries = epochNSavedTries;
        epochNSavedTries = Collections.newSetFromMap(new WeakHashMap<>());
    }

    // TODO(mc) deduplicate
    private void ensureTrieForCollection(Trie trie, boolean forceSaveRoot) {
        trie.getLeft().getNode().ifPresent(t -> ensureTrieForCollection(t, false));
        trie.getRight().getNode().ifPresent(t -> ensureTrieForCollection(t, false));

        if (trie.hasLongValue()) {
            epochN1.put(trie.getValueHash().getBytes(), trie.getValue());
        }

        if (trie.isEmbeddable() && !forceSaveRoot) {
            return;
        }

        epochN1.put(trie.getHash().getBytes(), trie.toMessage());
        epochN1SavedTries.add(trie);
    }

    // TODO(mc): handle negative values
    private void openStores(long currentEpoch) {
        logger.info("Opening unitrie stores for epoch {}", currentEpoch);

        this.epochN = new LevelDbDataSource("unitrie_" + currentEpoch, databaseDir);
        this.epochN1 = new LevelDbDataSource("unitrie_" + (currentEpoch - 1), databaseDir);
        this.epochN2 = new LevelDbDataSource("unitrie_" + (currentEpoch - 2), databaseDir);
        this.epochN.init();
        this.epochN1.init();
        this.epochN2.init();

        this.currentEpoch = currentEpoch;
    }
}
