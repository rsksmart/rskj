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

package co.rsk.db.migration;

import co.rsk.RskContext;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.db.StateRootHandler;
import co.rsk.remasc.RemascTransaction;
import co.rsk.trie.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.*;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * This is a one-time tool and should be removed after SecondFork (TBD) fork activation
 */
public class OrchidToUnitrieMigrator {

    private static final Logger logger = LoggerFactory.getLogger(OrchidToUnitrieMigrator.class);
    private final KeyValueDataSource orchidContractDetailsDataStore;
    private final KeyValueDataSource orchidContractsStorage;
    private final MissingOrchidStorageKeysProvider missingOrchidStorageKeysProvider;
    private final TrieStore orchidContractsTrieStore;
    private final TrieStore orchidAccountsTrieStore;
    private final String databaseDir;
    private final Map<RskAddress, TrieStore> contractStoreCache = new HashMap<>();
    private final Map<ByteArrayWrapper, RskAddress> addressHashes;
    private final TrieConverter trieConverter;
    private final Map<Keccak256, DataWord> keccak256Cache;
    private final Block blockToMigrate;
    private final Repository unitrieRepository;
    private final StateRootHandler stateRootHandler;

    public OrchidToUnitrieMigrator(Block blockToMigrate,
                                   String databaseDir,
                                   Repository unitrieRepository,
                                   StateRootHandler stateRootHandler,
                                   TrieConverter trieConverter,
                                   MissingOrchidStorageKeysProvider missingOrchidStorageKeysProvider) {
        this.databaseDir = databaseDir;
        this.blockToMigrate = blockToMigrate;
        this.unitrieRepository = unitrieRepository;
        this.stateRootHandler = stateRootHandler;
        this.trieConverter = trieConverter;

        this.orchidContractDetailsDataStore = RskContext.makeDataSource("details", databaseDir);
        this.orchidContractsStorage = RskContext.makeDataSource("contracts-storage", databaseDir);
        this.missingOrchidStorageKeysProvider = missingOrchidStorageKeysProvider;
        this.orchidContractsTrieStore = new CachedTrieStore(new TrieStoreImpl(orchidContractsStorage));
        this.orchidAccountsTrieStore = new CachedTrieStore(new TrieStoreImpl(RskContext.makeDataSource("state", databaseDir)));
        this.keccak256Cache = new HashMap<>();
        this.addressHashes = orchidContractDetailsDataStore.keys().stream()
                .filter(accountAddress -> accountAddress.length == 20)
                .collect(Collectors.toMap(
                        accountAddress -> ByteUtil.wrap(Keccak256Helper.keccak256(accountAddress)),
                        RskAddress::new
                ));
        // Remasc sender and receiver addresses
        this.addressHashes.put(ByteUtil.wrap(Keccak256Helper.keccak256(PrecompiledContracts.REMASC_ADDR.getBytes())), PrecompiledContracts.REMASC_ADDR);
        this.addressHashes.put(ByteUtil.wrap(Keccak256Helper.keccak256(RemascTransaction.REMASC_ADDRESS.getBytes())), RemascTransaction.REMASC_ADDRESS);
    }

