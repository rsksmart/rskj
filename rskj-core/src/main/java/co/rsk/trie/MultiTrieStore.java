/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MultiTrieStore implements TrieStore {
    private static final Logger logger = LoggerFactory.getLogger("multitriestore");

    private int currentEpoch;
    private final List<TrieStore> epochs;
    private final TrieStoreFactory trieStoreFactory;
    private final OnEpochDispose disposer;

    // To prevent entrance when executing discardOldEpoch()
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * Creates a MultiTrieStore
     * @param currentEpoch number (>= 0) of epoch to begin
     * @param liveEpochs number (>= 0) of concurrent living epochs
     * @param trieStoreFactory a trie store factory
     * @param disposer callback for when an epoch gets disposed
     */
    public MultiTrieStore(int currentEpoch, int liveEpochs, TrieStoreFactory trieStoreFactory, OnEpochDispose disposer) {
        // if currentEpoch < liveEpochs the store for it will be in the expected index in the epochs list
        this.currentEpoch = Math.max(currentEpoch, liveEpochs);
        this.trieStoreFactory = trieStoreFactory;
        this.disposer = disposer;
        this.epochs = new ArrayList<>(liveEpochs);
        for (int i = 1; i <= liveEpochs; i++) { // starting in 1 so it's easier to calculate epoch according index
            epochs.add(trieStoreFactory.newInstance(String.valueOf(this.currentEpoch - i)));
        }
    }

    /**
     * This will save to the current store <b>only</b> the new nodes b/c if there is some child in an older
     * epoch, it'll be reached by the {@link #retrieve(byte[]) retrieve} method breaking the recursion
     *
     * @param trie a {@link Trie} obtained from a {@link MultiTrieStore}
     */
    @Override
    public void save(Trie trie) {
        readWriteLock.readLock().lock();

        try {
            getCurrentStore().save(trie);
        }
        finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * It's not enough to just flush the current one b/c it may occur, if the epoch size doesn't match the flush size,
     * that some epoch may never get flushed
     */
    @Override
    public void flush() {
        logger.trace("Start MultiTrie flush");

        readWriteLock.writeLock().lock();

        try {
            epochs.forEach(TrieStore::flush);

            logger.trace("End MultiTrie flush");
        }
        finally {
            readWriteLock.writeLock().unlock();
        }
    }


    /**
     * This method will go through all epochs from newest to oldest retrieving the <code>rootHash</code>
     */
    @Override
    public Optional<Trie> retrieve(byte[] rootHash) {
        readWriteLock.readLock().lock();

        try {
            for (TrieStore epochTrieStore : epochs) {
                byte[] message = epochTrieStore.retrieveValue(rootHash);

                if (message == null) {
                    continue;
                }

                return Optional.of(Trie.fromMessage(message, this));
            }

            return Optional.empty();
        }
        finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        readWriteLock.readLock().lock();

        try {
            for (TrieStore epochTrieStore : epochs) {
                byte[] value = epochTrieStore.retrieveValue(hash);

                if (value != null) {
                    return value;
                }
            }

            return null;
        }
        finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public void dispose() {
        logger.trace("Start MultiTrie dispose");

        readWriteLock.writeLock().lock();

        try {
            epochs.forEach(TrieStore::dispose);

            logger.trace("End MultiTrie dispose");
        }
        finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * Save the oldest trie hash to keep into the previous to oldest epoch.
     *
     * The children of <code>oldestTrieHashToKeep</code> stored in the oldest epoch will be saved
     * into the previous one
     *
     * @param oldestTrieHashToKeep a trie root hash to ensure epoch survival
     */
    public void collect(byte[] oldestTrieHashToKeep) {
        logger.trace("Start MultiTrie collect");

        readWriteLock.readLock().lock();

        try {
            Trie oldestTrieToKeep = retrieve(oldestTrieHashToKeep)
                    .orElseThrow(() ->
                            new IllegalArgumentException(String.format("The trie with root %s is missing from every epoch",
                                    ByteUtil.toHexString(oldestTrieHashToKeep)
            )));

            epochs.get(epochs.size() - 2).save(oldestTrieToKeep); // save into the upcoming last epoch

            logger.trace("End MultiTrie collect");
        }
        finally {
            readWriteLock.readLock().unlock();
        }
    }

    /**
     * Discards the oldest epoch.
     *
     */
    public void discardOldestEpoch() {
        logger.trace("Start MultiTrie discard oldest epoch");

        readWriteLock.writeLock().lock();

        try {
            epochs.get(epochs.size() - 1).dispose(); // dispose last epoch
            disposer.callback(currentEpoch - epochs.size());
            Collections.rotate(epochs, 1); // move last epoch to first place
            epochs.set(0, trieStoreFactory.newInstance(String.valueOf(currentEpoch))); // update current epoch
            currentEpoch++;

            logger.trace("End MultiTrie discard oldest epoch");
        }
        finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private TrieStore getCurrentStore() {
        readWriteLock.readLock().lock();

        try {
            return epochs.get(0);
        }
        finally {
            readWriteLock.readLock().unlock();
        }
    }

    public interface OnEpochDispose {
        void callback(int disposedEpoch);
    }
}
