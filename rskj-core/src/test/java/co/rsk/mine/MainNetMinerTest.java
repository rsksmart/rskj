package co.rsk.mine;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.ConfigUtils;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.TransactionExecutorFactory;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.db.StateRootHandler;
import co.rsk.net.NodeBlockProcessor;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.*;
import org.ethereum.db.BlockStore;
import org.ethereum.facade.EthereumImpl;
import org.ethereum.util.RskTestFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.time.Clock;

/**
 * Created by SerAdmin on 1/3/2018.
 */
public class MainNetMinerTest {
    private Blockchain blockchain;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private TestSystemProperties config;
    private TransactionPool transactionPool;
    private BlockStore blockStore;
    private NodeBlockProcessor blockProcessor;
    private Repository repository;
    private BlockFactory blockFactory;
    private StateRootHandler stateRootHandler;
    private TransactionExecutorFactory transactionExecutorFactory;

    @Before
    public void setup() {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new BlockchainNetConfig(Constants.mainnet(), ActivationConfigsForTest.all()));
        RskTestFactory factory = new RskTestFactory(config);
        blockchain = factory.getBlockchain();
        transactionPool = factory.getTransactionPool();
        blockStore = factory.getBlockStore();
        blockProcessor = factory.getNodeBlockProcessor();
        repository = factory.getRepository();
        blockFactory = factory.getBlockFactory();
        stateRootHandler = factory.getStateRootHandler();
        transactionExecutorFactory = factory.getTransactionExecutorFactory();
    }

    /*
     * This test is probabilistic, but it has a really high chance to pass. We will generate
     * a random block that it is unlikely to pass the Long.MAX_VALUE difficulty, though
     * it may happen once. Twice would be suspicious.
     */
    @Test
    public void submitBitcoinBlockProofOfWorkNotGoodEnough() {
        /* We need a low target */
        BlockChainImpl blockchain = new BlockChainBuilder().build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(blockchain);
        gen.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(Long.MAX_VALUE)));
        blockchain.setStatus(gen, gen.getCumulativeDifficulty());

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                null,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.start();
            MinerWork work = minerServer.getWork();

            co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

            bitcoinMergedMiningBlock.setNonce(2);

            SubmitBlockResult result = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

            Assert.assertEquals("ERROR", result.getStatus());
            Assert.assertNull(result.getBlockInfo());

            Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());
        } finally {
            minerServer.stop();
        }
    }

    /*
     * This test is much more likely to fail than the
     * submitBitcoinBlockProofOfWorkNotGoodEnough test. Even then
     * it should almost never fail.
     */
    @Test
    public void submitBitcoinBlockInvalidBlockDoesntEliminateCache() {
        //////////////////////////////////////////////////////////////////////
        // To make this test work we need a special network spec with
        // medium minimum difficulty (this is not the mainnet nor the regnet)
        ////////////////////////////////////////////////////////////////////
        /* We need a low, but not too low, target */
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(blockchain);
        gen.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(300000)));
        blockchain.setStatus(gen, gen.getCumulativeDifficulty());

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                blockProcessor,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
                clock,
                blockFactory,
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
        minerServer.start();
        MinerWork work = minerServer.getWork();

        co.rsk.bitcoinj.core.BtcBlock bitcoinMergedMiningBlock = getMergedMiningBlock(work);

        bitcoinMergedMiningBlock.setNonce(1);

        // Try to submit a block with invalid PoW, this should not eliminate the block from the cache
        SubmitBlockResult result1 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("ERROR", result1.getStatus());
        Assert.assertNull(result1.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(0)).addNewMinedBlock(Mockito.any());

        // Now try to submit the same block, this should work fine since the block remains in the cache

        // This WON't work in mainnet because difficulty is HIGH
        /*---------------------------------------------------------
        findNonce(work, bitcoinMergedMiningBlock);

        SubmitBlockResult result2 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("OK", result2.getStatus());
        Assert.assertNotNull(result2.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());

        // Finally, submit the same block again and validate that addNewMinedBlock is called again
        SubmitBlockResult result3 = minerServer.submitBitcoinBlock(work.getBlockHashForMergedMining(), bitcoinMergedMiningBlock);

        Assert.assertEquals("OK", result3.getStatus());
        Assert.assertNotNull(result3.getBlockInfo());
        Mockito.verify(ethereumImpl, Mockito.times(2)).addNewMinedBlock(Mockito.any());
        -------------------------------*/
        } finally {
            minerServer.stop();
        }
    }

    private co.rsk.bitcoinj.core.BtcBlock getMergedMiningBlock(MinerWork work) {
        NetworkParameters bitcoinNetworkParameters = co.rsk.bitcoinj.params.RegTestParams.get();
        co.rsk.bitcoinj.core.BtcTransaction bitcoinMergedMiningCoinbaseTransaction = MinerUtils.getBitcoinMergedMiningCoinbaseTransaction(bitcoinNetworkParameters, work);
        return MinerUtils.getBitcoinMergedMiningBlock(bitcoinNetworkParameters, bitcoinMergedMiningCoinbaseTransaction);
    }

    private BlockToMineBuilder blockToMineBuilder() {
        BlockUnclesValidationRule unclesValidationRule = Mockito.mock(BlockUnclesValidationRule.class);
        Mockito.when(unclesValidationRule.isValid(Mockito.any())).thenReturn(true);
        MinerClock clock = new MinerClock(true, Clock.systemUTC());
        return new BlockToMineBuilder(
                ConfigUtils.getDefaultMiningConfig(),
                repository,
                blockStore,
                transactionPool,
                new DifficultyCalculator(config.getActivationConfig(), config.getNetworkConstants()),
                new GasLimitCalculator(config.getNetworkConstants()),
                unclesValidationRule,
                clock,
                blockFactory,
                stateRootHandler,
                transactionExecutorFactory
        );
    }
}