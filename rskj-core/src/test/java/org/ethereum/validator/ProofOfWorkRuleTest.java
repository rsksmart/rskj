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
import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.mine.ParameterizedNetworkUpgradeTest;
import co.rsk.util.DifficultyUtils;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class ProofOfWorkRuleTest extends ParameterizedNetworkUpgradeTest {

    private ProofOfWorkRule rule;
    private Logger logger;
    private TestSystemProperties config;

    public ProofOfWorkRuleTest(TestSystemProperties config) throws Exception {
        super(config);
        this.config = config;
        logger = Mockito.mock(Logger.class);
        setFinalStatic(ProofOfWorkRule.class.getDeclaredField("logger"), logger);
        this.rule = new ProofOfWorkRule(config).setFallbackMiningEnabled(false);
    }

    @Test
    public void test_1() {
        // mined block
        Block b = new BlockMiner(config).mineBlock(new BlockGenerator(config).getBlock(1));
        assertTrue(rule.isValid(b));
    }

    @Ignore
    @Test // invalid block
    public void test_2() {
        // mined block
        Block b = new BlockMiner(config).mineBlock(new BlockGenerator(config).getBlock(1));
        byte[] mergeMiningHeader = b.getBitcoinMergedMiningHeader();
        // TODO improve, the mutated block header could be still valid
        mergeMiningHeader[0]++;
        b.setBitcoinMergedMiningHeader(mergeMiningHeader);
        assertFalse(rule.isValid(b));
    }

    // This test must be moved to the appropiate place
    @Test
    public void test_RLPEncoding() {
        // mined block
        Block b = new BlockMiner(config).mineBlock(new BlockGenerator(config).getBlock(1));
        byte[] lastField = b.getBitcoinMergedMiningCoinbaseTransaction(); // last field
        b.flushRLP();// force re-encode
        byte[] encoded = b.getEncoded();
        Block b2 = new Block(encoded);
        byte[] lastField2 = b2.getBitcoinMergedMiningCoinbaseTransaction(); // last field
        b2.flushRLP();// force re-encode
        byte[] encoded2 = b2.getEncoded();
        Assert.assertTrue(Arrays.equals(encoded,encoded2));
        Assert.assertTrue(Arrays.equals(lastField,lastField2));
    }

    @Ignore
    @Test // stress test
    public void test_3() {
        int iterCnt = 1_000_000;

        // mined block
        Block b = new BlockMiner(config).mineBlock(new BlockGenerator(config).getBlock(1));

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterCnt; i++)
            rule.isValid(b);

        long total = System.currentTimeMillis() - start;

        System.out.println(String.format("Time: total = %d ms, per block = %.2f ms", total, (double) total / iterCnt));
    }

    @Test
    public void test_noRSKTagInCoinbaseTransaction() {
        BlockGenerator blockGenerator = new BlockGenerator(config);

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), new byte[100]);

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void test_RSKTagInCoinbaseTransactionTooFar() {
        /* This test is about a rsk block, with a compressed coinbase that leaves more than 64 bytes before the start of the RSK tag. */
        BlockGenerator blockGenerator = new BlockGenerator(config);
        byte[] prefix = new byte[1000];
        byte[] bytes = org.bouncycastle.util.Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), bytes);

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void test_BlockHeaderWithEmptyBitcoinMergedMiningMerkleProof() {
        // If logger.warn is called using the specific signature of the merkle proof validator, check if the message is what we expect it to be
        doAnswer(invocation -> {
            assertTrue(invocation.getArgumentAt(0, String.class).startsWith("Merkle proof can't be validated. Header"));
            if (!config.getBlockchainConfig().getConfigForBlock(1).isRskip92()) {
                // In Bamboo we were checking that the PMT had the appropiate size (i.e. empty is not appropiate)
                assertTrue(invocation.getArgumentAt(2, Exception.class).getMessage().startsWith("Partial merkle tree does not have the expected size"));
            } else {
                // In Orchid should fail only if the PMT is null
                assertTrue(invocation.getArgumentAt(2, Exception.class).getClass() == NullPointerException.class);
            }
            return null;
        }).when(logger).warn(any(String.class), any(String.class), any(Exception.class));

        // First test with empty Merkle proof
        BlockHeader data = new BlockHeader(
                new byte[] {}, new byte[] {}, RskAddress.nullAddress().getBytes(),
                new byte[] {}, new byte[] { 0,1 }, 1,
                new byte[] {}, 1,0,
                new byte[] {}, new byte[] {1} /*bitcoinMergedMiningHeader adding a fake value to force mergedMining data to be serialized*/, new byte[] {} /*bitcoinMergedMiningMerkleProof is empty*/,
                new byte[] { }, new byte[] {}, 0);

        BlockHeader blockHeader = new BlockHeader(data.getEncoded(), false);

        // This header is not valid so it should fail the validation
        Assert.assertFalse(this.rule.isValid(blockHeader));

        // Second test with NULL Merkle proof
        data = new BlockHeader(
                new byte[] {}, new byte[] {}, RskAddress.nullAddress().getBytes(),
                new byte[] {}, new byte[] { 0,1 }, 1,
                new byte[] {}, 1,0,
                new byte[] {}, new byte[] {} , new byte[] {}, // as I don't set bitcoinMergedMiningHeader, it won't serialize bitcoinMergedMiningMerkleProof thus the instance won't have it set
                new byte[] { }, new byte[] {}, 0);

        blockHeader = new BlockHeader(data.getEncoded(), false);

        // This header is not valid so it should fail the validation
        Assert.assertFalse(this.rule.isValid(blockHeader));
    }

    private Block mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(Block block, byte[] compressed) {
        Keccak256 blockMergedMiningHash = new Keccak256(block.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficulty());

        new BlockMiner(config).findNonce(bitcoinMergedMiningBlock, targetBI);

        // We need to clone to allow modifications
        Block newBlock = new Block(block.getEncoded()).cloneBlock();

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                config.getBlockchainConfig(),
                pb -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                newBlock.getNumber()
        );

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(org.bouncycastle.util.Arrays.concatenate(compressed, blockMergedMiningHash.getBytes()));
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }

    static void setFinalStatic(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, newValue);
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
