package co.rsk.metrics.block;

import co.rsk.config.TestSystemProperties;
import co.rsk.metrics.block.builder.BlockChainBuilder;
import co.rsk.metrics.block.builder.BlockInfo;
import co.rsk.metrics.block.builder.GenesisLoader;
import co.rsk.metrics.block.builder.InvalidGenesisFileException;
import co.rsk.metrics.block.tests.TestContext;
import co.rsk.metrics.profilers.Profiler;
import co.rsk.metrics.profilers.ProfilerFactory;
import org.ethereum.config.CommonConfig;
import org.ethereum.config.DefaultConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 *Plays an already-existing blockchain, generating a new blockchain instance in a different replayDir
 */
public class BlockchainPlayer {


    private static final Logger logger = LoggerFactory.getLogger("BlockchainPlayer");
    private static final Profiler profiler = ProfilerFactory.getInstance();



    public static void playBlockchain(BlockStore blockStore, String replayDir, int playFromBlock, TestSystemProperties config, boolean includesRemasc, boolean cachedTrxs) throws InvalidGenesisFileException {


        //Any pre-blockchain load profile metric is stored at "block" -3
        profiler.newBlock(-3,0);

        DefaultConfig defaultConfig = new DefaultConfig();
        CommonConfig commonConfig = new CommonConfig();
        BlockStore sourceBlockStore = blockStore;


        BlockStore destinationBlockStore = defaultConfig.buildBlockStore(replayDir);
        ReceiptStore receiptStore = defaultConfig.buildReceiptStore(replayDir);
        Repository repository = commonConfig.buildRepository(replayDir, 1024);

        BlockChainBuilder builder = new BlockChainBuilder(includesRemasc);
        builder.setBlockStore(destinationBlockStore);
        builder.setReceiptStore(receiptStore);
        builder.setRepository(repository);
        builder.setConfig(config);


        GenesisLoader genesisLoader = GenesisLoader.newGenesisLoader(config, TestContext.GENESIS_FILE_ROOT);

        builder.setGenesis(genesisLoader.loadGenesis());


        //Read all the blocks from the blockstore before executing them
        logger.info("Starting building block information list from source block store");
        List<Block> blocks = new ArrayList<>();


        long maxSourceBlock = sourceBlockStore.getMaxNumber();

        logger.info("Max source block is {}", maxSourceBlock);

        for (long height = playFromBlock ; height <= maxSourceBlock; height++) {
            List<BlockInformation> blocksInformation = sourceBlockStore.getBlocksInformationByNumber(height);
            for (BlockInformation blockInformation : blocksInformation) {
                Block block = sourceBlockStore.getBlockByHash(blockInformation.getHash());
                if (blockInformation.isInMainChain()) {

                    //Fill cache:
                    //TODO RAUL: REMOVE WHEN CACHE IS REMOVED
                    if(cachedTrxs){
                        for(Transaction trx : block.getTransactionsList()){
                            trx.getSender();
                        }
                    }


                    //Keeping track of sig validation resulted to be easier by adding the profiler right in the
                    //Transaction instead of just the TransactionExecutor, for example, trx.validate() interally gets
                    //the sender and it's the first call to getSender.
                    logger.info("transactions in block {} are {}", block.getNumber(), block.getTransactionsList().size());

                    blocks.add(block);
                }
                else
                {
                    logger.warn("Block {} not from MainChain, skipped", block.getNumber());
                }
            }
        }
        logger.info("Finished building block information list from source block store");


        builder.setProcessedBlocks(blocks);

        builder.play();

    }

      /*private static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(sourcePath -> {

                try {
                    Files.copy(
                            sourcePath,
                            src.resolve(dest.relativize(sourcePath)));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }*/
}