    public static void migrateStateToUnitrieIfNeeded(RskContext ctx) throws IOException {
        String databaseDir = ctx.getRskSystemProperties().databaseDir();
        // we need to check these before the data sources are init'ed
        Path unitrieDatabase = Paths.get(databaseDir, "unitrie");
        boolean hasUnitrieState = Files.exists(unitrieDatabase);
        boolean hasOldState = Files.exists(Paths.get(databaseDir, "state"));
        if (hasUnitrieState) {
            logger.trace("The node storage follows the Unitrie format");
            return;
        }

        if (!hasOldState) {
            logger.trace("The node is starting for the first time and storage will follow the Unitrie format");
            return;
        }

        // this block number has to be validated before the release to ensure the migration works fine for every user
        long minimumBlockNumberToMigrate = ctx.getRskSystemProperties().getDatabaseMigrationMinimumHeight();
        Block blockToMigrate = ctx.getBlockStore().getBestBlock();
        if (blockToMigrate == null || blockToMigrate.getNumber() < minimumBlockNumberToMigrate) {
            logger.error(
                    "The database can't be migrated because the node wasn't up to date before upgrading. " +
                            "Please reset the database or sync past block {} with the previous version to continue.",
                    minimumBlockNumberToMigrate
            );
            logger.error("Reset database or continue syncing with previous version");
            // just opening the db against the unitrie directory creates certain file structure
            // we clean that here in case of an error
            Files.deleteIfExists(unitrieDatabase);
            System.exit(1);
        }

        OrchidToUnitrieMigrator unitrieMigrationTool = new OrchidToUnitrieMigrator(
                blockToMigrate,
                databaseDir,
                ctx.getRepository(),
                ctx.getStateRootHandler(),
                ctx.getTrieConverter(),
                new MissingOrchidStorageKeysProvider(
                        databaseDir,
                        ctx.getRskSystemProperties().getDatabaseMissingStorageKeysUrl()
                )
        );

        unitrieMigrationTool.migrate();
    }

    public void migrate() {
        logger.info("Migration started");
        logger.info("Block {}", blockToMigrate.getNumber());
        Trie migratedTrie = migrateState(blockToMigrate);
        unitrieRepository.flush();

        stateRootHandler.register(
                blockToMigrate.getHeader(),
                migratedTrie
        );
    }

    private Trie migrateState(Block blockToMigrate) {
        byte[] orchidStateRoot = blockToMigrate.getStateRoot();
        Trie orchidAccountsTrie = orchidAccountsTrieStore.retrieve(orchidStateRoot);
        if (!Arrays.equals(orchidStateRoot, orchidAccountsTrie.getHashOrchid(true).getBytes())) {
            throw new IllegalStateException(String.format("Stored account state is not consistent with the expected root (%s) for block %d", Hex.toHexString(orchidStateRoot), blockToMigrate.getNumber()));
        }

        try {
            buildPartialUnitrie(orchidAccountsTrie, unitrieRepository);
        } catch (MissingContractStorageKeysException e) {
            StringBuilder missingStorageKeysMessage = new StringBuilder(
                "We have detected an inconsistency in your database and are unable to migrate it automatically.\n" +
                "Please visit https://www.github.com/rsksmart/rskj/issues/452 for information on how to continue.\n" +
                "Here is the data you'll need:\n"
            );
            for (Keccak256 entry : e.getMissingStorageKeys()) {
                missingStorageKeysMessage.append(entry.toHexString()).append("\n");
            }
            logger.error(missingStorageKeysMessage.toString());
        }

        byte[] lastStateRoot = unitrieRepository.getRoot();
        byte[] orchidMigratedStateRoot = trieConverter.getOrchidAccountTrieRoot(unitrieRepository.getMutableTrie().getTrie());
        if (!Arrays.equals(orchidStateRoot, orchidMigratedStateRoot)) {
            logger.error("State root after migration doesn't match");
            logger.error("Orchid state root: {}", Hex.toHexString(orchidStateRoot));
            logger.error("Converted Unitrie root: {}", Hex.toHexString(orchidMigratedStateRoot));
            logger.error("Unitrie state root: {}", Hex.toHexString(lastStateRoot));
            throw new IllegalStateException("State root after migration doesn't match. Check the log for more info.");
        } else {
            logger.info("Migration complete");
            logger.info("State root: {}", Hex.toHexString(lastStateRoot));
        }

        return unitrieRepository.getMutableTrie().getTrie();
    }

