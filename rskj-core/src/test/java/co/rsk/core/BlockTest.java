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

package co.rsk.core;


import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.peg.PegTestUtils;
import org.spongycastle.util.encoders.Hex;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.core.Block;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class BlockTest {
    private static final byte[] EMPTY_LIST_HASH = HashUtil.sha3(RLP.encodeList());

    @Test
    public void testParseRemascTransaction() {
        List<Transaction> txs = new ArrayList<>();

        Transaction txNotToRemasc = new Transaction(
                BigInteger.ZERO.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.valueOf(21000).toByteArray(),
                new ECKey().getAddress() ,
                BigInteger.valueOf(1000).toByteArray(),
                null);
        txNotToRemasc.sign(new ECKey().getPrivKeyBytes());
        txs.add(txNotToRemasc);

        Transaction txToRemascThatIsNotTheLatestTx = new Transaction(
                BigInteger.ZERO.toByteArray(),
                BigInteger.ONE.toByteArray(),
                BigInteger.valueOf(21000).toByteArray(),
                Hex.decode(PrecompiledContracts.REMASC_ADDR),
                BigInteger.valueOf(1000).toByteArray(),
                null);
        txToRemascThatIsNotTheLatestTx.sign(new ECKey().getPrivKeyBytes());
        txs.add(txToRemascThatIsNotTheLatestTx);

        Transaction remascTx = new RemascTransaction(1);
        txs.add(remascTx);

        Block block =  new Block(
                PegTestUtils.createHash3().getBytes(),          // parent hash
                EMPTY_LIST_HASH,       // uncle hash
                PegTestUtils.createHash3().getBytes(),            // coinbase
                new Bloom().getData(),          // logs bloom
                BigInteger.ONE.toByteArray(),    // difficulty
                1,
                BigInteger.valueOf(4000000).toByteArray(), // gasLimit
                3000000, // gasUsed
                100, //timestamp
                new byte[0],                    // extraData
                new byte[0],                    // mixHash
                new byte[]{0},         // provisory nonce
                HashUtil.EMPTY_TRIE_HASH,       // receipts root
                BlockChainImpl.calcTxTrie(txs), // transaction root
                HashUtil.EMPTY_TRIE_HASH,    //EMPTY_TRIE_HASH,   // state root
                txs,                            // transaction list
                null,  // uncle list
                BigInteger.TEN.toByteArray()
        );

        Block parsedBlock = new Block(block.getEncoded());
        Assert.assertEquals(Transaction.class, parsedBlock.getTransactionsList().get(0).getClass());
        Assert.assertEquals(Transaction.class, parsedBlock.getTransactionsList().get(1).getClass());
        Assert.assertEquals(RemascTransaction.class, parsedBlock.getTransactionsList().get(2).getClass());
    }

    @Test
    public void sealedBlockAddUncle() {
        Block block = BlockGenerator.createBlock(10, 0);
        Block uncle = BlockGenerator.createBlock(9, 0);

        block.seal();

        try {
            block.addUncle(uncle.getHeader());
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to add uncle", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetStateRoot() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setStateRoot(new byte[32]);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter state root", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetExtraData() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setExtraData(new byte[32]);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter extra data", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetTransactionList() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setTransactionsList(null);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter transaction list", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningCoinbaseTransaction() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningCoinbaseTransaction(new byte[32]);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter bitcoin merged mining coinbase transaction", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningHeader() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningHeader(new byte[32]);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter bitcoin merged mining header", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningMerkleProof() {
        Block block = BlockGenerator.createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningMerkleProof(new byte[32]);
            Assert.fail();
        }
        catch (RuntimeException ex) {
            Assert.assertEquals("Sealed block: trying to alter bitcoin merged mining Merkle proof", ex.getMessage());
        }
    }
}