package co.rsk.mine;

import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.ConfigUtils;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.BlockDifficulty;
import co.rsk.core.DifficultyCalculator;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.BlockChainImplTest;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.test.World;
import co.rsk.test.builders.BlockChainBuilder;
import co.rsk.validators.BlockUnclesValidationRule;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.BlockchainConfig;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.FallbackMainNetConfig;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImportResult;
import org.ethereum.core.TransactionPool;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.EthereumImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;

/**
 * Created by SerAdmin on 1/3/2018.
 */
public class MainNetMinerTest {
    private BlockChainImpl blockchain;
    public static DifficultyCalculator DIFFICULTY_CALCULATOR ;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private TestSystemProperties config;

    @Before
    public void setup() {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new FallbackMainNetConfig());
        DIFFICULTY_CALCULATOR = new DifficultyCalculator(config);
        World world = new World();
        blockchain = world.getBlockChain();

    }

    /*
     * This test is probabilistic, but it has a really high chance to pass. We will generate
     * a random block that it is unlikely to pass the Long.MAX_VALUE difficulty, though
     * it may happen once. Twice would be suspicious.
     */
    @Test
    public void submitBitcoinBlockProofOfWorkNotGoodEnough() {
        /* We need a low target */
        BlockChainImpl bc = new BlockChainBuilder().build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(bc);
        gen.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(Long.MAX_VALUE)));
        bc.setStatus(gen, gen.getCumulativeDifficulty());
        World world = new World(bc, gen);
        TransactionPool transactionPool = new TransactionPoolImpl(config, world.getRepository(), world.getBlockChain().getBlockStore(), null, null, null, 10, 100);
        blockchain = world.getBlockChain();

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);

        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                blockchain,
                null,
                DIFFICULTY_CALCULATOR,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
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

    //throws IOException, FileNotFoundException
    public static void saveToFile(byte[] array, File f) {
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(array);
            fos.close();
        } catch (IOException e) {
            System.out.println("Something is wrong when writing to file "+f.getName()+". Aborting");
            System.exit(-1);
        }
    }

    @Test
    public void generateFallbackMinedBlock() throws InterruptedException, IOException {
        // generate private keys for testing now.
        ECKey privateMiningKey0 = ECKey.fromPrivate(BigInteger.TEN);
        ECKey privateMiningKey1 = ECKey.fromPrivate(BigInteger.TEN.add(BigInteger.ONE));

        byte[] privKey0 = privateMiningKey0.getPrivKeyBytes();
        saveToFile(privKey0, new File(folder.getRoot().getCanonicalPath(), "privkey0.bin"));
        byte[] privKey1 = privateMiningKey1.getPrivKeyBytes();
        saveToFile(privKey1, new File(folder.getRoot().getCanonicalPath(), "privkey1.bin"));

        TestSystemProperties tempConfig = new TestSystemProperties() {

            BlockchainNetConfig blockchainNetConfig = config.getBlockchainConfig();

            @Override
            public String fallbackMiningKeysDir() {
                try {
                    return folder.getRoot().getCanonicalPath();
                } catch (Exception e) {}
                return null;
            }

            @Override
            public BlockchainNetConfig getBlockchainConfig() {
                return new BlockchainNetConfig() {
                    @Override
                    public BlockchainConfig getConfigForBlock(long blockNumber) {
                        return blockchainNetConfig.getConfigForBlock(blockNumber);
                    }

                    @Override
                    public Constants getCommonConstants() {
                        return new Constants() {
                            @Override
                            public byte[] getFallbackMiningPubKey0() {
                                return privateMiningKey0.getPubKey();
                            }
                            @Override
                            public byte[] getFallbackMiningPubKey1() {
                                return privateMiningKey1.getPubKey();
                            }
                        };
                    }
                };
            }

        };


        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        MinerServer minerServer = new MinerServerImpl(
                tempConfig,
                ethereumImpl,
                blockchain,
                null,
                DIFFICULTY_CALCULATOR,
                new ProofOfWorkRule(tempConfig).setFallbackMiningEnabled(true),
                blockToMineBuilder(),
                ConfigUtils.getDefaultMiningConfig()
        );
        try {
            minerServer.setFallbackMining(true);

            // Accelerate mining
            ((MinerServerImpl) minerServer).setSecsBetweenFallbackMinedBlocks(1);

            minerServer.start();

            // Blocks are generated auomatically
            // but we can call minerServer.generateFallbackBlock() to generate it manually
            // boolean result = minerServer.generateFallbackBlock();
            // Assert.assertTrue(result);
            long start = System.currentTimeMillis();
            while (((MinerServerImpl) minerServer).getFallbackBlocksGenerated() == 0) {

                if (System.currentTimeMillis() - start > 20 * 1000) {
                    Assert.assertTrue(false);
                }
                Thread.sleep(1000); //

            }

            Mockito.verify(ethereumImpl, Mockito.times(1)).addNewMinedBlock(Mockito.any());
            // mine another
            // NOTE that is NOT using the next block (parity change) because of the blockchain mockito
            // to mine a subsequent block, use a real blockchain, not the mockito.
            minerServer.buildBlockToMine(blockchain.getBestBlock(), false);

            //result = minerServer.generateFallbackBlock();
            //Assert.assertTrue(result);
            start = System.currentTimeMillis();
            while (((MinerServerImpl) minerServer).getFallbackBlocksGenerated() == 1) {
                if (System.currentTimeMillis() - start > 20 * 1000) {
                    Assert.assertTrue(false);
                }
                Thread.sleep(1000); //
            }

            Mockito.verify(ethereumImpl, Mockito.times(2)).addNewMinedBlock(Mockito.any());
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
        BlockChainImpl bc = new BlockChainBuilder().build();
        Genesis gen = (Genesis) BlockChainImplTest.getGenesisBlock(bc);
        gen.getHeader().setDifficulty(new BlockDifficulty(BigInteger.valueOf(300000)));
        bc.setStatus(gen, gen.getCumulativeDifficulty());
        World world = new World(bc, gen);
        blockchain = world.getBlockChain();

        EthereumImpl ethereumImpl = Mockito.mock(EthereumImpl.class);
        Mockito.when(ethereumImpl.addNewMinedBlock(Mockito.any())).thenReturn(ImportResult.IMPORTED_BEST);

        MinerServer minerServer = new MinerServerImpl(
                config,
                ethereumImpl,
                this.blockchain,
                world.getBlockProcessor(),
                DIFFICULTY_CALCULATOR,
                new ProofOfWorkRule(config).setFallbackMiningEnabled(false),
                blockToMineBuilder(),
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
        return new BlockToMineBuilder(
                ConfigUtils.getDefaultMiningConfig(),
                blockchain.getRepository(),
                this.blockchain.getBlockStore(),
                this.blockchain.getTransactionPool(),
                DIFFICULTY_CALCULATOR,
                new GasLimitCalculator(config),
                unclesValidationRule,
                config,
                null
        );
    }
}