/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package co.rsk.pcc.blockheader;

import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.peg.performance.ExecutionStats;
import co.rsk.peg.performance.PrecompiledContractPerformanceTestCase;
import co.rsk.test.World;
import co.rsk.util.DifficultyUtils;
import org.bouncycastle.util.Arrays;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.LinkedList;

@Disabled
public class GetCoinbasePerformanceTestCase extends PrecompiledContractPerformanceTestCase {

    @Test
    public void getCoinbase() throws IOException, VMException {
        ExecutionStats stats = new ExecutionStats("getCoinbase");

        EnvironmentBuilder environmentBuilder = (int executionIndex, TxBuilder txBuilder, int height) -> {
                World world = buildWorld(6000, 500, 6);
                BlockHeaderContract contract = new BlockHeaderContract(activationConfig, new RskAddress("0000000000000000000000000000000001000010"));
                contract.init(txBuilder.build(executionIndex), world.getBlockChain().getBestBlock(), world.getRepository(), world.getBlockStore(), null, new LinkedList<>());

                return EnvironmentBuilder.Environment.withContract(contract);
        };

        doGetCoinbase(environmentBuilder, stats, 4000);

        BlockHeaderPerformanceTest.addStats(stats);
    }

    private void doGetCoinbase(EnvironmentBuilder environmentBuilder, ExecutionStats stats, int numCases) throws IOException, VMException {
        CallTransaction.Function function = CallTransaction.Function.fromSignature(
                "getCoinbaseAddress",
                new String[]{"int256"},
                new String[]{"bytes"}
        );

        ABIEncoder abiEncoder = (int executionIndex) -> function.encode(3999);

        executeAndAverage(
                "getCoinbase",
                numCases,
                environmentBuilder,
                abiEncoder,
                Helper.getZeroValueTxBuilder(new ECKey()),
                Helper.getRandomHeightProvider(10),
                stats,
                (EnvironmentBuilder.Environment environment, byte[] result) -> {
                    Object[] decodedResult = function.decodeResult(result);
                    Assertions.assertEquals(byte[].class, decodedResult[0].getClass());
                    byte[] address = (byte[]) decodedResult[0];
                    Assertions.assertTrue(0== address.length || 20 == address.length);
                }
        );
    }

    private World buildWorld(int numBlocks, int txPerBlock, int unclesPerBlock){
        World world = new World();

        for (int i = 0; i < numBlocks; i++) {
            Block block = mineBlock(world.getBlockChain().getBestBlock(), txPerBlock, unclesPerBlock);
            world.getBlockChain().tryToConnect(block);
        }

        return world;
    }

    private Block mineBlock(Block parent, int txPerBlock, int unclesPerBlock) {
        BlockGenerator blockGenerator = new BlockGenerator(constants, activationConfig);
        byte[] prefix = new byte[1000];
        byte[] compressedTag = Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        Keccak256 mergedMiningHash = new Keccak256(parent.getHashForMergedMining());

        NetworkParameters networkParameters = RegTestParams.get();
        BtcTransaction mergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(networkParameters, mergedMiningHash.getBytes());
        BtcBlock mergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(networkParameters, mergedMiningCoinbaseTransaction);

        BigInteger targetDifficulty = DifficultyUtils.difficultyToTarget(parent.getDifficulty());

        new BlockMiner(activationConfig).findNonce(mergedMiningBlock, targetDifficulty);

        // We need to clone to allow modifications
        Block newBlock = new BlockFactory(activationConfig).cloneBlockForModification(
                blockGenerator.createChildBlock(parent, txPerBlock, parent.getDifficulty().asBigInteger().longValue())
        );

        newBlock.setBitcoinMergedMiningHeader(mergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                activationConfig,
                pb -> pb.buildFromBlock(mergedMiningBlock),
                newBlock.getNumber()
        );

        byte[] additionalTag = Arrays.concatenate(new byte[]{'A','L','T','B','L','O','C','K',':'}, mergedMiningHash.getBytes());
        byte[] mergedMiningTx = org.bouncycastle.util.Arrays.concatenate(compressedTag, mergedMiningHash.getBytes(), additionalTag);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(mergedMiningTx);
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }
}
