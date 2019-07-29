package co.rsk.pcc.blockheader;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockMiner;
import co.rsk.config.RskMiningConstants;
import co.rsk.config.TestSystemProperties;
import co.rsk.crypto.Keccak256;
import co.rsk.mine.MinerUtils;
import co.rsk.pcc.ExecutionEnvironment;
import co.rsk.test.World;
import co.rsk.util.DifficultyUtils;
import org.bouncycastle.util.Arrays;
import org.ethereum.core.Block;
import org.ethereum.core.BlockFactory;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnvironmentUtils {
    private static final byte[] ADDITIONAL_TAG = {'A', 'L', 'T', 'B', 'L', 'O', 'C', 'K', ':'};
    private static final BigInteger MIN_GAS_PRICE = new BigInteger("500000000000000000");

    private static World world;
    private static TestSystemProperties config;

    public static ExecutionEnvironment getEnvironmentWithBlockchainOfLength(int blockchainLength) {
        World world = new World();
        TestSystemProperties config = new TestSystemProperties();

        buildBlockchainOfLength(world, config, blockchainLength);

        ExecutionEnvironment executionEnvironment = mock(ExecutionEnvironment.class);
        when(executionEnvironment.getBlockStore()).thenReturn(world.getBlockStore());
        when(executionEnvironment.getBlock()).thenReturn(world.getBlockChain().getBestBlock());

        return executionEnvironment;
    }

    private static void buildBlockchainOfLength(World world, TestSystemProperties config, int length) {
        if (length < 0) return;

        for (int i = 0; i < length; i++) {
            Block block = mineBlockWithCoinbaseTransaction(config, world.getBlockChain().getBestBlock());
            world.getBlockChain().tryToConnect(block);
        }
    }

    private static Block mineBlockWithCoinbaseTransaction(TestSystemProperties config, Block parent) {
        BlockGenerator blockGenerator = new BlockGenerator(config.getNetworkConstants(), config.getActivationConfig());
        byte[] prefix = new byte[1000];
        byte[] compressedTag = org.bouncycastle.util.Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG);

        Keccak256 blockMergedMiningHash = new Keccak256(parent.getHashForMergedMining());

        co.rsk.bitcoinj.core.NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, blockMergedMiningHash.getBytes());
        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);

        BigInteger targetBI = DifficultyUtils.difficultyToTarget(parent.getDifficulty());

        new BlockMiner(config.getActivationConfig()).findNonce(bitcoinMergedMiningBlock, targetBI);

        // We need to clone to allow modifications
        Block newBlock = new BlockFactory(config.getActivationConfig()).cloneBlockForModification(
                blockGenerator.createChildBlock(
                        parent, new ArrayList<>(), new ArrayList<>(),
                        parent.getDifficulty().asBigInteger().longValue(),
                        MIN_GAS_PRICE, parent.getGasLimit()
                )
        );

        newBlock.setBitcoinMergedMiningHeader(bitcoinMergedMiningBlock.cloneAsHeader().bitcoinSerialize());

        byte[] merkleProof = MinerUtils.buildMerkleProof(
                config.getActivationConfig(),
                pb -> pb.buildFromBlock(bitcoinMergedMiningBlock),
                newBlock.getNumber()
        );

        byte[] additionalTag = Arrays.concatenate(ADDITIONAL_TAG, blockMergedMiningHash.getBytes());
        byte[] mergedMiningTx = org.bouncycastle.util.Arrays.concatenate(compressedTag, blockMergedMiningHash.getBytes(), additionalTag);

        newBlock.setBitcoinMergedMiningCoinbaseTransaction(mergedMiningTx);
        newBlock.setBitcoinMergedMiningMerkleProof(merkleProof);

        return newBlock;
    }
}
