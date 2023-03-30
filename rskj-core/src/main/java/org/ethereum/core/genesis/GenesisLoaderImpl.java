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

package org.ethereum.core.genesis;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.AccountState;
import org.ethereum.core.Genesis;
import org.ethereum.core.GenesisHeader;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.MutableRepository;
import org.ethereum.json.Utils;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * 1. Reads the genesis configuration from storage
 * 2. Loads the genesis initial state and sets the expected state root
 * 3. Stores the genesis initial state trie in the global storage
 * 4. Registers the genesis state root in the state root handler
 */
public class GenesisLoaderImpl implements GenesisLoader {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.keccak256(RLP.encodeList());

    private static final Logger logger = LoggerFactory.getLogger(GenesisLoaderImpl.class);

    private final ActivationConfig activationConfig;
    private final StateRootHandler stateRootHandler;
    private final TrieStore trieStore;
    private final GenesisJson genesisJson;

    private final BigInteger initialNonce;
    private final boolean isRsk;
    private final boolean useRskip92Encoding;
    private final boolean isRskip126Enabled;

    public GenesisLoaderImpl(
            ActivationConfig activationConfig,
            StateRootHandler stateRootHandler,
            TrieStore trieStore,
            String genesisFile,
            BigInteger initialNonce,
            boolean isRsk,
            boolean useRskip92Encoding,
            boolean isRskip126Enabled) {
        this(
                activationConfig,
                stateRootHandler,
                trieStore,
                GenesisLoaderImpl.class.getResourceAsStream("/genesis/" + genesisFile),
                initialNonce,
                isRsk,
                useRskip92Encoding,
                isRskip126Enabled
        );
    }

    public GenesisLoaderImpl(
            ActivationConfig activationConfig,
            StateRootHandler stateRootHandler,
            TrieStore trieStore,
            InputStream resourceAsStream,
            BigInteger initialNonce,
            boolean isRsk,
            boolean useRskip92Encoding,
            boolean isRskip126Enabled) {
        this.activationConfig = activationConfig;
        this.stateRootHandler = stateRootHandler;
        this.trieStore = trieStore;
        this.initialNonce = initialNonce;
        this.isRsk = isRsk;
        this.useRskip92Encoding = useRskip92Encoding;
        this.isRskip126Enabled = isRskip126Enabled;

        try {
                this.genesisJson = readGenesisJson(new FileInputStream("./genesis/rsk-block-performance-test.json"));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
        }
    }

    @Override
    public Genesis load() {
        Genesis incompleteGenesis = mapFromJson();
        Trie genesisTrie = loadGenesisTrie(incompleteGenesis);

        String stateRoot = genesisJson.getStateRoot();
        if (stateRoot != null) {
            incompleteGenesis.setStateRoot(Utils.parseData(stateRoot));
        } else {
            incompleteGenesis.setStateRoot(genesisTrie.getHash().getBytes());
        }

        incompleteGenesis.flushRLP();

        registerGenesisStateRoot(genesisTrie, incompleteGenesis);
        return incompleteGenesis;
    }

