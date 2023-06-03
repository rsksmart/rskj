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

import co.rsk.cli.tools.RewindBlocks;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.RepositoryLocator;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Blockchain;
import org.ethereum.util.RskTestFactory;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class BlockchainLoaderTest {

    @TempDir
    public Path tempDir;

    @Test
    void testLoadBlockchainEmptyBlockchain() {
        RskTestFactory objects = new RskTestFactory(tempDir) {
            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "blockchain_loader_genesis.json", BigInteger.ZERO, true, true, true);
            }
        };
        Blockchain blockchain = objects.getBlockchain();// calls loadBlockchain
        RepositorySnapshot repository = objects.getRepositoryLocator().snapshotAt(blockchain.getBestBlock().getHeader());

        TestSystemProperties testSystemProperties = new TestSystemProperties();
        ActivationConfig.ForBlock activations = testSystemProperties.getActivationConfig().forBlock(0);
        int enabledPCCs = PrecompiledContracts.GENESIS_ADDRESSES.size();
        for (ConsensusRule consensusRule:PrecompiledContracts.CONSENSUS_ENABLED_ADDRESSES.values()) {
            if (activations.isActive(consensusRule)) {
                enabledPCCs++;
            }
        }
        int testAccountsSize = 3;
        int genesisAccountKeysSize = enabledPCCs + testAccountsSize; // PCCs + test accounts in blockchain_loader_genesis.json
        Assertions.assertEquals(genesisAccountKeysSize, repository.getAccountsKeys().size());

        RskAddress daba01 = new RskAddress("dabadabadabadabadabadabadabadabadaba0001");
        Assertions.assertEquals(Coin.valueOf(2000), repository.getBalance(daba01));
        Assertions.assertEquals(BigInteger.valueOf(24), repository.getNonce(daba01));

        RskAddress daba02 = new RskAddress("dabadabadabadabadabadabadabadabadaba0002");
        Assertions.assertEquals(Coin.valueOf(1000), repository.getBalance(daba02));
        Assertions.assertEquals(BigInteger.ZERO, repository.getNonce(daba02));

        RskAddress address = new RskAddress("77045e71a7a2c50903d88e564cd72fab11e82051");
        Assertions.assertEquals(Coin.valueOf(10), repository.getBalance(address));
        Assertions.assertEquals(BigInteger.valueOf(25), repository.getNonce(address));
        Assertions.assertEquals(DataWord.ONE, repository.getStorageValue(address, DataWord.ZERO));
        Assertions.assertEquals(DataWord.valueOf(3), repository.getStorageValue(address, DataWord.ONE));
        Assertions.assertEquals(274, Objects.requireNonNull(repository.getCode(address)).length);
    }

    @Test
    void testLoadBlockchainWithInconsistentBlock() {
        RskTestFactory objects = new RskTestFactory(tempDir) {
            @Override
            protected synchronized RepositoryLocator buildRepositoryLocator() {
                RepositoryLocator repositoryLocatorSpy = spy(super.buildRepositoryLocator());

                doReturn(Optional.empty()).when(repositoryLocatorSpy).findSnapshotAt(any());

                return repositoryLocatorSpy;
            }

            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "blockchain_loader_genesis.json", BigInteger.ZERO, true, true, true);
            }
        };

        try {
            objects.getBlockchain(); // calls loadBlockchain
            fail();
        } catch (RuntimeException e) {
            String errorMessage = String.format("Best block is not consistent with the state db. Consider using `%s` cli tool to rewind inconsistent blocks",
                    RewindBlocks.class.getSimpleName());
            assertEquals(errorMessage, e.getMessage());
        }
    }
}
