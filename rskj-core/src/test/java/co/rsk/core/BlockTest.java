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
import co.rsk.core.bc.BlockHashesHelper;
import co.rsk.peg.PegTestUtils;
import co.rsk.remasc.RemascTransaction;
import org.ethereum.TestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockTest {

    private final BlockFactory blockFactory = new BlockFactory(ActivationConfigsForTest.all());

    @Test
    public void testParseRemascTransaction() {
        List<Transaction> txs = new ArrayList<>();

        Transaction txNotToRemasc = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(new ECKey().getAddress())
                .value(BigInteger.valueOf(1000))
                .build();
        txNotToRemasc.sign(new ECKey().getPrivKeyBytes());
        txs.add(txNotToRemasc);

        Transaction txToRemascThatIsNotTheLatestTx = Transaction.builder()
                .nonce(BigInteger.ZERO)
                .gasPrice(BigInteger.ONE)
                .gasLimit(BigInteger.valueOf(21000))
                .destination(PrecompiledContracts.REMASC_ADDR)
                .value(BigInteger.valueOf(1000))
                .build();
        txToRemascThatIsNotTheLatestTx.sign(new ECKey().getPrivKeyBytes());
        txs.add(txToRemascThatIsNotTheLatestTx);

        Transaction remascTx = new RemascTransaction(1);
        txs.add(remascTx);

        BlockHeader newHeader = blockFactory.getBlockHeaderBuilder()
                .setParentHashFromKeccak256(PegTestUtils.createHash3())
                .setEmptyUnclesHash()
                .setCoinbase(TestUtils.randomAddress())
                .setEmptyStateRoot()
                .setTxTrieRoot( BlockHashesHelper.getTxTrieRoot(txs, true))
                .setEmptyLogsBloom()
                .setEmptyReceiptTrieRoot()
                .setDifficultyFromBytes(BigInteger.ONE.toByteArray())
                .setNumber(1)
                .setGasLimit(BigInteger.valueOf(4000000).toByteArray())
                .setGasUsed( 3000000L)
                .setTimestamp(100)
                .setEmptyExtraData()
                .setEmptyMergedMiningForkDetectionData()
                .setMinimumGasPrice(new Coin(BigInteger.TEN))
                .setUncleCount(0)
                .setUmmRoot(new byte[0])
                .build();

        Block block = blockFactory.newBlock(
                newHeader,
                txs,
                Collections.emptyList()
        );

        Block parsedBlock = blockFactory.decodeBlock(block.getEncoded());
        Assertions.assertEquals(ImmutableTransaction.class, parsedBlock.getTransactionsList().get(0).getClass());
        Assertions.assertEquals(ImmutableTransaction.class, parsedBlock.getTransactionsList().get(1).getClass());
        Assertions.assertEquals(RemascTransaction.class, parsedBlock.getTransactionsList().get(2).getClass());
    }

    @Test
    public void sealedBlockHasSealesBlockHeader() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        Assertions.assertTrue(block.getHeader().isSealed());
    }

    @Test
    public void sealedBlockSetStateRoot() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.setStateRoot(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockException ex) {
            Assertions.assertEquals("Sealed block: trying to alter state root", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetTransactionList() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.setTransactionsList(Collections.emptyList());
            Assertions.fail();
        }
        catch (SealedBlockException ex) {
            Assertions.assertEquals("Sealed block: trying to alter transaction list", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningCoinbaseTransaction() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningCoinbaseTransaction(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockException ex) {
            Assertions.assertEquals("Sealed block: trying to alter bitcoin merged mining coinbase transaction", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningHeader() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningHeader(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockException ex) {
            Assertions.assertEquals("Sealed block: trying to alter bitcoin merged mining header", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockSetBitcoinMergedMiningMerkleProof() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.setBitcoinMergedMiningMerkleProof(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockException ex) {
            Assertions.assertEquals("Sealed block: trying to alter bitcoin merged mining Merkle proof", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetStateRoot() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setStateRoot(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter state root", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetReceiptsRoot() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setReceiptsRoot(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter receipts root", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetTransactionsRoot() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setTransactionsRoot(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter transactions root", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetDifficulty() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setDifficulty(RLP.parseBlockDifficulty(new byte[32]));
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter difficulty", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetPaidFees() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setPaidFees(Coin.valueOf(10L));
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter paid fees", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetGasUsed() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setGasUsed(10);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter gas used", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetLogsBloom() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setLogsBloom(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter logs bloom", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetBitcoinMergedMiningHeader() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setBitcoinMergedMiningHeader(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter bitcoin merged mining header", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetBitcoinMergedMiningMerkleProof() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setBitcoinMergedMiningMerkleProof(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter bitcoin merged mining merkle proof", ex.getMessage());
        }
    }

    @Test
    public void sealedBlockHeaderSetBitcoinMergedMiningCoinbaseTransaction() {
        Block block = new BlockGenerator().createBlock(10, 0);

        block.seal();

        try {
            block.getHeader().setBitcoinMergedMiningCoinbaseTransaction(new byte[32]);
            Assertions.fail();
        }
        catch (SealedBlockHeaderException ex) {
            Assertions.assertEquals("Sealed block header: trying to alter bitcoin merged mining coinbase transaction", ex.getMessage());
        }
    }

    @Test
    public void checkTxTrieShouldBeDifferentForDifferentBlock() {
        BlockGenerator blockGenerator = new BlockGenerator();
        Block block1 = blockGenerator.createBlock(10, 1);
        Block block2 = blockGenerator.createBlock(10, 2);
        String trieHash1 = ByteUtil.toHexString(block1.getTxTrieRoot());
        String trieHash2 = ByteUtil.toHexString(block2.getTxTrieRoot());
        Assertions.assertNotEquals(trieHash1, trieHash2);
    }

    @Test
    public void checkTxTrieShouldBeEqualForHeaderAndBody() {
        Block block = new BlockGenerator().createBlock(10, 5);
        byte[] trieHash = block.getTxTrieRoot();
        byte[] trieListHash = BlockHashesHelper.getTxTrieRoot(block.getTransactionsList(), true);
        Assertions.assertArrayEquals(trieHash, trieListHash);
    }

    @Test
    public void createBlockFromHeader() {
        Block block = new BlockGenerator().createBlock(10, 0);
        BlockHeader header = block.getHeader();

        Block result = Block.createBlockFromHeader(header, false);

        Assertions.assertNotNull(result);
        Assertions.assertArrayEquals(header.getHash().getBytes(), result.getHash().getBytes());
    }
}
