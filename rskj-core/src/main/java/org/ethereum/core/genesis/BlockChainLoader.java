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

import co.rsk.core.bc.EventInfoItem;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.listener.EthereumListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

import static org.ethereum.crypto.HashUtil.EMPTY_TRIE_HASH;

/**
 * Created by mario on 13/01/17.
 */
public class BlockChainLoader {

    private static final Logger logger = LoggerFactory.getLogger("general");

    private SystemProperties properties;
    private BlockStore blockStore;
    private Repository repository;
    private Blockchain blockchain;
    private EthereumListener listener;

    public BlockChainLoader(Blockchain blockchain, SystemProperties properties, BlockStore blockStore, Repository repository, EthereumListener listener) {
        this.properties = properties;
        this.blockStore = blockStore;
        this.repository = repository;
        this.blockchain = blockchain;
        this.listener = listener;
    }

    public void loadBlockchain() {

        if (!properties.databaseReset())
            blockStore.load();

        Block bestBlock = blockStore.getBestBlock();
        if (bestBlock == null) {
            logger.info("DB is empty - adding Genesis");

            BigInteger initialNonce = properties.getBlockchainConfig().getCommonConstants().getInitialNonce();
            Genesis genesis = GenesisLoader.loadGenesis(properties.genesisInfo(), initialNonce, true);
            for (ByteArrayWrapper address : genesis.getPremine().keySet()) {
                repository.createAccount(address.getData());
                InitialAddressState initialAddressState = genesis.getPremine().get(address);
                repository.addBalance(address.getData(), initialAddressState.getAccountState().getBalance());
                AccountState accountState = repository.getAccountState(address.getData());
                accountState.setNonce(initialAddressState.getAccountState().getNonce());
                if (initialAddressState.getContractDetails()!=null) {
                    repository.updateContractDetails(address.getData(), initialAddressState.getContractDetails());
                    accountState.setStateRoot(initialAddressState.getAccountState().getStateRoot());
                    accountState.setCodeHash(initialAddressState.getAccountState().getCodeHash());
                }
                repository.updateAccountState(address.getData(), accountState);
            }

            genesis.setStateRoot(repository.getRoot());
            genesis.flushRLP();

            blockStore.saveBlock(genesis, genesis.getCumulativeDifficulty(), true);
            blockchain.setBestBlock(genesis);
            blockchain.setTotalDifficulty(genesis.getCumulativeDifficulty());

            listener.onBlock(genesis, new ArrayList<TransactionReceipt>(),new ArrayList<EventInfoItem>() );
            repository.dumpState(genesis, 0, 0, null);

            logger.info("Genesis block loaded");
        } else {
            BigInteger totalDifficulty = blockStore.getTotalDifficultyForHash(bestBlock.getHash());

            blockchain.setBestBlock(bestBlock);
            blockchain.setTotalDifficulty(totalDifficulty);

            logger.info("*** Loaded up to block [{}] totalDifficulty [{}] with stateRoot [{}]",
                    blockchain.getBestBlock().getNumber(),
                    blockchain.getTotalDifficulty().toString(),
                    Hex.toHexString(blockchain.getBestBlock().getStateRoot()));
        }

        String rootHash = properties.rootHashStart();
        if (StringUtils.isNotBlank(rootHash)) {

            // update world state by dummy hash
            byte[] rootHashArray = Hex.decode(rootHash);
            logger.info("Loading root hash from property file: [{}]", rootHash);
            this.repository.syncToRoot(rootHashArray);

        } else {

            // Update world state to latest loaded block from db
            // if state is not generated from empty premine list
            // todo this is just a workaround, move EMPTY_TRIE_HASH logic to Trie implementation
            if (!Arrays.equals(blockchain.getBestBlock().getStateRoot(), EMPTY_TRIE_HASH)) {
                this.repository.syncToRoot(blockchain.getBestBlock().getStateRoot());
            }
        }
    }
}
