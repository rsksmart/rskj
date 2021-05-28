/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.cli.tools;

import co.rsk.RskContext;
import co.rsk.core.bc.BlockExecutor;
import co.rsk.db.RepositoryLocator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.datasource.DataSourceWithCache;
import org.ethereum.datasource.DataSourceWithFullReadCache;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ByteArrayWrapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The entry point for execute blocks CLI tool
 * This is an experimental/unsupported tool
 */
public class ExecuteBlocks {
    static final int bahamas = 3397;
    static final int afterBridgeSync = 370000;
    static final int orchid = 729000;
    // Between Orchid and Wasabi100, there were state root conversions.
    // RSKIP85 ... RSKIP126
    // Antes de RISKIP85 NO se valida state root
    // Entre RSKIP85 y RSKIP126 se hace la conversion
    // A partir de RSKIP126 se valida.
    static final int orchid060 = 1052700; //
    static final int wasabi100 = 1591000; //
    static final int twoToThree = 2018000;
    static final int papyrus200 = 2392700;
    final static  int maxBlockchainBlockInDB = 3_210_000;

    static String fileName = "C:\\s\\RSK\\Repos\\block-processor\\staterootdb.bin";

    public static void saveStateRootDBToFile(Map<ByteArrayWrapper,byte[]> map) throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(fileName));
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : map.entrySet()) {
            new StateRootMapEntry(entry.getKey().getData(),entry.getValue()).writeObject(out);
        }
        out.flush();
        //closing the stream
        out.close();
    }


    public static void loadStateRootDBFromFile() {
        int count =0;


        try {
            InputStream in;

            in = new FileInputStream(fileName);

            try {
                for (; ; ) {

                    StateRootMapEntry ti = new StateRootMapEntry(in);
                    ByteArrayWrapper b =new ByteArrayWrapper(ti.hash);
                    //cache.put(b, new ByteArrayWrapper(ti.value));
                    count++;
                    if (count % 100000 == 0) {
                        System.out.println("Count: " + count + " " + count * 100 / 1200000 + "%");
                    }
                }


            } catch (EOFException exc) {
                // end of stream
            }
            if (count==0) {
                System.out.println("Empty cache file: "+fileName);
                System.exit(1);
            }
            in.close();
        } catch (FileNotFoundException e) {
            System.out.println("Missing cache file: "+fileName);
            System.exit(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void saveStateRoots(RskContext ctx ) {
        KeyValueDataSource rdb = ctx.getStateRootsDB();
        System.out.println("StateRootDB:");
        Map<ByteArrayWrapper, byte[]> kv = rdb.keyValues();
        System.out.println(kv.size());
        System.out.println("start save");
        try {
            saveStateRootDBToFile(kv);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("done save");
    }

    public static void mainnetTest(String[] args)  {
        // Theactivation blocks for different RSKIPs have been changed
        // the chaged file is the following:
        // C:\s\RSK\Repos\rskj-latest\rskj\rskj-core\src\main\resources\reference.conf
        // These most be done before context creation
        RskContext.useTrieSnapshot = true;
        RskContext.useDummyBlockValidator = true;
        //DataSourceWithFullReadCache.fileName = "f1053683.bin";


        ////////////////////////////////////////
        RskContext ctx = new RskContext(args);


        // We'll dump the key/value database to disk in a fixed format.
        //saveStateRoots(ctx);
        //System.exit(1);

        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();

        TrieStoreImpl.useCacheForRetrieve =true;
        System.out.println("TrieStoreImpl.useCacheForRetrieve: "+TrieStoreImpl.useCacheForRetrieve);


        RepositoryLocator.useCache = true;
        System.out.println("RepositoryLocator.useCache: "+RepositoryLocator.useCache);

        TrieStoreImpl.useSavedTriesCache = true;
        System.out.println("TrieStoreImpl.useSavedTriesCache: "+TrieStoreImpl.useSavedTriesCache);

        // If RepositoryLocator.useCache is set to true, we have 100 assurance
        // that all nodes will be in a memory trie. Therefore we can avoid writing the
        // trie to disk (which also disables reading nodes, because while writting,
        // some nodes are read to avoid recursion
        if (RepositoryLocator.useCache) {
            // If there is AJL node trie wasSaved, then this flag changes nothing.
            TrieStoreImpl.reprocessingBlockchain = true;
            System.out.println("TrieStoreImpl.reprocessingBlockchain: "+TrieStoreImpl.reprocessingBlockchain);
        }

        // Faster for blocks after Orchid
        BlockExecutor.skipCheckTrieConversion = true;
        System.out.println("BlockExecutor.skipCheckTrieConversion: "+BlockExecutor.skipCheckTrieConversion);

        TrieStoreImpl.optimizeAJL = true;
        System.out.println("TrieStoreImpl.optimizeAJL: "+TrieStoreImpl.optimizeAJL);

        boolean isRskip151Enabledat4000 = ctx.getRskSystemProperties().getActivationConfig()
                .isActive(ConsensusRule.RSKIP151,4000);
        System.out.println("rskip151 active: "+isRskip151Enabledat4000);
        //execute(args, blockExecutor, blockStore, trieStore);
        // Execute 1000 blocks prior and past the important milestones

        /*Block block = blockStore.getChainBlockByNumber(1053684);
        Block parent = blockStore.getBlockByHash(block.getParentHash().getBytes());
        Trie aTrinode = ctx.getRepositoryLocator().getTrieAt(parent.getHeader());
        System.out.println(aTrinode.getValueLength());
        */

        int defaultRange = 100;

        String s ="";
        for(int i=0;i<args.length;i++) {
                s = s + " "+args[i];
        }
        System.out.println("arguments: "+s);
        /*
        executeCenter(bahamas,defaultRange, ctx);
        executeCenter(afterBridgeSync,defaultRange, ctx);
        executeCenter(orchid,defaultRange, ctx);
        executeCenter(orchid060,defaultRange , ctx);
        executeCenter(wasabi100,defaultRange , ctx);
        executeCenter(twoToThree,defaultRange , ctx);
        executeCenter(papyrus200,defaultRange , ctx);
        */
        // Process until a snapshot. 1M blocks

        //

        int start = 3_000_000;//papyrus200-defaultRange;//orchid-defaultRange;
        int snapshotNext  = start;//orchid-defaultRange;
        long stop = maxBlockchainBlockInDB; // start+maxBlockchainBlock-1

        int numThreads = 4;
        executeWithSnapshot(snapshotNext,start,stop,
                ctx,true,numThreads);
    }

    static String  testnetFilename ="testnet-1672390.bin";

    public static void testnetTest(String[] args) {
        // These most be done before context creation
        RskContext.useTrieSnapshot = true;
        DataSourceWithFullReadCache.fileName = testnetFilename;
        TrieStoreImpl.useCacheForRetrieve =false;
        ////////////////////////////////////////
        RskContext ctx = new RskContext(args);


        RepositoryLocator.useCache = false;
        // If RepositoryLocator.useCache is set to true, we have 100% assurance
        // that all nodes will be in a memory trie. Therefore we can avoid writing the
        // trie to disk (which also disables reading nodes, because while writting,
        // some nodes are read to avoid recursion
        if (RepositoryLocator.useCache)
            TrieStoreImpl.reprocessingBlockchain =true;

        //execute(1672391,1672391+2, ctx);
    }
    public static void testTrieEmbedding() {
        Trie root = new Trie();
        byte[] parent  = new byte[]{0x00};
        // The key is 10 bytes of key-hash, then 20 bytes of key: total 30 bytes
        byte[] key = new byte[]{0x00,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,
                0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A};
        // How many bytes are needed to store a balance? 18 decimals = 60 bits, and then 30 bits more for 1 billion tokens.
        // 90 bits = 12 bytes;
        byte[] value = new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x01,0x02};

        byte[] valueTooLong = new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x01,0x02,0x03};

        root =root.put(parent,parent); // just create a parent node.
        root = root.put(key,value);
        // this one is embedded
        System.out.println("node: "+root.toMessage().length);

        // This is not embedded
        root = new Trie();
        root =root.put(parent,parent); // just create a parent node.
        root = root.put(key,valueTooLong);
        System.out.println("node: "+root.toMessage().length);

        // Another possibility is to use a data structure selector byte
        // And not use Solidity mappings, because Solidity will expand the 20-byte
        // address into a 32-byte address.
        // The selector must not be zero, because Solidity uses storage variables
        // starting from index 0.
        // From documentation: (https://docs.soliditylang.org/en/latest/internals/layout_in_storage.html)
        // | The value corresponding to a mapping key k is located at keccak256(h(k) . p) where . is concatenation and h is a function that is applied to the key depending on its type:
        // (p is considered a 256 bit integer)
        // | for value types, h pads the value to 32 bytes in the same way as when storing the value in memory.
        // | If the value is again a non-elementary type, the positions are found by adding an offset of keccak256(k . p).
        // why the hash h(k)? Because Solidity use adjacent cells when the value in the mapping occupies
        // more than 32 bytes, it can use simply (k. p), because an attacker could use
        // k = u+1 to store a value as the second cell of another key u.
        // But if we're using only uin256 as values, we don't need this protection.
        // This means that we can use the mapping
        // <20-byte-addr> <0xff>
        // This works as long as the contract doesn't have a dynamic array at slot 0xff (255 contract  variables).
        // Why? Because RSK  strip the leading zeros of keys for storage (ethereum too? I don't remember)
        // A dynamic array at slot p will store the array size at storage cell p.
        // If I use the mapping <20-byte-addr> <0xff>, then if the 20-byte-addr is zero, this will
        // be stored at the same location of the dynamic array size.
        // (key 0x00 0x00 ..... 0xff is the same as 0x00 0xff).
        // We could improve this by adding a byte BEFORE the 20-byte-addr:
        // 0xff <20-byte-addr> <0xff>
        // In this way nothing can collude.
        // It won't be a problem even if we ditch the trailing 0xff. So the final scheme is:
        // // 0xff <20-byte-addr>
        // That header byte will lie in the middle of the trie path, because there are 10
        // randomization bytes at the begining.
        // In the end, by using this scheme the platform saves 32 bytes for the trie node hash, plus
        // 11 bytes of storage cell key, plus a node storage (3 bytes), which will necessarily have a LevelDb
        // overhead of (at least) 32 bytes totalling 32+11+3+32=78 bytes.
        // LevelDB overhead is 8 bytes  (2.4.2 Internal Key Representation) BUT surely there also word-padding overheads (https://core.ac.uk/download/pdf/52105336.pdf)
        // when stored in a single node the space used is: 20 bytes (key) and 12 bytes (value) = 32 bytes
        // Therefore the overhead of storing it in a different node is more than 100%.
        // I other words, the incentive for using small nodes should be less than 50% of th current cost.



    }
    public static void main(String[] args) {
        //testTrieEmbedding();
        //testnetTest(args);
        mainnetTest(args);
        System.out.println("Finished");
    }


    public static void execute(String[] args, BlockExecutor blockExecutor,
                               BlockStore blockStore, TrieStore trieStore,int numThreads) {
        long fromBlockNumber = Long.parseLong(args[0]);
        long toBlockNumber = Long.parseLong(args[1]);
        execute(fromBlockNumber,toBlockNumber,blockExecutor,blockStore,trieStore,numThreads);
    }

    public static void executeCenter(long centerBlockNumber , long range,  RskContext ctx,int numThreads) {
        executeWithSnapshot(centerBlockNumber-range,
                centerBlockNumber-range,centerBlockNumber+range, ctx,true,numThreads);
    }

    public static void executeWithSnapshot(
            long snapshotNext,long fromBlockNumber ,
            long toBlockNumber, RskContext ctx,boolean useSnapshot,int numThreads) {
        BlockExecutor blockExecutor = ctx.getBlockExecutor();
        BlockStore blockStore = ctx.getBlockStore();
        TrieStore trieStore = ctx.getTrieStore();
        //toBlockNumber
        if (useSnapshot) {
            ctx.dataSourceWithFullReadCache.setNewFileName(
                    "mainnet-" + (snapshotNext - 1) + ".bin");
        }
        execute(fromBlockNumber, toBlockNumber,  blockExecutor, blockStore, trieStore,numThreads);
    }

    public static void executeSingleThread(
            WorkArguments args) {
        int printInterval = 250;
        int bcount = 0;
        long lastGetCount =0;
        boolean printBlocks = false;
        boolean printEndStats = true;
        String tid = "Work "+args.num+": ";
        long  nblocks = (args.toBlockNumber-args.fromBlockNumber+1);
        System.out.println(tid+"Blocks to process: "+nblocks);
        System.out.println(tid+"Starting Block: "+args.fromBlockNumber);
        if (args.toBlockNumber-args.fromBlockNumber<10) {
            printBlocks = true;
        }
        long started = System.currentTimeMillis();
        long lastTime = started;

        for (long n = args.fromBlockNumber; n <= args.toBlockNumber; n++) {
            Block block = args.blockStore.getChainBlockByNumber(n);
            if (printBlocks)
                System.out.println("Work "+args.num+": "+"Block: "+n+" ("+(n*100/args.toBlockNumber)+"%)");
            Block parent = args.blockStore.getBlockByHash(block.getParentHash().getBytes());
            bcount++;
            if ((bcount>=printInterval) && (args.printStats)) {
                System.out.println("Work "+args.num+":Block: "+n+" ("+((n-args.fromBlockNumber)*100/(args.toBlockNumber-args.fromBlockNumber))+"%)");

            }
            //BlockResult br = blockExecutor.execute(block, parent.getHeader(), false, false);
            if (!args.blockExecutor.executeAndValidate(block,parent.getHeader())) {
                System.out.println(tid+"out of consensus at block: "+n);
                break;
            }
            if ((bcount >=printInterval) && (args.printStats)) {
                long currentTime = System.currentTimeMillis();
                long deltaTime = (currentTime - started);
                long deltaBlock = (n-args.fromBlockNumber);

                System.out.println(tid+"Time[s]: " + (deltaTime / 1000));

                if (currentTime>started)
                    System.out.println(tid+"total blocks/sec: " +deltaBlock*1000/(currentTime-started));
                if (currentTime>lastTime) {
                    System.out.println(tid+"current blocks/sec: " +bcount*1000/(currentTime-lastTime));
                    System.out.println(tid+"trie gets()/block: "+ (DataSourceWithCache.getGetsCount()-lastGetCount)/bcount);
                    lastGetCount = DataSourceWithCache.getGetsCount();
                    lastTime = currentTime;
                }
                System.out.println(tid+"Mem MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024  /1024);
                bcount =0;
                //trieStore.flush();
            }
        }
        if (printEndStats) {
            long currentTime = System.currentTimeMillis();
            long deltaTime = (currentTime - started);
            System.out.println(tid+"End Time[s]: " + (deltaTime / 1000));
            //System.out.println(tid+"Time[ms]: " + (deltaTime));
        }
    }

    static class WorkDispatcher implements Runnable {
        private WorkArguments args;

        public WorkDispatcher(WorkArguments aargs){
            this.args = aargs;
        }

        public void run() {
            System.out.println(Thread.currentThread().getName()+" (Start) work unit = "+args.num);
            processmessage();//
            System.out.println(Thread.currentThread().getName()+" (End) work unit = "+args.num);
        }
        private void processmessage() {
            //try {
                ExecuteBlocks.executeSingleThread(args);
                block_counter.getAndAdd((int)(args.toBlockNumber-args.fromBlockNumber+1));
                work_counter.getAndIncrement();
            //} catch (InterruptedException e) { e.printStackTrace(); }
        }
    }

    static class WorkArguments {
        int num;
        boolean printStats;
        long fromBlockNumber;
        long toBlockNumber;
        BlockExecutor blockExecutor;
        BlockStore blockStore;
        TrieStore trieStore;

        WorkArguments(int num,
                      boolean printStats,
                      long fromBlockNumber,
                      long toBlockNumber,
                      BlockExecutor blockExecutor,
                      BlockStore blockStore,
                      TrieStore trieStore) {
            this.num = num;
            this.printStats = printStats;
            this.fromBlockNumber = fromBlockNumber;
            this.toBlockNumber  = toBlockNumber;
            this.blockExecutor = blockExecutor;
            this.blockStore =blockStore;
            this.trieStore  = trieStore;
        }
    }
    static AtomicInteger block_counter = new AtomicInteger(0); // a global counter
    static AtomicInteger work_counter = new AtomicInteger(0); // a global counter


    public static void execute(long fromBlockNumber , long toBlockNumber,
                               BlockExecutor blockExecutor, BlockStore blockStore, TrieStore trieStore,
                               int numThreads) {

        // Dump the current state file, and load a new one

        long  nblocks = (toBlockNumber-fromBlockNumber+1);
        System.out.println("TOP Blocks to process: "+nblocks);
        System.out.println("TOP Starting Block: "+fromBlockNumber);
        String tid = "TOP: ";
        long started = System.currentTimeMillis();
        long lastTime = started;

        List<Thread> threads = new ArrayList<>();
        // 4 -> 22 secs
        if (numThreads==0) { // not defined
            if (nblocks < 100)
                numThreads = 1;
            else
                numThreads=4;
        }
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);//
        /*
        for(int i=0;i<numThreads;i++) {
            long thread_from = fromBlockNumber + (nblocks * i) / numThreads;
            long thread_to = fromBlockNumber + nblocks * (i + 1) / numThreads - 1;
            int num =i;
            Runnable runnable =
                    () -> {
                        executeSingleThread(num, true, thread_from, thread_to, blockExecutor, blockStore,trieStore);
                    };
            Thread thread = new Thread(runnable);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        */

        int divideBy = 100;
        if (nblocks>100*1000) {
            divideBy= 400;
        }
        // The work is divided int 100 units
        long work_range = nblocks  / divideBy;
        if (work_range<100) work_range =100; // minimum 1 seondd work
        long  work_units = (nblocks+work_range-1)/work_range;

        for(int i=0;i<work_units;i++) {
            long work_from = fromBlockNumber +  i*work_range;
            long work_to = work_from + work_range-1;
            if (work_to>toBlockNumber)
                work_to = toBlockNumber; // last unit may be shorter
            int num = i;
            Runnable worker = new WorkDispatcher(new WorkArguments(
                    num, false, work_from, work_to, blockExecutor, blockStore,trieStore
            ));
            executor.execute(worker);//calling execute method of ExecutorService

        }
        //;
        // Returns true if all tasks have completed following shut down. Note that isTerminated is never true unless either shutdown or shutdownNow was called first.
        while (!executor.isTerminated()) {
            try {  Thread.sleep(5000);  } catch (InterruptedException e) { e.printStackTrace(); }

            long currentTime = System.currentTimeMillis();
            long deltaTime = (currentTime - started);
            long deltaBlock = block_counter.get();
            long deltaWork =work_counter.get();
            System.out.println(tid+"Time[s]: " + (deltaTime / 1000));
            System.out.println(tid+"Blocks:  " +deltaBlock);
            System.out.println(tid+"Work units finished:  " +deltaWork);
            if (currentTime>started)
                System.out.println(tid+"total blocks/sec: " +deltaBlock*1000/(currentTime-started));
            System.out.println(tid+"Mem MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024  /1024);
            if (deltaWork==work_units)
                executor.shutdown();
        }

        long currentTime = System.currentTimeMillis();
        long deltaTime = (currentTime - started);
        long deltaBlock = nblocks;

        System.out.println(tid+"Time[s]: " + (deltaTime / 1000));

        if (currentTime>started)
            System.out.println(tid+"total blocks/sec: " +deltaBlock*1000/(currentTime-started));
        System.out.println(tid+"Mem MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024  /1024);

        //System.out.println(tid+"Time[ms]: " + (deltaTime));

        //trieStore.flush();
        //blockStore.flush();
    }
}
