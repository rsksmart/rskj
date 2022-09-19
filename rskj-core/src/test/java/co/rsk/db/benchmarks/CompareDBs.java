package co.rsk.db.benchmarks;


import co.rsk.freeheap.CreationFlag;
import co.rsk.datasources.FlatyDbDataSource;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.*;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.FastByteComparisons;
import java.io.*;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class CompareDBs extends Benchmark {
    enum Test {
        flushTest,
        readTest,
        writeTest,
        writeBatchTest,
        readWriteTest // read, Batched Writes and Async Flushes
    }

    static Test test = Test.writeTest;
    boolean keyIsValueHash =true;
    static DbKind database = DbKind.FLATY_DB;

    static enum DatabaseConfig {
        withLog,
        withMaxOffset
    }
    DatabaseConfig config = DatabaseConfig.withMaxOffset; // This is only for flatDb

    int maxKeys = 1_000_000; // 5_000_000; //100_000_000;
    int keyLength = 32;
    int valueLength = 50;


    public void createLogFile(String basename,String expectedItems) {
        String resultsDir = "DBResults/";
        try {
            Files.createDirectories(Paths.get(resultsDir));
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        String name = resultsDir+basename;
        name = name + "-"+database.name();
        name = name + "-"+expectedItems;
        name = name +"-Max_"+ getMillions( Runtime.getRuntime().maxMemory());

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        String strDate = dateFormat.format(date);
        name = name + "-"+ strDate;

        plainCreateLogFilename(name);
    }

    public void prepare() {
        showPartialMemConsumed = false;
    }

    void deleteDirectoryRecursion(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
        Files.delete(path);
    }
    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files==null)
            return 0;

        int count = files.length;

        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            }
            else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }

    public void dumpTrieDBFolderSize() {
        long fs = getFolderSize(new File(trieDBFolder.toString()));
        log("TrieDB size: "+fs+" ("+getMillions(fs)+"M)") ;

        log("valueLength: "+valueLength);
        double entryLen = fs/maxKeys;
        log("db entry length  : " + entryLen);
        int overhead = (int) (entryLen-valueLength);
        log("entry overhead : " + overhead);
        log("entry overhead [%] : " + overhead*100/valueLength+"%");
    }

    KeyValueDataSource db;
    Path trieDBFolder = Paths.get("./dbstore");

    public void openDB(boolean deleteIfExists, boolean abortIfExists, String dbName) {
        Path trieDBFolderPlusSize = trieDBFolder;

        if ((dbName!=null) && (dbName.length()>0)) {
            if ((dbName.indexOf("..")>=0) || (dbName.indexOf("/")>=0))
                throw new RuntimeException("sanity check");
            trieDBFolderPlusSize = trieDBFolder.resolve(dbName);
        }
        if (db==null)
            db = buildDB(trieDBFolderPlusSize,deleteIfExists,abortIfExists);
    }

    protected KeyValueDataSource buildDB(Path trieStorePath, boolean deleteIfExists, boolean abortIfExists) {
        int statesCacheSize;

        log("Database: "+trieStorePath.toAbsolutePath());
        if (abortIfExists) {
            if (Files.isDirectory(trieStorePath, LinkOption.NOFOLLOW_LINKS)) {

                System.out.println("Target trie db directory exists. enter 'del' to delete it and continue");

                // Enter data using BufferReader
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in));

                // Reading data using readLine
                String cmd = null;
                try {
                    cmd = reader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if ((cmd==null) || (!cmd.equals("del")))
                    throw new RuntimeException("Target trie db directory exists. Remove first");
                deleteIfExists = true;
            }

        }
        if (deleteIfExists) {
            try {
                log("deleting previous trie db");
                deleteDirectoryRecursion(trieStorePath);
                dumpTrieDBFolderSize();
            } catch (IOException e) {
                System.out.println("Could not delete database dir");
            }
        }

        log("Database class: " + database.toString());

        KeyValueDataSource dsDB =null;
        int maxNodeCount = 32*1000*1000;
        // 32 Million nodes -> 128 Mbytes of reference cache
        maxNodeCount = maxKeys*2;
        log("beHeap:maxNodeCount: "+maxNodeCount);
        long beHeapCapacity =64L*1000*1000*1000; // 64 GB
        // We double the heap capacity to be able to add more
        // nodes in the readWrite test
        beHeapCapacity =2L*maxNodeCount*valueLength; // 1 MB
        int maxKeys = maxNodeCount;
        log("beHeap:Capacity: "+beHeapCapacity);

        KeyValueDataSourceUtils.FlatyDBOptions flatyDbOptions = new KeyValueDataSourceUtils.FlatyDBOptions();
        flatyDbOptions.maxKeys =maxKeys;
        flatyDbOptions.maxCapacity =beHeapCapacity;
        flatyDbOptions.maxObjectSize = 256;
        flatyDbOptions.dbVersion = FlatyDbDataSource.latestDBVersion;

        // These flas are the ideal to create a Trie DB, which is what
        // we aim to. So we do not supportNullValues, nor do we allowRemovals.
        flatyDbOptions.creationFlags = EnumSet.of(
                CreationFlag.supportBigValues,
                CreationFlag.atomicBatches,
                CreationFlag.useDBForDescriptions);

        if (!keyIsValueHash) {
            flatyDbOptions.creationFlags.add(CreationFlag.storeKeys);
        }
        if (config==DatabaseConfig.withLog) {
            flatyDbOptions.creationFlags.add(CreationFlag.useLogForBatchConsistency);
        } else {
            flatyDbOptions.creationFlags.add(CreationFlag.useMaxOffsetForBatchConsistency);
        }
        dsDB = KeyValueDataSourceUtils.makeDataSourceExt(trieStorePath,database,false,
                flatyDbOptions);

        return dsDB;
    }

    PseudoRandom pseudoRandom = new PseudoRandom();

    public void dumpResults(int max) {
        long elapsedTimeMs = (ended - started);
        elapsedTime = elapsedTimeMs / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            log("Nodes/sec: " + (max *1000L/ elapsedTimeMs));
        }
        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));
        showUsedMemory();
    }

    public void reads() {

        boolean showStats = false;
        setup(true);
        start(false);
        int maxReadKeys = 0; // Filled later
        int dumpInterval = 0;

        if (maxKeys>=100_000_000) {
            // We need to read more than 200k keys to avoid the system cache
            // will show an unrealistic extremely fast read speed.
            maxReadKeys = 200_000; // Things get MUCH slower at this point with LevelDB
            dumpInterval = 5_000;
        } else {
            maxReadKeys = 1_000_000;
            dumpInterval = 10_000;
        }
        // To prevent the cache from caching exactly the keys we want to read
        // we should read staring from some random point
        for (long i = 0; i < maxReadKeys; i++) {

            long x =TestUtils.getPseudoRandom().nextLong(maxKeys);
            pseudoRandom.setSeed(x);

            byte[] key =null;
            if (!keyIsValueHash)
                key = pseudoRandom.randomBytes(keyLength);
            byte[] expectedValue = pseudoRandom.randomBytes(valueLength);
            if (keyIsValueHash)
                key= Keccak256Helper.keccak256(expectedValue);
            byte[] value = db.get(key);
            if (!FastByteComparisons.equalBytes(value,expectedValue)) {
                System.out.println("Wrong value");
                System.exit(1);
            }

            if (i % dumpInterval == 0) {
                dumpProgress(i, maxReadKeys);
                if (showStats) {
                    List<String> stats = db.getStats();
                    if (stats != null) {
                        log("Stats: ");
                        logList(" ", stats);
                    }
                }
            }

        }
        dumpProgress(maxReadKeys,maxReadKeys);
        stop(false);
        dumpResults(maxReadKeys);
        closeLog();
    }

    public void setup(boolean read) {
        String testName;
        testName ="DB"+test.name();

        String maxStr = ""+ getMillions(maxKeys);
        createLogFile(testName,maxStr);

        prepare();

        String tmpDbNamePrefix = "";
        String dbName = "DB_"+getExactCountLiteral(maxKeys);
        if (keyIsValueHash)
            dbName = dbName +"-vh";
        dbName = dbName + "-vlen_"+valueLength;
        dbName =dbName +"-"+database.name();
        //if (database==DbKind.FLAT_DB)
        //    dbName =dbName +"-"+config.name();

        if (read)
            openDB(false,false,dbName);
        else
            if ((test==Test.readWriteTest) || (test==Test.flushTest))
                openDB(false,false,dbName);
            else
        if (tmpDbNamePrefix.length()>0) {
            // Temporary DB. Can delete freely
            openDB(true,false,dbName);
        } else
            openDB(false,true,dbName);

    }

    public void writes() {
        setup(false);

        start(true);
        int flushBatch = 1_000_000;
        System.out.println("flushBatch: "+flushBatch);
        int c =0;
        for (long i = 0; i < maxKeys; i++) {

            pseudoRandom.setSeed(i);
            // The key is not the hash of the value.
            // This differs from a trie test, where that is true.
            byte key[]=null;
            if (!keyIsValueHash)
                key =pseudoRandom.randomBytes(keyLength);
            byte[] value = pseudoRandom.randomBytes(valueLength);
            if (keyIsValueHash)
                key= Keccak256Helper.keccak256(value);
            db.put(key, value);
            c++;
            if (c==flushBatch) {
                long fstart = System.currentTimeMillis();
                db.flush();
                long fstop = System.currentTimeMillis();
                log("Time to flush: "+(fstop-fstart)+" ms");
                c=0;
            }
            if (i % 100000 == 0) {
                dumpProgress(i,maxKeys);
            }

        }
        db.flush();
        dumpProgress(maxKeys,maxKeys);
        stop(true);
        closeDB();

        dumpResults(maxKeys);
        dumpTrieDBFolderSize();
        closeLog();
    }
    // We assume each block modifies 6800000/21000*2 = 646 leaf nodes
    // (all transactions are rBTC transfers).
    // Every leaf node modified implies re-writting al parent nodes, but the
    // parent nodes are shared. We'll assume that the first 8 levels are shared
    // (because the number of leaves modified is greater than 256).
    // We'll also assume the trie has 8 million nodes (2^23). Therefore
    // writing a leaf node implies writing another 23 nodes.
    // The total number of nodes written per block is 256+646*15=9946.
    // Let's use 10k for simplicity.
    // We set our flush interval to be the config's default:
    // flushNumberOfBlocks = 1000
    // So we get a batch of size 1000*1000 = 1M.
    // We could also assume that the accounts used in these transfers
    // have a Pareto distribution, which will decrease the number of account
    // touched significantly, but we won't do it here.
    public void writeBatch() {
        setup(false);

        start(true);
        int batchSize = 1000_000;
        int maxRounds = maxKeys/batchSize;
        long spentOnFlush =0;
        log("maxRounds: "+maxRounds);
        log("batchSize: "+batchSize);
        Set<ByteArrayWrapper> keysToRemove = new HashSet<>();

        for(int rounds = 0;rounds<maxRounds;rounds++) {
            HashMap<ByteArrayWrapper, byte[]> batch = new HashMap<>(batchSize);
            // We simulate a flush every
            for (int b = 0; b < batchSize; b++) {
                int i = rounds * batchSize + b;
                pseudoRandom.setSeed(i);
                // The key is not the hash of the value.
                // This differs from a trie test, where that is true.
                byte key[] = null;
                if (!keyIsValueHash)
                    key = pseudoRandom.randomBytes(keyLength);
                byte[] value = pseudoRandom.randomBytes(valueLength);
                if (keyIsValueHash)
                    key = Keccak256Helper.keccak256(value);

                batch.put(new ByteArrayWrapper(key), value);
                if (i % 100000 == 0) {
                    dumpProgress(i, maxKeys);
                }
            }
            db.updateBatch(batch, keysToRemove);
            long fstart = System.currentTimeMillis();
            db.flush();
            long fstop = System.currentTimeMillis();
            spentOnFlush +=(fstop-fstart);
            log("Time to flush: "+(fstop-fstart)+" ms");
        }
        dumpProgress(maxKeys,maxKeys);
        stop(true);
        log("spentOnFlush: "+(spentOnFlush/1000)+ " sec");
        closeDB();

        dumpResults(maxKeys);
        dumpTrieDBFolderSize();
        closeLog();
    }

    public class WorkerThread implements Runnable {
        int spentOnFlush = 0;
        private KeyValueDataSource db;
        AtomicLong acumSpent;

        public WorkerThread(KeyValueDataSource db,AtomicLong acumSpent){
            this.acumSpent = acumSpent;
            this.db=db;
        }

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName()+" Start.");
            processCommand();
            System.out.println(Thread.currentThread().getName()+" End.");
        }

        private void processCommand() {
            long fstart = System.currentTimeMillis();
            db.flush();
            long fstop = System.currentTimeMillis();
            spentOnFlush +=(fstop-fstart);
            acumSpent.addAndGet(spentOnFlush);
            log("Time to flush: "+(fstop-fstart)+" ms");

        }

    }

    public void flushTime() {
        setup(false);
        long fstart = System.currentTimeMillis();
        //if (db instanceof FlatyDbDataSource)
        //    ((FlatyDbDataSource)db).forceSaveBaMap();
        //db.flush();
        long fstop = System.currentTimeMillis();
        log("Time to flush: "+(fstop-fstart)+" ms");
    }

    public void readWrites() {
        // Lo que esta pasando con flat db ahora aca es
        // que el flush tarda tanto siempo que retrasa el update.
        boolean flushData = false;
        setup(false);

        start(true);
        int batchSize = 10_000;
        int maxReads = 20_000;
        int maxRounds = maxReads*20/batchSize; // 20 times more writes than reads, but split into batches
        // aumente la cantidad de reads de bathSize/20 a batchSize para evitar que
        // se bloquee en el updateBatch().
        int readsPerBatch = batchSize;
        //long spentOnFlush =0;
        log("maxRounds: "+maxRounds);
        log("batchSize: "+batchSize);
        log("maxReads: "+maxReads);
        Set<ByteArrayWrapper> keysToRemove = new HashSet<>();
        int unusedKeyBaseSeed = 50_000_000;
        ExecutorService executor=null;
        if (flushData)
            executor = Executors.newFixedThreadPool(1);
        AtomicLong spentOnFlush = new AtomicLong();

        for(int rounds = 0;rounds<maxRounds;rounds++) {
            HashMap<ByteArrayWrapper, byte[]> batch = new HashMap<>(batchSize);
            // We simulate a flush every
            // Perform
            for (int r =0;r<readsPerBatch;r++) {
                long x =TestUtils.getPseudoRandom().nextLong(maxKeys);
                pseudoRandom.setSeed(x);
                if (r % 100 == 0) { System.out.print("."); }
                byte[] key =null;
                if (!keyIsValueHash)
                    key = pseudoRandom.randomBytes(keyLength);
                byte[] expectedValue = pseudoRandom.randomBytes(valueLength);
                if (keyIsValueHash)
                    key= Keccak256Helper.keccak256(expectedValue);
                byte[] value = db.get(key);
                if ((value==null) || !FastByteComparisons.equalBytes(value,expectedValue)) {
                    System.out.println("Wrong value");
                    System.exit(1);
                }
            }
            System.out.println("beginUpdate");
            for (int b = 0; b < batchSize; b++) {
                int i = rounds * batchSize + b;
                pseudoRandom.setSeed(unusedKeyBaseSeed +i);
                // The key is not the hash of the value.
                // This differs from a trie test, where that is true.
                byte key[] = null;
                if (!keyIsValueHash)
                    key = pseudoRandom.randomBytes(keyLength);
                byte[] value = pseudoRandom.randomBytes(valueLength);
                if (keyIsValueHash)
                    key = Keccak256Helper.keccak256(value);

                batch.put(new ByteArrayWrapper(key), value);
                if (i % 100000 == 0) {
                    dumpProgress(i, maxRounds*batchSize);
                }
            }
            db.updateBatch(batch, keysToRemove);
            if (flushData) {
                Runnable worker = new WorkerThread(db, spentOnFlush);
                executor.execute(worker);
            } else
                db.flush();
        }
        //while (!executor.isTerminated()) { }

        System.out.println("Finished all threads");
        dumpProgress(maxKeys,maxKeys);
        stop(true);
        log("spentOnFlush: "+(spentOnFlush.get()/1000)+ " sec");
        closeDB();
        if (flushData) {
            executor.shutdown();
        }
        dumpResults(maxKeys);
        dumpTrieDBFolderSize();
        closeLog();
    }
    public void closeDB() {
        log("Closing db...");
        db.close();
        log("Closed");
    }

    public static void main (String args[]) {

        CompareDBs c = new CompareDBs();
        if (test== Test.readTest)
            c.reads();
        else
        if (test== Test.writeTest)
            c.writes();
        else
        if (test== Test.writeBatchTest)
            c.writeBatch();
        else
        if (test== Test.readWriteTest)
            c.readWrites();
        else
        if (test== Test.flushTest)
            c.flushTime();

    }
}
