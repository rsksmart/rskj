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

package org.ethereum.validator;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.MiningConfig;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerServerImpl;
import co.rsk.mine.MinerUtils;
import co.rsk.util.DifficultyUtils;
import co.rsk.validators.ProofOfWorkRule;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.BlockHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public abstract class ProofOfWorkRuleTest {

    private ActivationConfig activationConfig;
    private Constants networkConstants;
    private ProofOfWorkRule rule;
    private BlockFactory blockFactory;

    protected void setUp(TestSystemProperties config) {
        this.rule = new ProofOfWorkRule(config).setFallbackMiningEnabled(false);
        this.activationConfig = config.getActivationConfig();
        this.networkConstants = config.getNetworkConstants();
        this.blockFactory = new BlockFactory(activationConfig);
    }

    @Test
    void test_1() {
        // mined block
        Block b = new BlockMiner(activationConfig).mineBlock(new BlockGenerator(networkConstants, activationConfig).getBlock(1));
        assertTrue(rule.isValid(b));
    }

    @Disabled("TODO improve, the mutated block header could be still valid")
    // invalid block
    @Test
    void test_2() {
        // mined block
        Block b = new BlockMiner(activationConfig).mineBlock(new BlockGenerator(networkConstants, activationConfig).getBlock(1));
        byte[] mergeMiningHeader = b.getBitcoinMergedMiningHeader();
        // TODO improve, the mutated block header could be still valid
        mergeMiningHeader[0]++;
        b.setBitcoinMergedMiningHeader(mergeMiningHeader);
        Assertions.assertFalse(rule.isValid(b));
    }

    // This test must be moved to the appropiate place
    @Test
    void test_RLPEncoding() {
        // mined block
        Block b = new BlockMiner(activationConfig).mineBlock(new BlockGenerator(networkConstants, activationConfig).getBlock(1));
        byte[] lastField = b.getBitcoinMergedMiningCoinbaseTransaction(); // last field
        b.flushRLP();// force re-encode
        byte[] encoded = b.getEncoded();
        Block b2 = blockFactory.decodeBlock(encoded);
        byte[] lastField2 = b2.getBitcoinMergedMiningCoinbaseTransaction(); // last field
        b2.flushRLP();// force re-encode
        byte[] encoded2 = b2.getEncoded();
        assertTrue(Arrays.equals(encoded,encoded2));
        assertTrue(Arrays.equals(lastField,lastField2));
    }

    @Disabled("stress test")
    @Test
    @SuppressWarnings("squid:S2699")
    void test_3() {
        int iterCnt = 1_000_000;

        // mined block
        Block b = new BlockMiner(activationConfig).mineBlock(new BlockGenerator(networkConstants, activationConfig).getBlock(1));

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterCnt; i++)
            rule.isValid(b);

        long total = System.currentTimeMillis() - start;

        assertTrue(total > 0);

        System.out.printf("Time: total = %d ms, per block = %.2f ms%n", total, (double) total / iterCnt);
    }

    @Test
    void test_noRSKTagInCoinbaseTransaction() {
        BlockGenerator blockGenerator = new BlockGenerator(networkConstants, activationConfig);

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), new byte[100]);

        Assertions.assertFalse(rule.isValid(b));
    }

    @Test
    void test_RSKTagInCoinbaseTransactionTooFar() {
        /* This test is about a rsk block, with a compressed coinbase that leaves more than 64 bytes before the start of the RSK tag. */
        BlockGenerator blockGenerator = new BlockGenerator(networkConstants, activationConfig);
        byte[] prefix = new byte[1000];
        byte[] bytes = org.bouncycastle.util.Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), bytes);

        Assertions.assertFalse(rule.isValid(b));
    }

    @Test
    void test_validAfterEncodingAndDecoding() {
        BlockHeader header = blockFactory.getBlockHeaderBuilder()
                .setNumber(MiningConfig.REQUIRED_NUMBER_OF_BLOCKS_FOR_FORK_DETECTION_CALCULATION)
                .setIncludeForkDetectionData(true)
                .build();
        Block block = blockFactory.newBlock(header, Collections.emptyList(), Collections.emptyList(), false);
        Block minedBlock = new BlockMiner(activationConfig).mineBlock(block);
        BlockHeader minedBlockHeader = minedBlock.getHeader();

        byte[] encodedCompressed = minedBlockHeader.getFullEncoded();
        BlockHeader decodedHeader = blockFactory.decodeHeader(encodedCompressed, false);

        assertTrue(rule.isValid(decodedHeader));
    }

    @Test
    void bytesAfterMergedMiningHashAreLessThan128() {
        RskSystemProperties props = new TestSystemProperties() {
            @Override
            public ActivationConfig getActivationConfig() {
                return ActivationConfigsForTest.all();
            }
        };
        ActivationConfig config = props.getActivationConfig();
        Constants networkConstants = props.getNetworkConstants();

        BlockGenerator blockGenerator = new BlockGenerator(networkConstants, config);

        Block newBlock = blockGenerator.getBlock(1);
        while (newBlock.getNumber() < 455)
            newBlock = blockGenerator.createChildBlock(newBlock);

        String output1 = "6a24b9e11b6de9aa87561948d72e494fed2fb56bf8fd4193425f9350037f34dec5b13be7a86e";
        String output2 = "aa21a9ed90a5e7d6d8093d20aa54fb01f57da374e016d4a01ddec0210088675e5e3fee4e";

        byte[] mergedMiningLink = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, newBlock.getHashForMergedMining());
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinCoinbaseTransaction(params, mergedMiningLink);
        bitcoinMergedMiningCoinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, bitcoinMergedMiningCoinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), Hex.decode(output1)));
        bitcoinMergedMiningCoinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, bitcoinMergedMiningCoinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), Hex.decode(output2)));

        Block newBlock1 = new BlockMiner(config).mineBlock(newBlock, bitcoinMergedMiningCoinbaseTransaction);
        ProofOfWorkRule rule = new ProofOfWorkRule(props);
        assertTrue(rule.isValid(newBlock1));
    }

    @Test
    void bytesAfterMergedMiningHashAreMoreThan128() {
        // This test shows that a Mining Pools can not add more than 2 outputs with 36 bytes each,
        // otherwise solutions will not be taken as valid.

        RskSystemProperties props = new TestSystemProperties() {
            @Override
            public ActivationConfig getActivationConfig() {
                return ActivationConfigsForTest.all();
            }
        };
        ActivationConfig config = props.getActivationConfig();
        Constants networkConstants = props.getNetworkConstants();

        BlockGenerator blockGenerator = new BlockGenerator(networkConstants, config);

        Block newBlock = blockGenerator.getBlock(1);
        // fork detection data is for heights > 449
        while (newBlock.getNumber() < 455)
            newBlock = blockGenerator.createChildBlock(newBlock);

        String output1 = "6a24b9e11b6de9aa87561948d72e494fed2fb56bf8fd4193425f9350037f34dec5b13be7";
        String output2 = "aa21a9ed90a5e7d6d8093d20aa54fb01f57da374e016d4a01ddec0210088675e5e3fee4e";
        String output3 = "1111a9ed90a5e7d6d8093d20aa54fb01f57da374e016d4a01ddec0210088675e5e3fee4e";
        byte[] mergedMiningLink = org.bouncycastle.util.Arrays.concatenate(RskMiningConstants.RSK_TAG, newBlock.getHashForMergedMining());
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = MinerUtils.getBitcoinCoinbaseTransaction(params, mergedMiningLink);
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), Hex.decode(output1)));
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), Hex.decode(output2)));
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), Hex.decode(output3)));

        Block block = new BlockMiner(config).mineBlock(newBlock, coinbaseTransaction);
        ProofOfWorkRule rule = new ProofOfWorkRule(props);
        Assertions.assertFalse(rule.isValid(block));
    }

    @Test
    void test_fullBtcBlockAsHeader_rejectedByRskj() {
        // We assume that RSKIP98 will be active, if not, we skip this test with this assumption
        Assumptions.assumeTrue(activationConfig.isActive(ConsensusRule.RSKIP98, 1));
        // given
        BlockGenerator blockGenerator = new BlockGenerator(networkConstants, activationConfig);
        Block block = blockGenerator.getBlock(1);

        // Build a valid merge-mined BTC block (header + coinbase transaction)
        byte[] mergedMiningLink = org.bouncycastle.util.Arrays.concatenate(
                RskMiningConstants.RSK_TAG, block.getHashForMergedMining());
        co.rsk.bitcoinj.core.NetworkParameters params = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTx =
                MinerUtils.getBitcoinCoinbaseTransaction(params, mergedMiningLink);
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
                MinerUtils.getBitcoinMergedMiningBlock(params, coinbaseTx);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());
        new BlockMiner(activationConfig).findNonce(btcBlock, targetBI);

        // BtcBlock.parse() always hashes only the first 80 bytes (cursor - offset after reading
        // version + prevHash + merkleRoot + time + bits + nonce = 4+32+32+4+4+4 = 80).
        byte[] headerOnly = btcBlock.cloneAsHeader().bitcoinSerialize();
        byte[] fullBlock = btcBlock.bitcoinSerialize();

        Assertions.assertEquals(80, headerOnly.length);
        Assertions.assertTrue(fullBlock.length > 80,
                "Full BTC block should be larger than 80 bytes, was: " + fullBlock.length);

        co.rsk.bitcoinj.core.BtcBlock parsedFromHeader = params.getDefaultSerializer().makeBlock(headerOnly);
        co.rsk.bitcoinj.core.BtcBlock parsedFromFull = params.getDefaultSerializer().makeBlock(fullBlock);

        // bitcoinj hashes only the first 80 bytes, so PoW hash is identical regardless of payload size
        Assertions.assertEquals(parsedFromHeader.getHash(), parsedFromFull.getHash(),
                "PoW hash must be identical regardless of whether transactions are included");
        Assertions.assertEquals(parsedFromHeader.getMerkleRoot(), parsedFromFull.getMerkleRoot(),
                "Merkle root must be identical");

        // hashForMergedMining uses getEncoded(false, false, true) which EXCLUDES merged mining fields.
        byte[] hashForMergedMiningBefore = block.getHashForMergedMining();

        // Normal mining uses cloneAsHeader().bitcoinSerialize() (80 bytes).
        // Here we skip cloneAsHeader() and set the full BTC block (header + txs).
        Block newBlock = blockFactory.cloneBlockForModification(block);
        newBlock.setBitcoinMergedMiningHeader(fullBlock); // full BTC block, not header-only

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                activationConfig,
                pb -> pb.buildFromBlock(btcBlock),
                newBlock.getNumber());
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        coinbaseTx = btcBlock.getTransactions().get(0);
        newBlock.setBitcoinMergedMiningCoinbaseTransaction(
                MinerServerImpl.compressCoinbase(coinbaseTx.bitcoinSerialize()));

        Assertions.assertArrayEquals(hashForMergedMiningBefore, newBlock.getHashForMergedMining(),
                "hashForMergedMining must NOT change when BTC header is replaced with full block — " +
                "it excludes merged mining fields (getEncoded(false, false, true))");

        // ProofOfWorkRule enforces bitcoinMergedMiningHeader.length == 80
        Assertions.assertFalse(rule.isValid(newBlock),
                "RSKj must reject a full BTC block as the merged mining header");

        // BlockFactory.decodeBlock() also rejects oversized BTC headers during deserialization,
        // preventing malformed blocks from propagating through the P2P network.
        byte[] encoded = newBlock.getEncoded();
        Assertions.assertThrows(IllegalArgumentException.class, () -> blockFactory.decodeBlock(encoded),
                "BlockFactory must reject an oversized BTC header during deserialization");
    }

    @Test
    void test_tamperedTxsIntoBtcHeaderAreRejected() {
        // We assume that RSKIP98 will be active, if not, we skip this test with this assumption
        Assumptions.assumeTrue(activationConfig.isActive(ConsensusRule.RSKIP98, 1));
        // === Step 1: Honest miner produces a valid block ===
        Block honestBlock = new BlockMiner(activationConfig)
                .mineBlock(new BlockGenerator(networkConstants, activationConfig).getBlock(1));
        assertTrue(rule.isValid(honestBlock), "Honest block must be valid");

        // === Step 2: Honest block is RLP-encoded and sent over P2P ===
        byte[] honestBlockRlp = honestBlock.getEncoded();

        // === Step 3: Receives the RLP and decodes it ===
        Block changedDecodedBlock = blockFactory.decodeBlock(honestBlockRlp);
        byte[] originalBtcHeader = changedDecodedBlock.getBitcoinMergedMiningHeader();
        Assertions.assertEquals(80, originalBtcHeader.length,
                "Original block has the expected 80-byte BTC header");

        // === Step 4: Attempt to appends BTC transaction data to the header ===
        byte[] tamperedBtcHeader = org.bouncycastle.util.Arrays.concatenate(
                originalBtcHeader, new byte[]{0x00});

        // === Step 5: Attempt to constructs the tampered block ===
        Block tamperedBlock = blockFactory.cloneBlockForModification(changedDecodedBlock);
        tamperedBlock.setBitcoinMergedMiningHeader(tamperedBtcHeader);
        tamperedBlock.setBitcoinMergedMiningCoinbaseTransaction(
                changedDecodedBlock.getBitcoinMergedMiningCoinbaseTransaction());
        tamperedBlock.setBitcoinMergedMiningMerkleProof(
                changedDecodedBlock.getBitcoinMergedMiningMerkleProof());

        // hashForMergedMining is unchanged — the PoW proof is still technically valid,
        // but the size check now catches the tampered header before PoW is evaluated.
        Assertions.assertArrayEquals(
                honestBlock.getHeader().getHashForMergedMining(),
                tamperedBlock.getHeader().getHashForMergedMining(),
                "hashForMergedMining is identical — but size check catches the tamper before PoW evaluation");

        // === Step 6: ProofOfWorkRule rejects the tampered block ===
        Assertions.assertFalse(rule.isValid(tamperedBlock),
                "ProofOfWorkRule must reject the tampered block with oversized BTC header");

        // === Step 7: BlockFactory also rejects during deserialization ===
        byte[] tamperedBlockRlp = tamperedBlock.getEncoded();
        Assertions.assertThrows(IllegalArgumentException.class, () -> blockFactory.decodeBlock(tamperedBlockRlp),
                "BlockFactory must reject the tampered block during deserialization");
    }

    private Block mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(Block block, byte[] compressed) {
        return mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(block, compressed, this.activationConfig);
    }

    private Block mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(Block block, byte[] compressed, ActivationConfig activationConfig) {
        Keccak256 blockMergedMiningHash = new Keccak256(block.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());

        new BlockMiner(activationConfig).findNonce(bitcoinMergedMiningBlock, targetBI);

        Block newBlock = blockFactory.cloneBlockForModification(block);

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                activationConfig,
                pb -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                newBlock.getNumber()
        );

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(org.bouncycastle.util.Arrays.concatenate(compressed, blockMergedMiningHash.getBytes()));
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }

    private static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithoutRSKTag(co.rsk.bitcoinj.core.NetworkParameters params, byte[] blockHashForMergedMining) {
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = new co.rsk.bitcoinj.core.BtcTransaction(params);
        //Add a random number of random bytes before the RSK tag
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[1000];
        random.nextBytes(bytes);
        co.rsk.bitcoinj.core.TransactionInput ti = new co.rsk.bitcoinj.core.TransactionInput(params, coinbaseTransaction, bytes);
        coinbaseTransaction.addInput(ti);
        ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
        co.rsk.bitcoinj.core.BtcECKey key = new co.rsk.bitcoinj.core.BtcECKey();
        try {
            co.rsk.bitcoinj.script.Script.writeBytes(scriptPubKeyBytes, key.getPubKey());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        scriptPubKeyBytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG);
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(50, 0), scriptPubKeyBytes.toByteArray()));
        return coinbaseTransaction;
    }
}