    private void buildPartialUnitrie(Trie orchidAccountsTrie, Repository repository) throws MissingContractStorageKeysException {
        int accountsToLog = 500;
        int accountsCounter = 0;
        logger.trace("(x = {} accounts): ", accountsToLog);
        Iterator<Trie.IterationElement> orchidAccountsTrieIterator = orchidAccountsTrie.getPreOrderIterator();
        Collection<Keccak256> missingStorageKeys = new HashSet<>();
        while (orchidAccountsTrieIterator.hasNext()) {
            Trie.IterationElement orchidAccountsTrieElement = orchidAccountsTrieIterator.next();
            TrieKeySlice currentElementExpandedPath = orchidAccountsTrieElement.getNodeKey();
            if (currentElementExpandedPath.length() == Keccak256Helper.DEFAULT_SIZE) {
                accountsCounter++;
                byte[] hashedAddress = currentElementExpandedPath.encode();
                OrchidAccountState oldAccountState = new OrchidAccountState(orchidAccountsTrieElement.getNode().getValue());
                AccountState accountState = new AccountState(oldAccountState.getNonce(), oldAccountState.getBalance());
                RskAddress accountAddress = addressHashes.get(ByteUtil.wrap(hashedAddress));
                repository.createAccount(accountAddress);
                repository.updateAccountState(accountAddress, accountState);
                byte[] contractData = orchidContractDetailsDataStore.get(accountAddress.getBytes());
                byte[] codeHash = oldAccountState.getCodeHash();
                byte[] accountStateRoot = oldAccountState.getStateRoot();
                if (contractData != null) {
                    try {
                        migrateContract(accountAddress, repository, contractData, codeHash, accountStateRoot);
                    } catch (MissingContractStorageKeysException e) {
                        missingStorageKeys.addAll(e.getMissingStorageKeys());
                    }
                }
                if (accountsCounter % accountsToLog == 0) {
                    logger.trace("x");
                }
            }
        }
        if (!missingStorageKeys.isEmpty()) {
            throw new MissingContractStorageKeysException(missingStorageKeys);
        }
    }

    private void migrateContract(RskAddress accountAddress, Repository currentRepository, byte[] contractDataRaw, byte[] accountCodeHash, byte[] stateRoot) throws MissingContractStorageKeysException {
        ContractData contractData = new ContractData(contractDataRaw);

        boolean initialized = false;
        if (!Arrays.equals(stateRoot, EMPTY_TRIE_HASH)) {
            RskAddress contractAddress = contractData.getContractAddress();
            Trie contractStorageTrie = getContractStorageTrie(contractAddress, contractData);

            try {
                contractStorageTrie = contractStorageTrie.getSnapshotTo(new Keccak256(stateRoot));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Cannot find state root trie. Check the log for more info.", e);
            }

            RLPList rlpKeys = contractData.getKeys();
            int keysCount = rlpKeys.size();
            int keysToLog = 2000;
            boolean logKeysMigrationProgress = keysCount > keysToLog * 2;
            if (logKeysMigrationProgress) {
                logger.trace("Migrating {} with {} keys (. = {} keys): ", contractAddress, rlpKeys.size(), keysToLog);
            }
            int migratedKeysCounter = 0;
            for (RLPElement rlpKey : rlpKeys) {
                byte[] rawKey = rlpKey.getRLPData();
                DataWord storageKey = DataWord.valueOf(rawKey);
                Keccak256 storageKeyHash = new Keccak256(Keccak256Helper.keccak256(rawKey));
                keccak256Cache.put(storageKeyHash, storageKey);
            }
            Collection<Keccak256> missingStorageKeys = new HashSet<>();
            Iterator<Trie.IterationElement> inOrderIterator = contractStorageTrie.getInOrderIterator();
            while (inOrderIterator.hasNext()) {
                Trie.IterationElement iterationElement = inOrderIterator.next();
                if (iterationElement.getNode().getValue() != null) {
                    Keccak256 storageKeyHash = new Keccak256(iterationElement.getNodeKey().encode());
                    DataWord storageKey = keccak256Cache.computeIfAbsent(storageKeyHash, missingOrchidStorageKeysProvider::getKeccak256PreImage);
                    if (storageKey == null) {
                        missingStorageKeys.add(storageKeyHash);
                        continue;
                    }

                    byte[] value = iterationElement.getNode().getValue();
                    migratedKeysCounter++;
                    if (!initialized) {
                        currentRepository.setupContract(accountAddress);
                        initialized = true;
                    }
                    if (logKeysMigrationProgress && migratedKeysCounter % keysToLog == 0) {
                        logger.trace(".");
                    }
                    currentRepository.addStorageBytes(contractAddress, storageKey, value);
                }
            }
            if (!missingStorageKeys.isEmpty()) {
                logger.error("{} keys lost", missingStorageKeys.size());
                throw new MissingContractStorageKeysException(missingStorageKeys);
            }
        }

        byte[] code = contractData.getCode();
        if (code != null) {
            if (!initialized) {
                currentRepository.setupContract(accountAddress);
            }
            if (!Arrays.equals(accountCodeHash, Keccak256Helper.keccak256(code))) {
                // mati-fix (ref: org.ethereum.db.DetailsDataStore#get)
                code = orchidContractsStorage.get(accountCodeHash);
            }
            currentRepository.saveCode(accountAddress, code);
        }
    }

