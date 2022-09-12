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
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.util.DifficultyUtils;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;


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
        Assertions.assertTrue(rule.isValid(b));
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
        Assertions.assertTrue(Arrays.equals(encoded,encoded2));
        Assertions.assertTrue(Arrays.equals(lastField,lastField2));
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

        Assertions.assertTrue(total > 0);

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
        Assertions.assertTrue(rule.isValid(newBlock1));
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
