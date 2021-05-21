package org.ethereum.datasource;

import co.rsk.crypto.Keccak256;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class DataSourceWithFullReadCache implements KeyValueDataSource {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceWithFullReadCache.class);

    private final KeyValueDataSource base;
    static public String fileName = "";//""f728000.bin";//"f3210000.bin";
    //private final IndexTrie committedCache;
    //private byte[] cache;
    HashMap<ByteArrayWrapper,ByteArrayWrapper> cache;
    long hits;
    long gets;
    long hitsPartial;
    long getsPartial;

    public static class TrieItem  {
        public byte[] hash;
        public byte[] value;

        public TrieItem(byte[] hash, byte[] value) {
            this.hash = hash;
            this.value = value;
        }

        public TrieItem(InputStream in) throws IOException {
            readObject(in);
        }

        private void readObject(InputStream stream)
                throws IOException {

            this.hash =ObjectIO.readNBytes(stream,32);
            int valueLength = ObjectIO.readInt(stream);
            if ((valueLength<0) || (valueLength>10_000_000)) {
                //
                System.out.println(valueLength);
                throw  new RuntimeException("invalid stream");
            }

            //this.value = stream.readNBytes(valueLength);
            byte[] avalue = new byte[valueLength];
            int r =stream.read(avalue,0,valueLength);
            if (r<valueLength) {
                throw  new RuntimeException("invalid stream");
            }
            this.value = avalue;

        }
        private void writeObject(OutputStream out)
                throws IOException {
            if (hash.length!=32) {
                throw new RuntimeException("Invalid hash");
            }
            out.write(hash);
            int length = value.length;
            if (length<0)
                throw new RuntimeException("Invalid length");
            ObjectIO.writeInt(out,length);
            out.write(value);
        }
    }

    public DataSourceWithFullReadCache(KeyValueDataSource base, int cacheSize) {
        this.base = base;
        this.cache = null;
        //this.committedCache = IndexTrie.empty();
       readCache();
    }

    public void setNewFileName(String fn) {
      // Remove previous cache
      fileName = fn;
      readCache();
    }

    public void readCache() {
        cache = new HashMap<>();
        if (fileName.length()==0) return;

        int count = 0;
        System.out.println("Reading cache file: "+fileName);
        try {
            InputStream in;
            //String fileName = "C:\\s\\RSK\\Repos\\block-processor\\f3210000.txt";

            in = new FileInputStream(fileName);

            try {
                for (; ; ) {

                    TrieItem ti = new TrieItem(in);
                    //System.out.println("Count: "+count+" "+count*100/1200000+"%");
                    ByteArrayWrapper b =new ByteArrayWrapper(ti.hash);
                    cache.put(b, new ByteArrayWrapper(ti.value));
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

    long started;
    long partialTime;
    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value= null;
        if (gets==0) {
            started = System.currentTimeMillis();
            partialTime = started;
        }

         try {
             ByteArrayWrapper data = cache.get(wrappedKey);
             if (data!=null) {
                 value = data.getData();
                 hitsPartial++;
                 hits++;
             }
             else {
                 //System.out.println("key: "+wrappedKey.toString());
                 value = base.get(key); // pass-through
                 //System.out.println("value: "+ByteUtil.toHexString(value));
             }
             gets++;
             getsPartial++;
             if (getsPartial%5000==0) {
                 long currentTime = System.currentTimeMillis();
                 // No more than 1/sec
                 if (currentTime>partialTime+1000) {
                     long deltaTime = (currentTime - started);
                     System.out.println("Time[s]: " + (deltaTime / 1000));

                     if (gets>0)
                        System.out.println("total gets: " + gets+" hits: "+hits+" rate: "+(hits*100/gets)+"% gets/sec: "+(gets*1000/deltaTime));
                     if (getsPartial>0)
                        System.out.println("part  gets: " + getsPartial+" hits: "+hitsPartial+" rate: "+(hitsPartial*100/getsPartial)+"% gets/sec: "+(getsPartial*1000/deltaTime));

                     getsPartial=0;
                     hitsPartial=0;
                     partialTime = currentTime;
                 }
             }
        }
        finally {
        }
        return value;
    }

    @Override
    public byte[] put(byte[] key, byte[] value) {
        return base.put(key, value);
    }

    private byte[] put(ByteArrayWrapper wrappedKey, byte[] value) {
        Objects.requireNonNull(value);
        return base.put(wrappedKey.getData(), value);

    }

    @Override
    public void delete(byte[] key) {
        delete(ByteUtil.wrap(key));
    }

    private void delete(ByteArrayWrapper wrappedKey) {
            base.delete(wrappedKey.getData());
    }

    @Override
    public Collection<byte[]> keys() {
            return base.keys();

    }
    @Override
    public Map<ByteArrayWrapper,byte[]> keyValues() {
        return base.keyValues();
    }

    @Override
    public void updateBatch(Map<ByteArrayWrapper, byte[]> rows, Set<ByteArrayWrapper> keysToRemove) {
        base.updateBatch(rows,keysToRemove);
    }

    @Override
    public void flush() {
        base.flush();
    }

    public String getName() {
        return base.getName() + "-with-fullread";
    }

    public void init() {
        base.init();
    }

    public boolean isAlive() {
        return base.isAlive();
    }

    public void close() {
        base.close();
    }
}
