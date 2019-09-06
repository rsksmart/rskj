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

import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiTrieStore implements TrieStore {

    private int currentEpoch;
    private final int numberOfEpochs;
    private final List<TrieStore> epochs;
    private final TrieStoreFactory trieStoreFactory;
    private final OnEpochDispose disposer;

    public MultiTrieStore(int currentEpoch, int numberOfEpochs, TrieStoreFactory trieStoreFactory, OnEpochDispose disposer) {
        this.currentEpoch = currentEpoch;
        this.numberOfEpochs = numberOfEpochs;
        this.trieStoreFactory = trieStoreFactory;
        this.disposer = disposer;
        this.epochs = new ArrayList<>(numberOfEpochs);
        int minimumEpoch = Math.max(0, currentEpoch - numberOfEpochs);
        for (int i = currentEpoch; i >= minimumEpoch; i--) {
            epochs.add(trieStoreFactory.newInstance(String.valueOf(i)));
        }
    }

    /**
     * This will save to the current store <b>only</b> the new nodes b/c if there is some child in an older
     * epoch, it'll be reached by the {@link #retrieve(byte[]) retrieve} method breaking the recursion
     *
     * @param trie: a {@link Trie} obtained from a {@link MultiTrieStore}
     */
    @Override
    public void save(Trie trie) {
        getCurrentStore().save(trie);
    }

    /**
     * It's not enough to just flush the current one b/c it may occur, if the epoch size doesn't match the flush size,
     * that some epoch may never get flushed
     */
    @Override
    public void flush() {
        epochs.forEach(TrieStore::flush);
    }

    /**
     * This method will go through all epochs from newest to oldest retrieving the <code>rootHash</code>
     *
     * @param rootHash: the root of the {@link Trie} to retrieve
     * @return the {@link Trie} with <code>rootHash</code>
     * @throws IllegalArgumentException if it's not found
     */
    @Override
    public Trie retrieve(byte[] rootHash) {
        for (TrieStore epochTrieStore : epochs) {
            byte[] message = epochTrieStore.retrieveValue(rootHash);
            if (message == null) {
                continue;
            }
            return Trie.fromMessage(message, this);
        }
        throw new IllegalArgumentException(String.format(
                "The trie with root %s is missing from every epoch", Hex.toHexString(rootHash)
        ));
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        for (TrieStore epochTrieStore : epochs) {
            byte[] value = epochTrieStore.retrieveValue(hash);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        epochs.forEach(TrieStore::dispose);
    }

    /**
     * Discards the oldest epoch.
     *
     * The children of <code>oldestTrieHashToKeep</code> stored in the oldest epoch will be saved
     * into the previous one
     *
     * @param oldestTrieHashToKeep a trie root hash to ensure epoch survival
     */
    public void collect(byte[] oldestTrieHashToKeep) {
        currentEpoch++;
        if (currentEpoch < numberOfEpochs) {
            epochs.add(0, trieStoreFactory.newInstance(String.valueOf(currentEpoch)));
        } else {
            Trie oldestTrieToKeep = retrieve(oldestTrieHashToKeep);
            epochs.get(numberOfEpochs - 2).save(oldestTrieToKeep); // save into the upcoming last epoch
            epochs.get(numberOfEpochs - 1).dispose(); // dispose last epoch
            disposer.callback(currentEpoch - numberOfEpochs);
            Collections.rotate(epochs, 1); // move last epoch to first place
            epochs.set(0, trieStoreFactory.newInstance(String.valueOf(currentEpoch))); // update current epoch
        }
    }

    private TrieStore getCurrentStore() {
        return epochs.get(0);
    }

    public interface OnEpochDispose {
        void callback(int disposedEpoch);
    }
}
