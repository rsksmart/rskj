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
<<<<<<< HEAD

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import co.rsk.mine.MinerUtils;
import co.rsk.util.DifficultyUtils;
=======
import co.rsk.blockchain.utils.BlockGenerator;
>>>>>>> EventsTrie super-merge
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import static co.rsk.mine.MinerServerImpl.getBitcoinMergedMerkleBranch;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Mikhail Kalinin
 * @since 02.09.2015
 */
public class ProofOfWorkRuleTest {

    private ProofOfWorkRule rule = new ProofOfWorkRule(RskSystemProperties.CONFIG);

    @Test
    public void test_1() {
        // mined block
        Block b = BlockMiner.mineBlock(new BlockGenerator().getBlock(1));
        assertTrue(rule.isValid(b));
    }

    @Ignore
    @Test // invalid block
    public void test_2() {
        // mined block
        Block b = BlockMiner.mineBlock(new BlockGenerator().getBlock(1));
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
        Block b = BlockMiner.mineBlock(new BlockGenerator().getBlock(1));
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
        Block b = BlockMiner.mineBlock(new BlockGenerator().getBlock(1));

        long start = System.currentTimeMillis();
        for (int i = 0; i < iterCnt; i++)
            rule.isValid(b);

        long total = System.currentTimeMillis() - start;

        System.out.println(String.format("Time: total = %d ms, per block = %.2f ms", total, (double) total / iterCnt));
    }

    @Test
    public void test_noRSKTagInCoinbaseTransaction() {
        BlockGenerator blockGenerator = new BlockGenerator();

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), new byte[100]);

        Assert.assertFalse(rule.isValid(b));
    }

    @Test
    public void test_RSKTagInCoinbaseTransactionTooFar() {
        /* This test is about a rsk block, with a compressed coinbase that leaves more than 64 bytes before the start of the RSK tag. */
<<<<<<< HEAD
        BlockGenerator blockGenerator = new BlockGenerator();
        byte[] prefix = new byte[1000];
        byte[] bytes = org.spongycastle.util.Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        // mined block
        Block b = mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(blockGenerator.getBlock(1), bytes);
=======
        Block b = BlockGenerator.decodeBlockBadlyEncoded("f9034ff9034aa06c33d16f87f1311b969bc714b450a2fa11ca883fd65adee93c2bdb1b9b9b6b9ba01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0c430cf78b938432f6b9f7f5890336a028b96804ce28a8442fa8ada47036707e7a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421b9010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000830f000001831e8480808457f2c4bc808080b850711101000000000000000000000000000000000000000000000000000000000000000000274b33d60ca54f2f22390dcda58601bf5da215fd9f01607ea2ec991c475f9781bcc4f257ffff7f21727a0000a70100000001274b33d60ca54f2f22390dcda58601bf5da215fd9f01607ea2ec991c475f97810101b8fd0000000000000140428e85a0dea9d2b73769360697368391f5a8fb26dfb66b8bedad0406eafa2c0756021651401c3006885b54ee7eaeb8d6074d1f1bdae814fbbb840aa73664cbd1ba6bf60bf1d9693a7a531dd72daba2f0ba425bbd0e399e38b2d81de87a83967f7f81846211938a68f534de2681f484da31c14cb2644ec113a244a4d442b03d2f6a8734cd73f0959c426a986bf017b47640313bd85bc64e52534b424c4f434b3a7de9842e2f82423be569b5fdd3184fb14ab467ab20b177d68a773ae0b8145b52ffffffff0100f2052a010000002321033ecddae9656e6aced734115b7485f1c971f71828c00a97b8a7fdce46f7e22cc3ac00000000800ac0c0");
>>>>>>> EventsTrie super-merge

        Assert.assertFalse(rule.isValid(b));
    }

    private static Block mineBlockWithCoinbaseTransactionWithCompressedCoinbaseTransactionPrefix(Block block, byte[] compressed) {
        Sha3Hash blockMergedMiningHash = new Sha3Hash(block.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(block.getDifficultyBI());

        BlockMiner.findNonce(bitcoinMergedMiningBlock, targetBI);

        // We need to clone to allow modifications
        Block newBlock = new Block(block.getEncoded()).cloneBlock();

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        co.rsk.bitcoinj.core.PartialMerkleTree bitcoinMergedMiningMerkleBranch = getBitcoinMergedMerkleBranch(bitcoinMergedMiningBlock);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(org.spongycastle.util.Arrays.concatenate(compressed, blockMergedMiningHash.getBytes()));
        newBlock.setBitcoinMergedMiningMerkleProof(bitcoinMergedMiningMerkleBranch.bitcoinSerialize());

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
