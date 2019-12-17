package co.rsk;

import co.rsk.net.BlockProcessResult;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.ImportResult;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;

import java.io.File;
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

    public SnappyMetrics(String path, boolean rw, int values, int seed, boolean useSnappy) {
        this.objects = new RskContext(new String[]{ "--testnet", "-base-path",  path}, useSnappy);
        this.store = objects.getBlockStore();
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
        dummyStore.flush();
        FileRecursiveDelete.deleteFile(dummyPath);
        return totalTime;

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

    public long runExperiment() {
        long totalTime ;

        if (rw == READ) {
            totalTime = measureReads(values, seed);
        } else {
            totalTime = measureWrites(values);
        }

        return totalTime;
    }
}

class FileRecursiveDelete {
    public static void deleteFile(String s) {

        File directory = new File(s);
        if(!directory.exists()){
            System.out.println("Directory does not exist.");
            System.exit(0);
        }else{
            delete(directory);
//            System.out.println("Deleted ready");
        }
    }

    public static void delete(File file) {
        if(file.isDirectory()){

            //directory is empty, then delete it
            if(file.list().length==0){
                file.delete();
                //System.out.println("Directory is deleted : " + file.getAbsolutePath());
            }else{
                //list all the directory contents
                String files[] = file.list();
                for (String temp : files) {
                    //construct the file structure
                    File fileDelete = new File(file, temp);
                    //recursive delete
                    delete(fileDelete);
                }
                //check the directory again, if empty then delete it
                if(file.list().length==0){
                    file.delete();
                    //System.out.println("Directory is deleted : "+ file.getAbsolutePath());
                }
            }
        }else{
            //if file, then delete it
            file.delete();
            //System.out.println("File is deleted : " + file.getAbsolutePath());
        }
    }
}