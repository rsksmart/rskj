/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositoryImpl;
import co.rsk.db.StateRootHandler;
import co.rsk.trie.Trie;
import co.rsk.validators.BlockValidator;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.ReceiptStore;
import org.ethereum.db.TrieStorePoolOnMemory;
import org.ethereum.listener.EthereumListener;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * Created by mario on 13/01/17.
 */
public class BlockChainLoader {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private final RskSystemProperties config;
    private final BlockStore blockStore;
    private final Repository repository;
    private final ReceiptStore receiptStore;
    private final TransactionPool transactionPool;
    private final EthereumListener listener;
    private final BlockValidator blockValidator;
    private final BlockExecutor blockExecutor;
    private final Genesis genesis;
    private final StateRootHandler stateRootHandler;

    public BlockChainLoader(
            RskSystemProperties config,
            org.ethereum.core.Repository repository,
            org.ethereum.db.BlockStore blockStore,
            ReceiptStore receiptStore,
            TransactionPool transactionPool,
            EthereumListener listener,
            BlockValidator blockValidator,
            BlockExecutor blockExecutor,
            Genesis genesis,
            StateRootHandler stateRootHandler) {
        this.config = config;
        this.blockStore = blockStore;
        this.repository = repository;
        this.receiptStore = receiptStore;
        this.transactionPool = transactionPool;
        this.listener = listener;
        this.blockValidator = blockValidator;
        this.blockExecutor = blockExecutor;
        this.genesis = genesis;
        this.stateRootHandler = stateRootHandler;
    }

    public BlockChainImpl loadBlockchain() {
        BlockChainImpl blockchain = new BlockChainImpl(
                repository,
                blockStore,
                receiptStore,
                transactionPool,
                listener,
                blockValidator,
                config.isFlushEnabled(),
                config.flushNumberOfBlocks(),
                blockExecutor,
                stateRootHandler
        );

        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            logger.info("DB is empty - adding Genesis");

            loadRepository(repository);
            updateGenesis(repository);

            blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
            blockchain.setStatus(genesis, genesis.getCumulativeDifficulty());

            listener.onBlock(genesis, new ArrayList<>() );

            logger.info("Genesis block loaded");
        } else {
            BlockDifficulty totalDifficulty = blockStore.getTotalDifficultyForHash(bestBlock.getHash().getBytes());
            blockchain.setStatus(bestBlock, totalDifficulty);

            // we need to do this because when bestBlock == null we touch the genesis' state root
            Repository repo = new RepositoryImpl(new Trie(true), null, new TrieStorePoolOnMemory());
            loadRepository(repo);
            updateGenesis(repo);

            logger.info("*** Loaded up to block [{}] totalDifficulty [{}] with stateRoot [{}]",
                    blockchain.getBestBlock().getNumber(),
                    blockchain.getTotalDifficulty().toString(),
                    Hex.toHexString(blockchain.getBestBlock().getStateRoot()));
        }

        String rootHash = config.rootHashStart();
        if (StringUtils.isNotBlank(rootHash)) {

            // update world state by dummy hash
            byte[] rootHashArray = Hex.decode(rootHash);
            logger.info("Loading root hash from property file: [{}]", rootHash);
            this.repository.syncToRoot(rootHashArray);

        } else {

            // Update world state to latest loaded block from db
            // if state is not generated from empty premine list
            // todo this is just a workaround, move EMPTY_TRIE_HASH logic to Trie implementation
            BlockHeader bestBlockHeader = blockchain.getBestBlock().getHeader();
            Keccak256 bestBlockStateRoot = stateRootHandler.translate(bestBlockHeader);
            if (!Arrays.equals(bestBlockStateRoot.getBytes(), EMPTY_TRIE_HASH)) {
                repository.syncToRoot(bestBlockStateRoot.getBytes());
            }
        }
        return blockchain;
    }

    private void updateGenesis(Repository repository) {
        genesis.setStateRoot(repository.getRoot());
        genesis.flushRLP();
    }

    private void loadRepository(Repository repository) {
        // first we need to create the accounts, which creates also the associated ContractDetails
        for (RskAddress accounts : genesis.getAccounts().keySet()) {
                repository.createAccount(accounts);
            }

        // second we create contracts whom only have code modifying the preexisting ContractDetails instance
        for (Map.Entry<RskAddress, byte[]> codeEntry : genesis.getCodes().entrySet()) {
            RskAddress contractAddress = codeEntry.getKey();
            ContractDetails contractDetails = repository.getContractDetails(contractAddress);
            contractDetails.setCode(codeEntry.getValue());
            Map<DataWord, byte[]> contractStorage = genesis.getStorages().get(contractAddress);
            for (Map.Entry<DataWord, byte[]> storageEntry : contractStorage.entrySet()) {
                contractDetails.putBytes(storageEntry.getKey(), storageEntry.getValue());
            }
            repository.updateContractDetails(contractAddress, contractDetails);
        }

        // given the accounts had the proper storage root set from the genesis construction we update the account state
        for (Map.Entry<RskAddress, AccountState> accountEntry : genesis.getAccounts().entrySet()) {
            repository.updateAccountState(accountEntry.getKey(), accountEntry.getValue());
        }
    }
}
