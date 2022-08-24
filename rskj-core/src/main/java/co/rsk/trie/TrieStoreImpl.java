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

import co.rsk.db.StateRootsStore;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.KeyValueDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TrieStoreImpl store and retrieve Trie node by hash
 *
 * It saves/retrieves the serialized form (byte array) of a Trie node
 *
 * Internally, it uses a key value data source
 *
 * Created by ajlopez on 08/01/2017.
 */
public class TrieStoreImpl implements TrieStore {

    private static final Logger logger = LoggerFactory.getLogger("triestore");
    public static boolean forcePerformanceLogging = false;
    private static final ThreadLocal<TraceInfo> traceInfoLocal = ThreadLocal.withInitial(TraceInfo::new);

    private final KeyValueDataSource store;
    KeyValueDataSource rootsDataSource;

    public TrieStoreImpl(KeyValueDataSource store) {
        this.store = store;
        this.rootsDataSource = null;
    }

    public TrieStoreImpl(KeyValueDataSource store, StateRootsStore rootStore) {
        this.store = store;
        this.rootsDataSource = rootsDataSource;
    }

    /**
     * Recursively saves all unsaved nodes of this trie to the underlying key-value store
     */
    @Override
    public void save(Trie trie) {
        TraceInfo traceInfo = null;
        SaveInfo saveInfo = null;

        if ((forcePerformanceLogging) || (logger.isTraceEnabled())) {
            traceInfo = traceInfoLocal.get();
            saveInfo = new SaveInfo();
            traceInfo.numOfTopLevelSaves++;
        }

        if ((logger.isTraceEnabled())) {
            logger.trace("Start saving trie root.");
        }

        // save a trie recursively
        save(trie, true, 0, traceInfo,saveInfo);

        if (saveInfo != null) {
            logger.trace("End saving trie root. No. Saves: {}. No. No Saves: {}",
                    saveInfo.numOfSaves, saveInfo.numOfNoSaves);

            if (store instanceof DataSourceWithCache) {
                ((DataSourceWithCache) store).emitLogs();
            }
        }

        if (rootsDataSource!=null) {
            rootsDataSource.put(trie.getHash().getBytes(),nodePresent);
        }
    }

    static byte[] nodePresent = new byte[]{1};

    /**
     * @param isRootNode it is the root node of the trie
     */
    int cc =0;
    private void save(Trie trie, boolean isRootNode, int level, @Nullable TraceInfo traceInfo, @Nullable SaveInfo saveInfo) {
        if (trie.wasSaved()) {
            return;
        }

        logger.trace("Start saving trie, level : {}", level);

        byte[] trieKeyBytes = trie.getHash().getBytes();

        if (isRootNode && this.store.get(trieKeyBytes) != null) {
            // the full trie is already saved
            logger.trace("End saving trie, level : {}, already saved.", level);

            if (traceInfo != null) {
                traceInfo.numOfNoSaves++;
            }
            if (saveInfo != null) {
                saveInfo.numOfNoSaves++;
            }

            return;
        }

        if (traceInfo != null) {
            traceInfo.numOfSaves++;
        }
        if (saveInfo != null) {
            saveInfo.numOfSaves++;
        }

        // Save first the children to maintain trie database consistency
        // The last we write is the parent node.
        NodeReference leftNodeReference = trie.getLeft();

        if (leftNodeReference.wasLoaded()) {
            logger.trace("Start left trie. Level: {}", level);
            leftNodeReference.getNode().ifPresent(t -> save(t, false, level + 1, traceInfo,saveInfo));
        }

        NodeReference rightNodeReference = trie.getRight();

        if (rightNodeReference.wasLoaded()) {
            logger.trace("Start right trie. Level: {}", level);
            rightNodeReference.getNode().ifPresent(t -> save(t, false, level + 1, traceInfo,saveInfo));
        }

        if (trie.hasLongValue()) {
            // Note that there is no distinction in keys between node data and value data. This could bring problems in
            // the future when trying to garbage-collect the data. We could split the key spaces bit a single
            // overwritten MSB of the hash. Also note that when storing a node that has long value it could be the case
            // that the save the value here, but the value is already present in the database because other node shares
            // the value. This is suboptimal, we could check existence here but maybe the database already has
            // provisions to reduce the load in these cases where a key/value is set equal to the previous value.
            // In particular our levelDB driver has not method to test for the existence of a key without retrieving the
            // value also, so manually checking pre-existence here seems it will add overhead on the average case,
            // instead of reducing it.
            logger.trace("Putting in store, hasLongValue. Level: {}", level);
            this.store.put(trie.getValueHash().getBytes(), trie.getValue());
            logger.trace("End Putting in store, hasLongValue. Level: {}", level);
        }

        if (trie.isEmbeddable() && !isRootNode) {
            logger.trace("End Saving. Level: {}", level);
            return;
        }

        logger.trace("Putting in store trie root.");
        this.store.put(trieKeyBytes, trie.toMessage());
        trie.markAsSaved();
        logger.trace("End putting in store trie root.");
        logger.trace("End Saving trie, level: {}.", level);
    }

    @Override
    public void flush(){
        this.store.flush();
    }

    @Override
    public Optional<Trie> retrieve(byte[] hash) {
        byte[] message = this.store.get(hash);

        if (message == null) {
            return Optional.empty();
        }

        if ((forcePerformanceLogging) || (logger.isTraceEnabled())) {
            TraceInfo traceInfo = traceInfoLocal.get();
            traceInfo.numOfRetrieveTries++;
        }

        Trie trie = Trie.fromMessage(message, this).markAsSaved();
        return Optional.of(trie);
    }

    @Override
    public byte[] retrieveValue(byte[] hash) {
        if ((forcePerformanceLogging) || (logger.isTraceEnabled())) {
            TraceInfo traceInfo = traceInfoLocal.get();
            traceInfo.numOfRetrieveValues++;
        }

        return this.store.get(hash);
    }

    @Override
    public void dispose() {
        store.close();
    }

    public List<String> getStats() {
        List<String> r = new ArrayList<>();
        TraceInfo traceInfo = traceInfoLocal.get();
        r.add("numOfRetrieveTries: " + traceInfo.numOfRetrieveTries);
        r.add("numOfRetrieveValues: " + traceInfo.numOfRetrieveValues);
        r.add("numOfNoSaves: " + traceInfo.numOfNoSaves);
        r.add("numOfTopLevelSaves: " + traceInfo.numOfTopLevelSaves);
        r.add("numOfSaves: " + traceInfo.numOfSaves);

        List<String> dsStats = store.getStats();
        if (dsStats!=null) {
            r.add("DataSource stats ("+store.getName()+"):");
            r.addAll(dsStats);
        }
        return r;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public void saveCache() {
        store.saveCache();
    }

    /**
     * This holds tracing information during execution of the {@link #save(Trie)} method.
     * Should not be used when logger tracing is disabled ({@link Logger#isTraceEnabled()} is {@code false}).
     */
    private static final class TraceInfo {
        private int numOfRetrieveTries;
        private int numOfRetrieveValues;
        private int numOfSaves;
        private int numOfTopLevelSaves;
        private int numOfNoSaves;

    }

    private static final class SaveInfo {
        private int numOfRetrievesInSaveTrie;
        private int numOfSaves;
        private int numOfNoSaves;
    }
}
