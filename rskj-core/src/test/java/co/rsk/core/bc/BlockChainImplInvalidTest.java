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
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.test.builders.BlockBuilder;
import co.rsk.trie.TrieStore;
import co.rsk.validators.BlockValidator;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.*;
import org.ethereum.core.genesis.GenesisLoader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockStore;
import org.ethereum.util.RskTestContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BlockChainImplInvalidTest {
    private Blockchain blockChain;
    private RskContext objects;

    @Before
    public void setup() {
        objects = new RskTestContext(new String[0]) {
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

    @Test
    public void addInvalidBlockBadStateRoot() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        block1.getHeader().setTransactionsRoot(HashUtil.randomHash());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidBlockBadUnclesHash() {
        Block genesis = blockChain.getBestBlock();
        Block block1 = new BlockGenerator().createChildBlock(genesis);

        Whitebox.setInternalState(block1.getHeader(), "unclesHash", HashUtil.randomHash());

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block1));
    }

    @Test
    public void addInvalidMGPBlock() {
        TrieStore trieStore = objects.getTrieStore();
        BlockStore blockStore = objects.getBlockStore();

        BlockValidatorBuilder validatorBuilder = new BlockValidatorBuilder();
        validatorBuilder.addBlockRootValidationRule().addBlockUnclesValidationRule(blockStore)
                .addBlockTxsValidationRule(trieStore).addPrevMinGasPriceRule().addTxsMinGasPriceRule();

        validatorBuilder.build();

        Block genesis = blockChain.getBestBlock();

        Block block = new BlockBuilder(null, null, null).minGasPrice(BigInteger.ONE)
                .parent(genesis).build();

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));

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

        Assert.assertEquals(ImportResult.INVALID_BLOCK, blockChain.tryToConnect(block));
    }

}
