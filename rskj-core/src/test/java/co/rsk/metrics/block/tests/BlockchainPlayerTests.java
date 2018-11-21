package co.rsk.metrics.block.tests;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.BlockchainPlayer;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import co.rsk.metrics.block.profiler.ProfilingException;
import co.rsk.metrics.block.profiler.detailed.ExecutionProfiler;
import co.rsk.metrics.block.profiler.detailed.Metric;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.config.DefaultConfig;
import org.ethereum.config.blockchain.regtest.RegTestGenesisConfig;
import org.ethereum.db.BlockStore;
import org.junit.Test;

import java.io.IOException;
import java.util.List;


public class BlockchainPlayerTests {


    @Test
    public void runGC(){
        //Put as a separate run
        System.gc();
    }


    @Test
    public void testPlayBlockchainWithoutRemasc_DetailedProfiler() throws InvalidGenesisFileException, IOException, ProfilingException {
        ExecutionProfiler.singleton().clean();
        ProfilerFactory.configure(ExecutionProfiler.singleton());
        ExecutionProfiler.singleton().newBlock(-4,0);


        DefaultConfig defaultConfig = new DefaultConfig();
        //Source blockstore has a dummyprofiler since the reading metrics from the source blockstore in the preparation
        //for the play is not desired
        BlockStore sourceBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR);

        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);


        for(int i = 0; i <11; i++){
            playBlockchain_DetailedProfiler(sourceBlockStore, TestContext.PLAY_DB_FILE+"_"+i, TestContext.BLOCK_REPLAY_DIR+"/playRun_"+i+".json", config, false);
            System.gc();
        }
    }


    @Test
    public void testPlaySingleBlockchainWithoutRemasc_DetailedProfiler() throws InvalidGenesisFileException, IOException, ProfilingException {
        ExecutionProfiler.singleton().clean();
        ProfilerFactory.configure(ExecutionProfiler.singleton());
        ExecutionProfiler.singleton().newBlock(-4,0);

        DefaultConfig defaultConfig = new DefaultConfig();
        //Source blockstore has a dummyprofiler since the reading metrics from the source blockstore in the preparation
        //for the play is not desired
        BlockStore sourceBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR);
        int run = 0;
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);
        playBlockchain_DetailedProfiler(sourceBlockStore, TestContext.PLAY_DB_FILE+"_"+run, TestContext.BLOCK_REPLAY_DIR+"/playRun_"+run+".json", config, false);

    }


    @Test
    public void testPlayBlockchainWithRemasc_DetailedProfiler() throws InvalidGenesisFileException, IOException, ProfilingException {

        ExecutionProfiler.singleton().clean();
        ProfilerFactory.configure(ExecutionProfiler.singleton());
        ExecutionProfiler.singleton().newBlock(-4,0);

        DefaultConfig defaultConfig = new DefaultConfig();
        BlockStore sourceRemascBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR+"-resmac");

        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);


        for(int i = 0; i < 11; i++){
            playBlockchain_DetailedProfiler(sourceRemascBlockStore, TestContext.PLAY_DB_FILE+"-resmac_"+i, TestContext.BLOCK_REPLAY_DIR+"/playRunRemasc_"+i+".json", config, true);
            System.gc();
        }

    }

    @Test
    public void testPlaySingleBlockchainWithRemasc_DetailedProfiler() throws InvalidGenesisFileException, IOException, ProfilingException {
        ExecutionProfiler.singleton().clean();
        ProfilerFactory.configure(ExecutionProfiler.singleton());
        ExecutionProfiler.singleton().newBlock(-4,0);

        DefaultConfig defaultConfig = new DefaultConfig();
        BlockStore sourceRemascBlockStore = defaultConfig.buildBlockStore(TestContext.BLOCK_DB_DIR+"-resmac");
        int run =0;
        TestSystemProperties config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestGenesisConfig());
        config.setGenesisInfo(TestContext.GENESIS_FILE);

        playBlockchain_DetailedProfiler(sourceRemascBlockStore, TestContext.PLAY_DB_FILE+"-resmac_"+run, TestContext.BLOCK_REPLAY_DIR+"/playRunRemasc_"+run+".json", config, true);
        System.gc();
    }



    private void playBlockchain_DetailedProfiler(BlockStore blockStore, String destinationBlockchain, String profileOutput, TestSystemProperties config, boolean includesRemasc) throws InvalidGenesisFileException, IOException, ProfilingException {
        ExecutionProfiler.singleton().clean();

        BlockchainPlayer.playBlockchain(blockStore, destinationBlockchain, 1, config, includesRemasc, true);
        List<Metric> nonstopped = ExecutionProfiler.singleton().isAllStopped();
        if(nonstopped.size()>0){
            System.out.println("NO SE PARARON TODOS LOS METRICS!!");
            for(Metric metric : nonstopped){
                System.out.println(Profiler.PROFILING_TYPE.values()[metric.getType()]);
            }

        }
        ExecutionProfiler.singleton().flushAggregated(profileOutput);
    }


}
