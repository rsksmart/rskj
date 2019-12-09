package co.rsk;

import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class SnappyMetrics {

    private static final boolean READ = true;
    private static final int MIN = 1;
    private static final int MAX = 150000;

    private BlockStore store;
    private boolean rw;
    private int values;
    private int seed;
    private boolean useSnappy;
    private RskContext objects;
    private final Blockchain blockchain;

    public SnappyMetrics(String path, boolean rw, int values, int seed, boolean useSnappy) {
        this.objects = new RskContext(new String[]{ "--testnet", "-base-path",  path}, useSnappy);
        this.store = objects.getBlockStore();
        this.blockchain = objects.getBlockchain();
        this.rw = rw;
        this.values = values;
        this.seed = seed;
        this.useSnappy = useSnappy;
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

    public long measureReads(long values, long seed) {
        Random valueSource = new Random(seed);
        IntStream blockNumbers = valueSource.ints(values, MIN, MAX);
        long totalTime = 0;
        System.out.println("-- Measuring Reads: " + values + " values --");
        if (useSnappy) {
            System.out.println("With Snappy Blockchain");
            totalTime += timeForRead(store, blockNumbers);
        } else {
            System.out.println("With Normal Blockchain");
            totalTime += timeForRead(store, blockNumbers);
        }
        return totalTime;
    }
//    public long measureWrites(long values) {
//        long totalTime = 0;
//        System.out.println("-- Measuring Writes: " + values + " values --");
//        if (useSnappy) {
//            System.out.println("With Snappy Blockchain");
//            totalTime += timeForWrite(snappyStore, values);
//        } else {
//            System.out.println("With Normal Blockchain");
//            totalTime += timeForWrite(normalStore, values);
//        }
//        return totalTime;

//    }
//    private long timeForWrite(BlockStore store, long values) {
//        final long maxNumber = store.getMaxNumber();
//        long time = 0;
//        for (int i = 0; i < values; i++) {
//            Block normalBlock = blockchain.getChainBlockByNumber(normalBlockNumber++);
//            System.out.println("Writing block number: " + normalBlockNumber);
//            long saveTime = System.nanoTime();
//            ImportResult result = normalDummyBlockchain.tryToConnect(normalBlock);
//            normalTime += System.nanoTime() - saveTime;
//
//            if (!BlockProcessResult.importOk(result)) {
//                System.err.printf("Import failed at block %7d%n", normalBlockNumber);
//                System.exit(1);
//            }
//        }
//        return 0;

//    }

    private static boolean compareBlockchains (BlockStore normalStore, BlockStore snappyStore) {
        boolean equals = normalStore.getMaxNumber() == snappyStore.getMaxNumber();

        long length = Math.min(normalStore.getMaxNumber(), snappyStore.getMaxNumber());
        for (int i = 1; i <= length && equals; i++ ) {
            Block normalBlock = normalStore.getChainBlockByNumber(i);
            Block snappyBlock = snappyStore.getChainBlockByNumber(i);
            equals &= normalBlock.getHash().equals(snappyBlock.getHash());

            if (i % 100 == 0) {
                System.out.println("Comparing block number " + i);
            }

        }
        return equals;
    }

    public void runExperiment() {
        final long totalTime = 0;
        if (useSnappy) {

        } else {
        }

        if (rw == READ) {
            measureReads(values, seed);
        } else {
            //measureWrites(100);
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("usage: SnappyMetrics <Normal Blockchain path> <Snappy Blockchain path> " +
                    "<Use Snappy (true/false)> <Use read/write (true/false)>");
            System.exit(0);
            return;
        }

        final String normalDbdir = "/Users/julian/workspace/rskj-projects/dbs/normal-database-150";
        final String snappyDBdir = "/Users/julian/workspace/rskj-projects/dbs/snappy-database-150";
        final String dummyBlockchain = "/Users/julian/workspace/rskj-projects/dbs/dummy-database";

//        if (compareBlockchains(normalBlockStore, snappyBlockStore)){
//        } else {
//            System.out.println("Blockchain compressed and uncompressed are NOT equals");
//        }


//        final long readTimeSnappy = smSnappy.measureReads(100, 100);

//        final long readTimeNormal = smNormal.measureReads(100, 100);




        System.exit(0);

    }
}