    private Trie getContractStorageTrie(RskAddress contractAddress, ContractData contractData) {
        byte[] external = contractData.getExternal();
        byte[] root = contractData.getRoot();

        if (external == null || external.length <= 0 || external[0] != 1) {
            return orchidTrieDeserialize(root);
        }

        Trie contractStorageTrie = orchidContractsTrieStore.retrieve(root);
        if (contractStorageTrie != null) {
            return contractStorageTrie;
        }

        // picco-fix (ref: co.rsk.db.ContractStorageStoreFactory#getTrieStore)
        TrieStore contractTrieStore = contractStoreCache.computeIfAbsent(
                contractAddress,
                address -> new CachedTrieStore(new TrieStoreImpl(RskContext.makeDataSource("details-storage/" + address, databaseDir)))
        );
        contractStorageTrie = contractTrieStore.retrieve(root);
        if (contractStorageTrie == null) {
            throw new IllegalStateException(String.format("Unable to find root %s for the contract %s", Hex.toHexString(root), contractAddress));
        }
        if (!Arrays.equals(root, contractStorageTrie.getHashOrchid(true).getBytes())) {
            throw new IllegalStateException(String.format("Stored contract state is not consistent with the expected root (%s)", Hex.toHexString(root)));
        }

        return contractStorageTrie;
    }

    private static Trie orchidTrieDeserialize(byte[] bytes) {
        final int keccakSize = Keccak256Helper.DEFAULT_SIZE_BYTES;
        int expectedSize = Short.BYTES + keccakSize;
        if (expectedSize > bytes.length) {
            throw new IllegalArgumentException(
                    String.format("Expected size is: %d actual size is %d", expectedSize, bytes.length));
        }

        byte[] root = Arrays.copyOfRange(bytes, Short.BYTES, expectedSize);
        TrieStore store = orchidTrieStoreDeserialize(bytes, expectedSize, new HashMapDB());

        Trie newTrie = store.retrieve(root);

        if (newTrie == null) {
            throw new IllegalArgumentException(String.format("Deserialized storage doesn't contain expected trie: %s", Hex.toHexString(root)));
        }

        return newTrie;
    }

    private static TrieStore orchidTrieStoreDeserialize(byte[] bytes, int offset, KeyValueDataSource ds) {
        int current = offset;
        current += Short.BYTES; // version

        int nkeys = readInt(bytes, current);
        current += Integer.BYTES;

        for (int k = 0; k < nkeys; k++) {
            int lkey = readInt(bytes, current);
            current += Integer.BYTES;
            if (lkey > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for key expected:%d actual:%d total:%d",
                        lkey, bytes.length - current, bytes.length));
            }
            byte[] key = Arrays.copyOfRange(bytes, current, current + lkey);
            current += lkey;

