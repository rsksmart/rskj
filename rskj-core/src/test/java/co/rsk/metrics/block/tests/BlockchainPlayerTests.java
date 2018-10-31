package co.rsk.metrics.block.tests;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.BlockchainPlayer;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import co.rsk.metrics.profilers.impl.ExecutionProfiler;
import org.ethereum.config.DefaultConfig;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.db.BlockStore;
import org.junit.Test;

import java.io.IOException;



public class BlockchainPlayerTests {


    @Test
    public void runGC(){
        //Put as a separate run
        System.gc();
    }


    @Test
    public void testPlayBlockchainWithoutRemasc() throws InvalidGenesisFileException, IOException {
        DefaultConfig defaultConfig = new DefaultConfig();
        //Source blockstore has a dummyprofiler since the reading metrics from the source blockstore in the preparation
        //for the play is not desired
        BlockStore sourceBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR);

        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);


        for(int i = 0; i <10; i++){
            playBlockchain(sourceBlockStore, TestContext.PLAY_DB_FILE+"_"+i, TestContext.BLOCK_REPLAY_DIR+"/playRun_"+i+".json", config, false);
        }
    }


    @Test
    public void testPlayBlockchainWithtRemasc() throws InvalidGenesisFileException, IOException {
        DefaultConfig defaultConfig = new DefaultConfig();
        BlockStore sourceRemascBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR+"-resmac");

        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);


        for(int i = 0; i < 10; i++){
            playBlockchain(sourceRemascBlockStore, TestContext.PLAY_DB_FILE+"-resmac_"+i, TestContext.BLOCK_REPLAY_DIR+"/playRunRemasc_"+i+".json", config, true);
        }

    }


    private void playBlockchain(BlockStore blockStore, String destinationBlockchain, String profileOutput, TestSystemProperties config, boolean includesRemasc) throws InvalidGenesisFileException, IOException {

        //Path replayPath = Paths.get(destinationBlockchain);
        //Files.createDirectory(replayPath);

        ExecutionProfiler.singleton().clean();

        BlockchainPlayer.playBlockchain(blockStore, destinationBlockchain, 1, config, ExecutionProfiler.singleton(), includesRemasc);

        ExecutionProfiler.singleton().flush(profileOutput);
    }


}
