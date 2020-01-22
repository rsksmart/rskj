package co.rsk.snappy;

import co.rsk.RskContext;
import co.rsk.net.BlockProcessResult;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class SnappyBlocksMetrics extends SnappyMetrics {

    private BlockStore store;

    public SnappyBlocksMetrics(String path, boolean rw, int values, int seed, boolean useSnappy) {
        super(path, rw, values, seed, useSnappy);
        this.store = objects.getBlockStore();
    }

    public long runExperiment() {
        long totalTime ;

        if (rw == READ) {
            totalTime = measureReads(values, seed);
        } else {
            totalTime = measureWrites(values);
        }

        return totalTime;
    }

    public long measureReads(long values, long seed) {
        Random valueSource = new Random(seed);
        IntStream blockNumbers = valueSource.ints(values, MIN, MAX);
        long totalTime = 0;
        totalTime += timeForRead(store, blockNumbers);
//        System.out.println("-- Measuring Reads: " + values + " values --");
        if (useSnappy) {
            System.out.println("s,r,"+totalTime+","+values);
        } else {
            System.out.println("n,r,"+totalTime+","+values);
        }

        return totalTime;
    }

    public long measureWrites(long values) {
        final String dummyPath = "/Users/julian/workspace/rskj-projects/dbs/blockchain-dummy-test";
        String[] dummyCliArgs = new String[] {"--testnet","-base-path", dummyPath};
        RskContext rskContextDummy = new RskContext(dummyCliArgs, useSnappy);
        Blockchain dummyblockchain = rskContextDummy.getBlockchain();
        BlockStore dummyStore = rskContextDummy.getBlockStore();

        long totalTime = 0;
//        System.out.println("-- Measuring Writes: " + values + " values --");
/*        if (useSnappy) {
            System.out.println("With Snappy Blockchain");
        } else {
            System.out.println("With Normal Blockchain");
        }*/
        totalTime += timeForWrite(store, dummyblockchain, values);
        if (useSnappy) {
            System.out.println("s,w,"+totalTime +","+values);
        } else {
            System.out.println("n,w,"+totalTime+","+values);
        }
        dummyStore.flush(); //This if just for prevent bad db closing.
        FileRecursiveDelete.deleteFile(dummyPath);
        return totalTime;
    }

    private long timeForRead(BlockStore store, IntStream blockNumbers) {
        long time = 0;
        for (int anInt : blockNumbers.toArray()) {

            List<BlockInformation> blockinfos = store.getBlocksInformationByNumber(anInt);

            for (BlockInformation bi : blockinfos) {
                long saveTime = System.nanoTime();
                store.getBlockByHash(bi.getHash());
                time += System.nanoTime() - saveTime;
            }
        }
        return time;
    }

    private long timeForWrite(BlockStore store, Blockchain dummyBlockchain, long values) {
        long blockNumber = dummyBlockchain.getBestBlock().getNumber() + 1;
        long time = 0;

        for (int i = 0; i < values; i++) {
            Block block = store.getChainBlockByNumber(blockNumber++);
//            System.out.println("Writing block number: " + blockNumber);
            long saveTime = System.nanoTime();
            ImportResult result = dummyBlockchain.tryToConnect(block);
            time += System.nanoTime() - saveTime;


            if (!BlockProcessResult.importOk(result)) {
                System.err.printf("Import failed at block %7d%n", blockNumber);
                System.exit(1);
            }
        }

        return time;
    }
}

