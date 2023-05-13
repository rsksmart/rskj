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
package co.rsk.cli.tools;

import co.rsk.crypto.Keccak256;
import co.rsk.trie.*;
import org.ethereum.datasource.DataSourceKeyIterator;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MigrateStateUtil {
    private static final Logger logger = LoggerFactory.getLogger(MigrateStateUtil.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final int STAT_PRINT_INTERVAL = 50_000;

    private TrieStore fixSrcTrieStore;
    private Command command;
    private int nodesExported = 0;
    private long skipped = 0;
    private final byte[] root;
    private final TrieStore inputTrieStore;
    private final KeyValueDataSource dsSrc;
    private final KeyValueDataSource dsDst;
    private final KeyValueDataSource dsCache;
    private KeyValueDataSource dsChecked;
    private long startTime;

    private static class BooleanResult {
        private boolean value;

        public boolean isValue() {
            return value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }
    }

    public enum Command {
        COPY("COPY"),
        COPYALL("COPYALL"),
        MIGRATE("MIGRATE"),
        MIGRATE2("MIGRATE2"),
        CHECK("CHECK"),
        NODEEXISTS("NODEEXISTS"),
        VALUEEXISTS("VALUEEXISTS"),
        FIX("FIX");

        private final String name;

        Command(@Nonnull String name) {
            this.name = name;
        }

        public static Command ofName(@Nonnull String name) {
            Objects.requireNonNull(name, "name cannot be null");
            return Arrays.stream(Command.values()).filter(cmd -> cmd.name.equals(name) || cmd.name().equals(name))
                    .findAny()
                    .orElseThrow(() -> new IllegalArgumentException(String.format("%s: not found as Command", name)));
        }
    }

    public MigrateStateUtil(byte[] root,
                            TrieStore trieStore, KeyValueDataSource dsSrc,
                            KeyValueDataSource dsDst, KeyValueDataSource dsCache) {
        this.root = root;
        this.inputTrieStore = trieStore;
        this.dsSrc = dsSrc;
        this.dsDst = dsDst;
        this.dsCache = dsCache;
    }


    private void consoleLog(String s) {
        LocalDateTime now = LocalDateTime.now();
        logger.info(DTF.format(now) + ": " + s);
    }

    @SuppressWarnings("")
    private void showMem() {
        Runtime runtime = Runtime.getRuntime();

        NumberFormat format = NumberFormat.getInstance();

        long memory = runtime.totalMemory() - runtime.freeMemory();
        ;
        consoleLog("  used memory: " + format.format(memory / 1000) + "k -> ");
    }

    public boolean executeCommand(Command command) {
        TrieStore trieStore;
        this.command = command;

        if (command == Command.COPYALL) {
            copyAll();
            return true;
        }

        if (command == Command.MIGRATE2) {
            // Build a trie store having two internal stores
            trieStore = new CachedTrieStore(new
                    TrieStoreImpl(dsCache), inputTrieStore);
        } else {
            trieStore = inputTrieStore;
        }


        if (command == Command.VALUEEXISTS) {
            byte[] value = trieStore.retrieveValue(root);
            if (value == null) {
                consoleLog("Long value does not exists");
                return false;
            }
            consoleLog("ok value.length=" + value.length);
            return true;
        }
        Optional<Trie> otrie = trieStore.retrieve(root);

        if (!otrie.isPresent()) {
            consoleLog("Key not found");
            return false;
        }

        Trie trie = otrie.get();

        if (command == Command.NODEEXISTS) {
            consoleLog("Node found");
            consoleLog("Node Hash: " + trie.getHash().toHexString());
            consoleLog("Node encoded: " + Hex.toHexString(trie.toMessage()));
            if (trie.getValueLength().intValue() > 0) {
                consoleLog("Value hash: " + trie.getValueHash().toHexString());
            } else {
                consoleLog("No value stored");
            }
            if (trie.hasLongValue()) {
                consoleLog("Long value");
                byte[] value = trieStore.retrieveValue(trie.getValueHash().getBytes());
                if (value == null) {
                    consoleLog("Long value is null ");
                    return false;
                }
            }
            return true;
        }

        if (command == Command.CHECK) {
            dsChecked = dsSrc;
        }

        if (command == Command.FIX) {
            fixSrcTrieStore = new TrieStoreImpl(dsSrc);
        }

        boolean ret;
        startTime = System.currentTimeMillis();
        ret = processTrie(trie, dsSrc, dsDst);

        showStat();
        return ret;
    }

    private void showTimeStats() {
        long endTime = System.currentTimeMillis();
        long elapsedTimeSec = (endTime - startTime) / 1000;
        consoleLog("Elapsed time: " + elapsedTimeSec + " sec");
        if (elapsedTimeSec > 0) {
            consoleLog("Nodes/sec: " + nodesExported / elapsedTimeSec);
        }
    }

    private void showStat() {
        //consoleLog("nodes scanned: " +(nodesExported/1000)+"k skipped: "+(skipped/1000)+"k");
        consoleLog("nodes scanned: " + nodesExported + " skipped: " + skipped);
        showTimeStats();
    }

    private boolean processEmbedded(Trie trie, KeyValueDataSource dsSrc, KeyValueDataSource dsDst) {

        if ((command == Command.CHECK) || (command == Command.FIX)) {
            if (trie.hasLongValue()) {
                Keccak256 v = trie.getValueHash();

                byte[] lv = dsChecked.get(v.getBytes());
                // If we iterate over the big db we would do
                //
                boolean badLongValue = false;
                if (lv == null) { // not present
                    badLongValue = true;
                } else if (lv.length != trie.getValueLength().intValue()) {
                    badLongValue = true;
                } else if (!Arrays.equals(lv, trie.getValue())) {
                    badLongValue = true;
                }
                if (badLongValue) {
                    consoleLog("Long value incorrect node: " + trie.getHash().toHexString());
                    consoleLog("long value hash: " + trie.getValueHash().toHexString());
                    if (command == Command.CHECK) {
                        return false;
                    }
                    // FIX
                    byte[] value = fixSrcTrieStore.retrieveValue(trie.getValueHash().getBytes());
                    if (value == null || value.length != trie.getValueLength().intValue()) {
                        consoleLog("Cannot fix.");
                        return false;
                    }

                    dsDst.put(trie.getValueHash().getBytes(), value);
                }
            }
        } else {
            if (trie.hasLongValue()) {
                dsDst.put(trie.getValueHash().getBytes(), trie.getValue());
            }
        }
        return true;
    }

    private boolean isMigration() {
        return (command == Command.MIGRATE) || (command == Command.MIGRATE2);
    }

    private void statsCheck() {
        nodesExported++;
        if (nodesExported % STAT_PRINT_INTERVAL == 0) {
            showStat();
            showMem();
            if (isMigration()) {
                dsDst.flush();
            }
        }
    }

    private boolean processTrie(Trie trie, KeyValueDataSource dsSrc, KeyValueDataSource dsDst) {

        statsCheck();

        byte[] hash = trie.getHash().getBytes();
        if ((isMigration()) && (dsDst.get(hash) != null)) {
            skipped++;
            //sadly, if there is a collision between node data and long value data,
            // the existence of the node data does not guarantee the existence of the children
            return true; // already exists
        }
        boolean fixme = false;
        try {
            NodeReference leftReference = trie.getLeft();
            if (!processReference(leftReference)) {
                return false;
            }
            NodeReference rightReference = trie.getRight();
            if (!processReference(rightReference)) {
                return false;
            }

            if ((command == Command.CHECK) || (command == Command.FIX)) {
                BooleanResult r = new BooleanResult();
                if (!checkNode(hash, trie, r)) {
                    return false;
                }
                fixme = r.value;
            } else {
                // MIGRATE, MIGRATE2 & COPY
                copyNode(trie, dsDst, hash);
            }

        } catch (RuntimeException e) {
            consoleLog("Node invalid: " + trie.getHash().toHexString());
            if (command == Command.CHECK) {
                return false;
            }
            fixme = true;
        }
        if (fixme) {
            if (fixNode(dsDst, hash)) return false;
        }
        return true;
    }

    private void copyNode(Trie trie, KeyValueDataSource dsDst, byte[] hash) {
        byte[] m = trie.toMessage();
        dsDst.put(hash, m);
        if (trie.hasLongValue()) {
            dsDst.put(trie.getValueHash().getBytes(), trie.getValue());
        }
    }

    private boolean checkNode(byte[] hash, Trie trie, BooleanResult fixme) {
        byte[] m = trie.toMessage();
        byte[] ret = dsSrc.get(hash);
        if (!Arrays.equals(ret, m)) {
            consoleLog("Node incorrect: " + trie.getHash().toHexString());
            if (command == Command.CHECK) {
                return false;
            }
            fixme.value = true;
        }
        if (trie.hasLongValue()) {
            BooleanResult r = new BooleanResult();
            if (!checkLongValue(trie, r)) {
                return false;
            }
            fixme.value = r.value;

        }
        return true;
    }

    private boolean processReference(NodeReference leftReference) {
        if (!leftReference.isEmpty()) {
            Optional<Trie> left = leftReference.getNodeDetached();

            if (left.isPresent()) {
                Trie leftTrie = left.get();

                if (!leftReference.isEmbeddable()) {
                    if (!processTrie(leftTrie, dsSrc, dsDst)) {
                        return false;
                    }
                } else {
                    if (!processEmbedded(leftTrie, dsSrc, dsDst)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean fixNode(KeyValueDataSource dsDst, byte[] hash) {
        Optional<Trie> origNode = fixSrcTrieStore.retrieve(hash);
        if (!origNode.isPresent()) {
            consoleLog("cannot fix");
            return true;
        }
        Trie o = origNode.get();
        dsDst.put(hash, o.toMessage());
        if (o.hasLongValue()) {
            byte[] oValue = o.getValue();
            if (oValue.length != o.getValueLength().intValue()) {
                consoleLog("Cannot fix.");
                return true;
            }
            dsDst.put(o.getValueHash().getBytes(), oValue);
        }
        return false;
    }

    boolean checkLongValue(Trie trie, BooleanResult needsFixing) {
        Keccak256 v = trie.getValueHash();
        byte[] lv = dsChecked.get(v.getBytes());
        // If we iterate over the big db we would do
        //
        boolean badLongValue = false;
        if (lv == null) { // not present
            badLongValue = true;
        } else if (lv.length != trie.getValueLength().intValue()) {
            badLongValue = true;
        } else if (!Arrays.equals(lv, trie.getValue())) {
            badLongValue = true;
        }

        if (badLongValue) {
            consoleLog("Long value incorrect node: " + trie.getHash().toHexString());
            consoleLog("long value hash: " + trie.getValueHash().toHexString());
            if (command == Command.CHECK) {
                return false; // stop
            }
            needsFixing.value = true;
        }
        return true; // continue
    }

    private void copyAll() {
        consoleLog("Fetching keys...");
        long nodesExported = 0;
        Map<ByteArrayWrapper, byte[]> bulkData = new HashMap<>();

        try (DataSourceKeyIterator iterator = dsSrc.keyIterator()) {
            while (iterator.hasNext()) {
                byte[] data = iterator.next();

                bulkData.put(
                        new ByteArrayWrapper(data),
                        dsSrc.get(data)
                );

                nodesExported++;
                if (nodesExported % 50000 == 0) {
                    consoleLog("nodes scanned: " + (nodesExported / 1000) + "k");
                    dsDst.updateBatch(bulkData, new HashSet<>());
                    bulkData.clear();
                }
            }

            if (!bulkData.isEmpty()) {
                dsDst.updateBatch(bulkData, new HashSet<>());
                bulkData.clear();
            }
        } catch (Exception e) {
            logger.error("An error happened closing DB Key Iterator", e);
            throw new RuntimeException(e);
        }
        consoleLog("Done");
    }
}
