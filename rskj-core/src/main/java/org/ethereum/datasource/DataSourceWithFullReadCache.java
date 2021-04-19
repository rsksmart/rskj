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
    //private final IndexTrie committedCache;
    //private byte[] cache;
    HashMap<ByteArrayWrapper,ByteArrayWrapper> cache;

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

    public void readCache() {
        int count = 0;

        try {
            InputStream in;
            //String fileName = "C:\\s\\RSK\\Repos\\block-processor\\f3210000.txt";
            String fileName = "f3210000.bin";
            in = new FileInputStream(fileName);
            cache = new HashMap<>();
            try {
                for (; ; ) {

                    TrieItem ti = new TrieItem(in);
                    //System.out.println("Count: "+count+" "+count*100/1200000+"%");
                    cache.put(new ByteArrayWrapper(ti.hash), new ByteArrayWrapper(ti.value));
                    count++;
                    if (count % 100000 == 0) {
                        System.out.println("Count: " + count + " " + count * 100 / 1200000 + "%");
                    }
                }
            } catch (EOFException exc) {
                // end of stream
            }
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] get(byte[] key) {
        Objects.requireNonNull(key);
        ByteArrayWrapper wrappedKey = ByteUtil.wrap(key);
        byte[] value= null;

         try {
             ByteArrayWrapper data = cache.get(wrappedKey);
             if (data!=null)
                value = data.getData();
             else
                value = base.get(key); // pass-through
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
    public Set<byte[]> keys() {
            return base.keys();

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
