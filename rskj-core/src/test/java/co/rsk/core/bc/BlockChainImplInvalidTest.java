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

package co.rsk.core.bc;

import co.rsk.RskContext;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.validators.BlockValidator;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.core.Transaction;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class BlockChainImplInvalidTest {

    @TempDir
    public Path tempDir;

    private Blockchain blockChain;
    private RskContext objects;

    @BeforeEach
    void setup() {
        objects = new RskTestContext(tempDir) {
            @Override
            protected synchronized RskSystemProperties buildRskSystemProperties() {
                RskSystemProperties rskSystemProperties = super.buildRskSystemProperties();

                ActivationConfig activationConfigSpy = spy(rskSystemProperties.getActivationConfig());
                RskSystemProperties rskSystemPropertiesSpy = spy(rskSystemProperties);

                doReturn(true).when(activationConfigSpy).isActive(eq(ConsensusRule.RSKIP126), anyLong());
                doReturn(activationConfigSpy).when(rskSystemPropertiesSpy).getActivationConfig();

                return rskSystemPropertiesSpy;
            }

            @Override
            protected GenesisLoader buildGenesisLoader() {
                return new TestGenesisLoader(getTrieStore(), "rsk-unittests.json", BigInteger.ZERO, true, true, true);
            }

            @Override
            public BlockValidator buildBlockValidator() {
                return new BlockValidatorImpl(
                        getBlockStore(),
                        getBlockParentDependantValidationRule(),
                        getBlockValidationRule()
                );
            }
        };
        blockChain = objects.getBlockchain();
    }

    @AfterEach
    void tearDown() {
        objects.close();
    }

    @Test
    void addInvalidBlockBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setTransactionsRoot(TestUtils.generateBytes("root",32));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidBlockBadUnclesHash() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        TestUtils.setInternalState(block1.getHeader(), "unclesHash", TestUtils.generateBytes("uncleHAsh",32));

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    void addInvalidMGPBlock() {
        TrieStore trieStore = objects.getTrieStore();
        BlockStore blockStore = objects.getBlockStore();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();
        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(trieStore).addPrevMinGasPriceRule().addTxsMinGasPriceRule();

        validatorBuilder.build();

        Block genesis = blockChain.getBestBlock();

        Block block = new BlockBuilder(null, null, null).minGasPrice(BigInteger.ONE)
                .parent(genesis).build();

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));

        List<Transaction> txs = new ArrayList<>();
        Transaction tx = Transaction
                .builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.valueOf(1L))
                .gasLimit(BigInteger.TEN)
                .destination(Hex.decode("0000000000000000000000000000000000000006"))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(BigInteger.ZERO)
                .build();
        tx.sign(new byte[]{22, 11, 00});
        txs.add(tx);

        block = new BlockBuilder(null, null, null).transactions(txs).minGasPrice(BigInteger.valueOf(11L))
                .parent(genesis).build();

        Assertions.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));
    }

}