    private Genesis mapFromJson() {
        byte[] difficulty = Utils.parseData(genesisJson.difficulty);
        byte[] coinbase = Utils.parseData(genesisJson.coinbase);

        byte[] timestampBytes = Utils.parseData(genesisJson.timestamp);
        long timestamp = ByteUtil.byteArrayToLong(timestampBytes);

        byte[] parentHash = Utils.parseData(genesisJson.parentHash);
        byte[] extraData = Utils.parseData(genesisJson.extraData);

        byte[] gasLimitBytes = Utils.parseData(genesisJson.gasLimit);
        long gasLimit = ByteUtil.byteArrayToLong(gasLimitBytes);

        byte[] bitcoinMergedMiningHeader = null;
        byte[] bitcoinMergedMiningMerkleProof = null;
        byte[] bitcoinMergedMiningCoinbaseTransaction = null;
        byte[] minGasPrice = null;

        if (isRsk) {
            bitcoinMergedMiningHeader = Utils.parseData(genesisJson.bitcoinMergedMiningHeader);
            bitcoinMergedMiningMerkleProof = Utils.parseData(genesisJson.bitcoinMergedMiningMerkleProof);
            bitcoinMergedMiningCoinbaseTransaction = Utils.parseData(genesisJson.bitcoinMergedMiningCoinbaseTransaction);
            minGasPrice = Utils.parseData(genesisJson.getMinimumGasPrice());
        }

        Map<RskAddress, AccountState> accounts = new HashMap<>();
        Map<RskAddress, byte[]> codes = new HashMap<>();
        Map<RskAddress, Map<DataWord, byte[]>> storages = new HashMap<>();
        Map<String, AllocatedAccount> alloc = genesisJson.getAlloc();
        for (Map.Entry<String, AllocatedAccount> accountEntry : alloc.entrySet()) {
            if(!"00".equals(accountEntry.getKey())) {
                Coin balance = new Coin(new BigInteger(accountEntry.getValue().getBalance()));
                BigInteger accountNonce;

                if (accountEntry.getValue().getNonce() != null) {
                    accountNonce = new BigInteger(accountEntry.getValue().getNonce());
                } else {
                    accountNonce = initialNonce;
                }

                AccountState acctState = new AccountState(accountNonce, balance);
                Contract contract = accountEntry.getValue().getContract();

                RskAddress address = new RskAddress(accountEntry.getKey());
                if (contract != null) {
                    byte[] code = Hex.decode(contract.getCode());
                    codes.put(address, code);
                    Map<DataWord, byte[]> storage = new HashMap<>(contract.getData().size());
                    for (Map.Entry<String, String> storageData : contract.getData().entrySet()) {
                        storage.put(DataWord.valueFromHex(storageData.getKey()), Hex.decode(storageData.getValue()));
                    }
                    storages.put(address, storage);
                }
                accounts.put(address, acctState);
            }
        }

        GenesisHeader header = new GenesisHeader(
                parentHash,
                EMPTY_LIST_HASH,
                Genesis.getZeroHash(),
                difficulty,
                0,
                ByteUtil.longToBytes(gasLimit),
                0,
                timestamp,
                extraData,
                bitcoinMergedMiningHeader,
                bitcoinMergedMiningMerkleProof,
                bitcoinMergedMiningCoinbaseTransaction,
                minGasPrice,
                useRskip92Encoding,
                coinbase);

        return new Genesis(isRskip126Enabled, accounts, codes, storages, header);
    }

    private Trie loadGenesisTrie(Genesis genesis) {
        Repository repository = new MutableRepository(trieStore, new Trie(trieStore));
        loadGenesisInitalState(repository, genesis);

        setupPrecompiledContractsStorage(repository);

        repository.commit();
        repository.save();
        return repository.getTrie();
    }

    private void registerGenesisStateRoot(Trie genesisTrie, Genesis genesis) {
        stateRootHandler.register(genesis.getHeader(), genesisTrie);
    }

    /**
     * When created, contracts are marked in storage for consistency.
     * Here, we apply this logic to precompiled contracts depending on consensus rules
     */
    private void setupPrecompiledContractsStorage(Repository repository) {
        ActivationConfig.ForBlock genesisActivations = activationConfig.forBlock(0);
        if (genesisActivations.isActivating(ConsensusRule.RSKIP126)) {
            BlockExecutor.maintainPrecompiledContractStorageRoots(repository, genesisActivations);
        }
    }

    public static void loadGenesisInitalState(Repository repository, Genesis genesis) {
        // first we need to create the accounts, which creates also the associated ContractDetails
        for (RskAddress accounts : genesis.getAccounts().keySet()) {
            repository.createAccount(accounts);
        }

        // second we create contracts whom only have code modifying the preexisting ContractDetails instance
        for (Map.Entry<RskAddress, byte[]> codeEntry : genesis.getCodes().entrySet()) {
            RskAddress contractAddress = codeEntry.getKey();
            repository.setupContract(contractAddress);
            repository.saveCode(contractAddress, codeEntry.getValue());
            Map<DataWord, byte[]> contractStorage = genesis.getStorages().get(contractAddress);
            for (Map.Entry<DataWord, byte[]> storageEntry : contractStorage.entrySet()) {
                repository.addStorageBytes(contractAddress, storageEntry.getKey(), storageEntry.getValue());
            }
        }

        // given the accounts had the proper storage root set from the genesis construction we update the account state
        for (Map.Entry<RskAddress, AccountState> accountEntry : genesis.getAccounts().entrySet()) {
            repository.updateAccountState(accountEntry.getKey(), accountEntry.getValue());
        }
    }

    private static GenesisJson readGenesisJson(InputStream inputStream) {
        try {
            return new ObjectMapper().readValue(inputStream, GenesisJson.class);
        } catch (Exception e) {
            logger.error("Cannot read genesis json file");

            throw new RuntimeException("Genesis block configuration is corrupted or not found ./resources/genesis/...", e);
        } finally {
            closeStream(inputStream);
        }
    }

    private static void closeStream(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            logger.error("Cannot close input stream", e);
        }
    }
}
