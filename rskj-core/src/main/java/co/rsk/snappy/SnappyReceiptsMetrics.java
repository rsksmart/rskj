package co.rsk.snappy;

import co.rsk.RskContext;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.BlockInformation;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

public class SnappyReceiptsMetrics extends SnappyMetrics {
    private final ReceiptStore store;
    private final BlockStore blockstore;

    public SnappyReceiptsMetrics(String path, boolean rW, int values, int seed, boolean useSnappy) {
        super(path, rW, values, seed, useSnappy);
        this.store = objects.getReceiptStore();
        this.blockstore = objects.getBlockStore();
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

    private long measureWrites(int values) {
        final String dummyPath = "/Users/julian/workspace/rskj-projects/dbs/blockchain-dummy-test";
        String[] dummyCliArgs = new String[] {"--testnet","-base-path", dummyPath};
        RskContext rskContextDummy = new RskContext(dummyCliArgs, useSnappy);
        ReceiptStore dummyStore = rskContextDummy.getReceiptStore();
        long totalTime = timeForWrite(dummyStore, values);

        if (useSnappy) {
            System.out.println("s,w,"+totalTime +","+values);
        } else {
            System.out.println("n,w,"+totalTime+","+values);
        }

        FileRecursiveDelete.deleteFile(dummyPath);
        return totalTime;
    }

    private long timeForWrite(ReceiptStore dummyStore, int values) {
        long time = 0;
        TransactionReceipt receipt = createReceipt();
        for (int i = 0; i < values; i++) {
            byte[] blockHash = HashUtil.randomHash();
            long saveTime = System.nanoTime();
            dummyStore.add(blockHash, 3, receipt);
            time += System.nanoTime() - saveTime;
        }
        return time;
    }

    private long measureReads(int values, int seed) {
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

    private long timeForRead(ReceiptStore store, IntStream blockNumbers) {
        long time = 0;
        for (int anInt : blockNumbers.toArray()) {
            List<BlockInformation> blockinfos = blockstore.getBlocksInformationByNumber(anInt);
            for (BlockInformation bi : blockinfos) {
                final Block block = blockstore.getBlockByHash(bi.getHash());
                final Transaction tx = block.getTransactionsList().get(0);
                long saveTime = System.nanoTime();
                store.getAll(tx.getHash().getBytes());
                time += System.nanoTime() - saveTime;
            }
        }
        return time;
    }

    private static TransactionReceipt createReceipt() {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});

        receipt.setTransaction(new Transaction((byte[]) null, null, null, null, null, null));

        return receipt;
    }
}