            int lvalue = readInt(bytes, current);
            current += Integer.BYTES;
            if (lvalue > bytes.length - current) {
                throw new IllegalArgumentException(String.format(
                        "Left bytes are too short for value expected:%d actual:%d total:%d",
                        lvalue, bytes.length - current, bytes.length));
            }
            byte[] value = Arrays.copyOfRange(bytes, current, current + lvalue);
            current += lvalue;
            ds.put(key, value);
        }

        return new TrieStoreImpl(ds);
    }

    // this methods reads a int as dataInputStream + byteArrayInputStream
    private static int readInt(byte[] bytes, int position) {
        int ch1 = bytes[position];
        int ch2 = bytes[position + 1];
        int ch3 = bytes[position + 2];
        int ch4 = bytes[position + 3];
        if ((ch1 | ch2 | ch3 | ch4) < 0) {
            throw new IllegalArgumentException(String.format(
                    "On position %d there are invalid bytes for a short value %s %s %s %s", position, ch1, ch2, ch3, ch4
            ));
        }

        return (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4);
    }

    private static class ContractData {
        private RLPList rlpList;
        private byte[] code;
        private RskAddress contractAddress;
        private byte[] external;
        private byte[] root;

        public ContractData(byte[] contractData) {
            ArrayList<RLPElement> rlpData = RLP.decode2(contractData);
            rlpList = (RLPList) rlpData.get(0);
            RLPElement rlpCode = rlpList.get(3);
            code = rlpCode.getRLPData();

            RLPItem rlpAddress = (RLPItem) rlpList.get(0);
            RLPItem rlpIsExternalStorage = (RLPItem) rlpList.get(1);
            RLPItem rlpStorage = (RLPItem) rlpList.get(2);
            byte[] rawAddress = rlpAddress.getRLPData();
            if (Arrays.equals(rawAddress, new byte[]{0x00})) {
                contractAddress = PrecompiledContracts.REMASC_ADDR;
            } else {
                contractAddress = new RskAddress(rawAddress);
            }
            external = rlpIsExternalStorage.getRLPData();
            root = rlpStorage.getRLPData();
        }

        public byte[] getCode() {
            return code != null? Arrays.copyOf(code, code.length): null;
        }

        public RskAddress getContractAddress() {
            return contractAddress;
        }

        public byte[] getExternal() {
            return external != null? Arrays.copyOf(external, external.length): null;
        }

        public byte[] getRoot() {
            return root != null? Arrays.copyOf(root, root.length): null;
        }

        public RLPList getKeys() {
            return (RLPList) rlpList.get(4);
        }
    }

    private static class CachedTrieStore implements TrieStore {

        private final TrieStore parent;
        private final Map<Keccak256, Trie> triesCache;
        private final Map<ByteArrayWrapper, byte[]> valueCache;

        private CachedTrieStore(TrieStore parent) {
            this.parent = parent;
            this.triesCache = new HashMap<>();
            this.valueCache = new HashMap<>();
        }

        @Override
        public void save(Trie trie) {
            triesCache.put(trie.getHash(), trie);
            parent.save(trie);
        }

        @Override
        public void saveValue(Trie trie) {
            throw new UnsupportedOperationException("It's not expected for current store to save values");
        }

        @Override
        public Trie retrieve(byte[] hash) {
            return triesCache.computeIfAbsent(new Keccak256(hash), key -> parent.retrieve(hash));
        }

        @Override
        public byte[] retrieveValue(byte[] hash) {
            return valueCache.computeIfAbsent(ByteUtil.wrap(hash), key -> parent.retrieveValue(hash));
        }

        @Override
        public void flush() {
        }
    }

    private static class MissingContractStorageKeysException extends Exception {

        private final Collection<Keccak256> missingStorageKeys;

        private MissingContractStorageKeysException(Collection<Keccak256> missingStorageKeys) {
            this.missingStorageKeys = Collections.unmodifiableCollection(missingStorageKeys);
        }

        private Collection<Keccak256> getMissingStorageKeys() {
            return missingStorageKeys;
        }
    }
}
