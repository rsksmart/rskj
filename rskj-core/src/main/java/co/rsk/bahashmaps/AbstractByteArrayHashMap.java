package co.rsk.bahashmaps;

import co.rsk.baheaps.ByteArrayHeap;
import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.datasources.FailureTrack;
import co.rsk.datasources.flatdb.LogManager;
import co.rsk.dbutils.ObjectIO;
import co.rsk.packedtables.Table;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractByteArrayHashMap  extends AbstractMap<ByteArrayWrapper, byte[]> implements Map<ByteArrayWrapper, byte[]>, Cloneable, Serializable  {
    private static final long serialVersionUID = 362498820763181265L;
    static final int DEFAULT_INITIAL_CAPACITY = 16;
    static final int MAXIMUM_CAPACITY = 1073741824;
    static final float DEFAULT_LOAD_FACTOR = 0.5F;
    static final long defaultNewBeHeapCapacity = 750_000_000;

    // Is seems that memory mapped index slows down reads at least 10X
    // However, it triples the time of flushes() (but this only makes writing
    // 10% slower on a 10M node file)
    // One example 50M:
    //  spentOnFlush: 136 sec
    // Elapsed time [s]: 226
    // With memory mapped index:
    //  spentOnFlush: 72 sec
    //  Elapsed time [s]: 184
    // Therefore the flush time was reduced 50%, which overall decreased flush time 20%.
    // Therefore it seems that memory mapping does not pay back (unless in Java).
    // Maybe it's because of the JINI overhead.
    static boolean memoryMappedIndex = false;

    static final boolean debugCheckHeap = false;
    static final String debugKey=null;
    transient Table table;
    transient int size;
    transient int modCount;
    int threshold;
    long maxOffset=-1;
    boolean inBatch;
    long maxOffsetToCommit =-1;

    float loadFactor;
    KeyValueDataSource dataSource;

    ///////////////////////////
    // For file I/O
    Format format;
    boolean resized;
    boolean loaded;
    Path mapPath;

    ///////////////////////////
    // For performance evaluation
    // The average number of slot checks per lookup is tableSlotChecks/tableLookups
    public int tableSlotChecks;
    public int tableLookups;

    int maxElements;
    boolean logEvents = true;

    co.rsk.bahashmaps.BAKeyValueRelation BAKeyValueRelation;
    AbstractByteArrayHeap baHeap;

    //////////////////////////////////////////////////////////////////////////
    // Management of marked handle methods and other flags derived from
    // Creation Flags
    //
    boolean supportNullValues;
    boolean supportBigValues;
    boolean storeKeys;
    boolean variableLengthKeys;
    boolean allowRemovals;
    boolean autoUpgrade;
    boolean useLogForBatchConsistency;
    boolean useMaxOffsetForBatchConsistency;
    boolean useMWChecksumForSlotConsistency;

    // This masks are only used if supportNullValues is true.
    // nullMarkedOffsetBitMask marks when a key contains a null value
    long nullMarkedOffsetBitMask ;//= 0x8000000000L;
    final static long emptyMarkedOffset = 0x0L;
    // bigMarkedOffsetBitMask marks when a value is so big that the key
    // is not dynamically computed from the data, but it is stored before
    // the data.
    long bigMarkedOffsetBitMask ;//= 0x4000000000L;
    long numberOfZerosBitMask;
    int numberOfZerosShift;
    long removeMarksMask;

    int keysize;
    LogManager logManager;
    int elementSize;

    void computeMasks() {
        elementSize =getElementSize();
        int slotSizeBits =elementSize*8;//
        int usedBits =0;
        nullMarkedOffsetBitMask = (1L<<(slotSizeBits-1)); usedBits++;
         bigMarkedOffsetBitMask = (1L<<(slotSizeBits-2));usedBits++;
         if (useMWChecksumForSlotConsistency) {
            numberOfZerosBitMask   = 7L*(1L<<(slotSizeBits-5)); // 3 bits
            numberOfZerosShift = (slotSizeBits-5);
             usedBits+=3;

         }
        removeMarksMask = (1L<<(slotSizeBits-usedBits))-1;
    }

    public void deleteLog() {
        if (logManager==null) {
            return;
        }
        logManager.deleteLog();
    }

    public void beginLog() throws IOException {

        if (inBatch)
            throw new RuntimeException("Cannot create a batch if already in a batch");
        inBatch = true;
        // from now on this object should be locked and only one thread should
        // access it. The lock must be provided by the object calling beginLog()
        if (logManager==null) {
            return;
        }
        // Only one thread may begin and work on a log at the same time
        // beginLog() operations cannot be nested
        logManager.beginLog();
    }

    public void endLog() throws IOException {
        if (!inBatch)
            throw new RuntimeException("Not in a batch");
        inBatch = false;
        maxOffsetToCommit = maxOffset;
        if (logManager==null) {
            return;
        }
        logManager.endLog();
    }

    ///////////////////////////
    // For debugging:
    // This data structure is used only for external units tests and debugging.
    // Returns a copy of the data, so you should not use with big tables.
    // DO NOT USE FOR PRODUCTION CODE.
    public class TableItem {
        public int bucket;
        public ByteArrayWrapper key;
        public long markedOffset;
        public byte[] data;
        public byte[] metadata;
        public int priority;
        public int dataHash;
    }



    // Constructor
    public AbstractByteArrayHashMap(int initialCapacity, float loadFactor,
                                    BAKeyValueRelation BAKeyValueRelation,
                                    long newBeHeapCapacity,
                                    AbstractByteArrayHeap sharedBaHeap,
                                    int maxElements,
                                    Format format,
                                    LogManager logManager) {
        this.loadFactor = 0;
        this.format =format;
        if (format != null) {
            expandCreationFlags(format.creationFlags);
        } else
            format = new Format();

        if (BAKeyValueRelation!=null)
            keysize = BAKeyValueRelation.getKeySize();

        computeMasks();

        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        } else {
            if (initialCapacity > 1073741824) {
                initialCapacity = 1073741824;
            }

            if (!(loadFactor <= 0.0F) && !Float.isNaN(loadFactor)) {
                this.loadFactor = loadFactor;
                this.threshold = tableSizeFor(initialCapacity);
            } else {

                throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
            }
            this.BAKeyValueRelation = BAKeyValueRelation;
            this.maxElements = maxElements;

            if (sharedBaHeap==null)
                this.baHeap = createByteArrayHeap(initialCapacity, newBeHeapCapacity);
            else
                this.baHeap = sharedBaHeap;
        }
        this.logManager = logManager;

    }

    void expandCreationFlags(EnumSet<CreationFlag> creationFlags) {
        this.supportNullValues = creationFlags.contains(CreationFlag.supportNullValues);
        this.allowRemovals = creationFlags.contains(CreationFlag.allowRemovals);
        this.supportBigValues = creationFlags.contains(CreationFlag.supportBigValues);
        this.storeKeys = creationFlags.contains(CreationFlag.storeKeys);
        this.variableLengthKeys = creationFlags.contains(CreationFlag.variableLengthKeys);
        this.useLogForBatchConsistency= creationFlags.contains(CreationFlag.useLogForBatchConsistency);
        this.useMaxOffsetForBatchConsistency= creationFlags.contains(CreationFlag.useMaxOffsetForBatchConsistency);
        this.useMWChecksumForSlotConsistency= creationFlags.contains(CreationFlag.useMWChecksumForSlotConsistency);
    }

    protected int getElementSize() {
        return 0; // child class must implement
    }

    public int hash(Object key) {
        // It's important that this hash DOES not collude with the HashMap hash, because
        // we're using hashmaps inside each bucket. If both hashes are the same,
        // then all objects in the same bucket will be also in the same bucked of the
        // hashmap stored in the bucket.
        //
        if (key == null)
            return 0;
        if (BAKeyValueRelation == null)
            return key.hashCode();
        else
            return BAKeyValueRelation.getHashcode(((ByteArrayWrapper) key).getData());
    }

    public int hashOfBA(byte[] key) {
        if (key == null)
            return 0;
        if (BAKeyValueRelation == null)
            return key.hashCode();
        else
            return BAKeyValueRelation.getHashcode(key);
    }


    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return n < 0 ? 1 : (n >= 1073741824 ? 1073741824 : n + 1);
    }



    public AbstractByteArrayHeap getByteArrayHashMap() {
        return this.baHeap;
    }

    AbstractByteArrayHeap createByteArrayHeap(long initialCapacity, long newBeHeapCapacity)  {
        ByteArrayHeap baHeap = new ByteArrayHeap();
        baHeap.setMaxMemory(newBeHeapCapacity); //730_000_000L); // 500 Mb / 1 GB
        baHeap.initialize();
        return baHeap;

    }

    public interface ShouldRemove {
        // Key is only given if data == null.
        // If can be recomputed by user from data.
        boolean remove(byte[] key, byte[] data);
    }

    public AbstractByteArrayHashMap(int initialCapacity, BAKeyValueRelation BAKeyValueRelation,
                                    Format format) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, BAKeyValueRelation,defaultNewBeHeapCapacity,null,
                0,format,null);
    }

    public AbstractByteArrayHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    public AbstractByteArrayHashMap(Map<? extends ByteArrayWrapper, ? extends byte[]> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        this.putMapEntries(m, false);
    }
    
    
    final void putMapEntries(Map<? extends ByteArrayWrapper, ? extends byte[]> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (tableIsNull()) {
                float ft = (float) s / this.loadFactor + 1.0F;
                int t = ft < 1.07374182E9F ? (int) ft : 1073741824;
                if (t > this.threshold) {
                    this.threshold = tableSizeFor(t);
                }
            } else if (s > this.threshold) {
                this.resize();
            }

            Iterator iter = m.entrySet().iterator();

            while (iter.hasNext()) {
                Entry<? extends ByteArrayWrapper, ? extends byte[]> e = (Entry) iter.next();
                ByteArrayWrapper key = e.getKey();
                byte[] value = e.getValue();
                this.putVal(hash(key), key, value, false, evict);
            }
        }

    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public long getPureOffsetFromMarkedOffset(long markedOffset) {
        if (format.dbVersion==0)
            return (markedOffset);
            else
            return (markedOffset & removeMarksMask)-1;
    }

    public long getNullMarkedOffsetFromPureOffset(long pureOffset) {
        if (!supportNullValues)
            throw new RuntimeException("null values not supported");
        return setMWCheck((pureOffset+1) | nullMarkedOffsetBitMask);
    }


    public long getValueMarkedOffsetFromPureOffset(long pureOffset) {
         return setMWCheck(pureOffset+1);
    }

    public long getKeyStoredMarkedOffsetFromPureOffset(long pureOffset) {
        return setMWCheck((pureOffset+1) | bigMarkedOffsetBitMask);
    }

    public boolean isValueMarkedOffset(long markedOffset) {
        if (!supportNullValues)
            return true;
        return ((markedOffset & nullMarkedOffsetBitMask)==0);
    }

    public boolean isKeyStoredMarkedOffset(long markedOffset) {
        if (storeKeys)
            return true;
        if (!supportBigValues)
            return false;
        return ((markedOffset & bigMarkedOffsetBitMask)!=0);
    }

    public boolean isNullMarkedOffset(long markedOffset) {
        if (!supportNullValues) return false;
        return ((markedOffset & nullMarkedOffsetBitMask)!=0);
    }
    //////////////////////////////////////////////////////////////////
    public void refreshedMarkedOffset(long p) {

    }

    public byte[] get(Object key) {
        GetNodeResult result = new GetNodeResult();
        long p = this.getNode(hash(key), key,result);

        if (p!=-1) {
            refreshedMarkedOffset(p);
            this.afterNodeAccess(p,result.data);
            return result.data;
        } else
            return null;
    }

    public byte[] getKeyFromKPD(byte[] keyPlusData) {
        int keyLength;
        int ofs;
        if (variableLengthKeys) {
            keyLength = ObjectIO.getInt(keyPlusData,0);
            ofs = 4;
        } else {
            keyLength = keysize;
            ofs =0;
        }
        byte[] c = new byte[keyLength];
        System.arraycopy(keyPlusData, ofs, c, 0, keyLength);
        return c;
    }

    public byte[] getKeyFromKPD(byte[] kpd,long markedOffset) {
        if (!isValueMarkedOffset(markedOffset)) { //*
            return kpd;
        }

        if (isKeyStoredMarkedOffset(markedOffset))
            return getKeyFromKPD(kpd);

        return BAKeyValueRelation.computeKey(kpd);
    }

    public ByteArrayWrapper getWrappedKeyFromKPD(byte[] kpd,long markedOffset) {
        return  new ByteArrayWrapper(getKeyFromKPD(kpd));
    }

    public byte[] getDataFromKPD(byte[] keyPlusData, long markedOffset) {

        if (!isValueMarkedOffset(markedOffset)) { //*
            return null;
        }

        if (!isKeyStoredMarkedOffset(markedOffset))
            return keyPlusData;

        return getDataFromKPD(keyPlusData);

    }

    public byte[] getDataFromKPD(byte[] keyPlusData) {
        int keySpaceLength;
        if (variableLengthKeys) {
            keySpaceLength = ObjectIO.getInt(keyPlusData,0)+4;
        } else {
           keySpaceLength = keysize;
        }
        byte[] c = new byte[keyPlusData.length - keySpaceLength];
        System.arraycopy(keyPlusData, keySpaceLength, c, 0, c.length);
        return c;

    }
    class GetNodeResult {
        public byte[] data; // This is the actual data, not the kpd
    }

    // Returns a handle or -1 if no node was found (it does not return empty)
    final long getNode(int hash, Object key, GetNodeResult result) {
        tableLookups++;
        if (result!=null) {
            result.data = null;
        }
        if (tableIsNull())
            return -1;
        int n = table.length();
        if (n==0) {
            throw new RuntimeException("empty hashtable error");
        }
        int idx = (n - 1) & hash;
        long markedOffset;
        boolean markedOffsetWasValid;
        do {
            markedOffset  = table.getPos(idx);
            tableSlotChecks++;
            if (markedOffset == emptyMarkedOffset) {
                // If we want to perform auto-repair of "corrupted" hashtables during reads
                // the we can check here if markedOffsetWasInvalid==true.
                // In that case, we fill that slot with emptyMarkedOffset
                return -1;
            }
            markedOffsetWasValid = isValidMarkedOffset(markedOffset);
            if (markedOffsetWasValid) {
                long pureOffset = getPureOffsetFromMarkedOffset(markedOffset);
                byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                if (fastCompareKPDWithKey(kpd, (ByteArrayWrapper) key, markedOffset)) {
                    if (result != null) {
                        result.data = getDataFromKPD(kpd, markedOffset);
                    }
                    return markedOffset;
                }
            }
            idx = (idx+1) & (n-1);
        } while (true);
    }

    public long setMWCheck(long v) {
        if (!useMWChecksumForSlotConsistency)
            return v;

       return  ((1L*countZeros(v))<<numberOfZerosShift) | v;
    }

    int countZeros(long payload) {
        int zeros =0;
        // up to 6 bytes (48 bits)
        for (int i=0;i<elementSize;i++) {
            if ((payload & 0xff) == 0) zeros++;
            payload >>= 8;
        }
        return zeros;
    }
    int invalidEntriesSkipped =0;

    private boolean isValidMarkedOffset(long markedOffset) {
        if ((!useMWChecksumForSlotConsistency) && (!useMaxOffsetForBatchConsistency))
            return true;

        if( useMWChecksumForSlotConsistency) {
            long payload = markedOffset & ~numberOfZerosBitMask;
            int zeros = countZeros(payload);
            int zerosCheck = (int) ((markedOffset & numberOfZerosBitMask) >> numberOfZerosShift);
            boolean valid = (zerosCheck == zeros);
            if (!valid) {
                invalidEntriesSkipped++;
                return false;
            }
        }
        if (useMaxOffsetForBatchConsistency) {
            long pureOffset = getPureOffsetFromMarkedOffset(markedOffset);
            if (pureOffset>maxOffset)
                return false;
        }
        return true;
    }

    public boolean fastCompareKPDWithKey(byte[] kpd,ByteArrayWrapper key,long markedOffset) {
        byte[] keyBytes2 = key.getData();
        byte[] keyBytes1 = getKeyFromKPD(kpd, markedOffset);

        return ByteUtil.fastEquals(keyBytes1,keyBytes2);
    }

    public boolean fastCompareKPDWithKeyOrValue(byte[] kpd,ByteArrayWrapper key,byte[] data,long markedOffset) {
        if ((!storeKeys) && (data!=null) && (isValueMarkedOffset(markedOffset))) { //*
            // this is the same as comparing the key, because the keys depends on the data.
            // The advantage of comparing the data is that we don't need to recompute the key
            // for element oldValue. This optimization depends if the data is big or not.

            byte[] oldValue =getDataFromKPD(kpd,markedOffset);
            return Arrays.equals(oldValue, data);
        }
        return fastCompareKPDWithKey(kpd,key,markedOffset);
    }

    public boolean containsKey(Object key) {
        return this.getNode(hash(key), key,null) != -1;
    }

    public boolean tableIsNull() {
        return (table==null);
    }

      public byte[] put(byte[] value) {
        ByteArrayWrapper key = computeWrappedKey(value);
        return this.putVal(hash(key), key, value, false, true);
    }

    public byte[] put(ByteArrayWrapper key, byte[] value) {
        return this.putVal(hash(key), key, value, false, true);
    }


    final void setItemInTable(int i,int hash,byte[] key,byte[] data,byte[] metadata,boolean evict) {
        long offset ;
        long markedOffsetOld = table.getPos(i);
        if (markedOffsetOld!= emptyMarkedOffset) {
            // We can't remove data from the baHeap now. But we'll keep
            // removal functionality if needed later.

            if (isValidMarkedOffset(markedOffsetOld)) {
                long pureOffsetOld = getPureOffsetFromMarkedOffset(markedOffsetOld);
                baHeap.removeObjectByOfs(pureOffsetOld);
            }
            // this is not needed, because later we overwrite the same slot
            // Also by not setting the element to empty, we allow asynchronous
            // flushes (in the future) as long as writing to a position
            // in the table is atomic (either by using a lock or by native word
            // write)
            // tableSetPos(i, emptyMarkedOffset);
        } else
            this.size++;

        long markedOffsetNew;

        if (data==null) {
            offset = baHeap.addObjectReturnOfs(key, metadata);
            markedOffsetNew =getNullMarkedOffsetFromPureOffset(offset);
        }   else {
            if ((storeKeys) || (data.length>StoreKeyThreshold)) {
                byte[] keyPlusData;
                if (variableLengthKeys) {
                    // Leave room for keylength, then put key length
                    keyPlusData = concat(4,key,data);
                    ObjectIO.putInt(keyPlusData,0,key.length);
                } else {
                    // Append the key before the data
                    keyPlusData = concat(0,key,data);
                }

                offset = baHeap.addObjectReturnOfs(keyPlusData, metadata);
                markedOffsetNew = getKeyStoredMarkedOffsetFromPureOffset(offset);
            } else {
                offset = baHeap.addObjectReturnOfs(data, metadata);
                markedOffsetNew = getValueMarkedOffsetFromPureOffset(offset);
            }
        }

        tableSetPos(i, markedOffsetNew);
        registerNewPureOffset(offset);
        if (offset== debugOffset)
            offset = offset;
        if (i==debugIndex) {
            i = i;
            int tabp = (table.length() - 1) & hash;
            tabp = tabp;
        }

        this.afterNodeInsertion(markedOffsetNew,key,data,evict);
    }

    // Register must be called only when the data key/value has been stored
    // in memory (both the key and the data). This allows another thread to
    // perform a flush() asynchronously.
    void registerNewPureOffset(long offset) {
        if (offset<=maxOffset) {
            if (useMaxOffsetForBatchConsistency)
                   throw new RuntimeException("Cannot add a lower offset. The heap database is corrupted.");
        }
        maxOffset = offset;
        if (!inBatch)
            maxOffsetToCommit = maxOffset;
    }

    final int StoreKeyThreshold = 1024;

    byte[] concat(int leftSpace,byte[] a,byte[] b) {
        byte[] c = new byte[leftSpace+a.length + b.length];
        System.arraycopy(a, 0, c, leftSpace, a.length);
        System.arraycopy(b, 0, c, leftSpace+a.length, b.length);
        return c;
    }

    // This method is public only to recontruct the table using
    //  LogManager.
    public void processLogEntry(int i,long value) {
        if ((tableIsNull()) || (table.length()) == 0) {
            this.resize();
        }
        table.setPos(i, value);
    }

    public void tableSetPos(int i,long value) {
        table.setPos(i, value);
        if (logManager!=null)
            logManager.logSetPos(i,value);
    }

    byte[] getNewMetadata() {
        return null;
    }

    final byte[] putVal(int hash, ByteArrayWrapper key, byte[] value, boolean onlyIfAbsent, boolean evict) {
        int n;
        if ((debugKey!=null) && (key.toString().equals(debugKey))) {
            key = key;
        }
        if ((tableIsNull()) || (n = table.length()) == 0) {
            this.resize();
            n = table.length();
        }

        byte[] oldKPD = null;
        long p;
        int i = n - 1 & hash;

        do {
            p = table.getPos(i);
            // If the position is empty OR invalid, we can reuse it
            if ((p == emptyMarkedOffset) || (!isValidMarkedOffset(p))) {
                setItemInTable(i, hash, key.getData(), value, getNewMetadata(), evict);
                break;
            }

            long pureOffset = getPureOffsetFromMarkedOffset(p);
            oldKPD = baHeap.retrieveDataByOfs(pureOffset);
            boolean sameKey = (fastCompareKPDWithKeyOrValue(oldKPD, key, value, p));
            if (!isValueMarkedOffset(p)) {
                if (sameKey) {
                    // Key matches
                    if (value == null) {
                        // the existing value is associated with null, and also the new value
                        return null;
                    }
                    if (!evict) {
                        // The value (null) already exists
                        return null;
                    }

                    // replace a null value by a non-null value
                    // Do not increase this.size.
                    setItemInTable(i, hash, key.getData(), value, getNewMetadata(), evict);
                    break;
                }
            } else {
                if (value == null) {
                    if (sameKey) {
                        if (!evict)
                            return getDataFromKPD(oldKPD, p);

                        // replace
                        setItemInTable(i, hash, key.getData(), null, getNewMetadata(), evict);
                        break;
                    }
                } else {
                    if (sameKey) {
                        if (!evict)
                            return getDataFromKPD(oldKPD, p);

                        // It the keys depend on values, there is no point in replacing
                        // the value by itself (unless to increase the priority
                        // specified in the metadata).
                        // Anyway, do not increase the size.
                        setItemInTable(i, hash, key.getData(), value, getNewMetadata(), evict);
                        break;
                    }
                }

            }
            i = (i + 1) & (n - 1);
        } while (true);

        if (this.size > this.threshold) {
            this.resize();
        }


        return oldKPD;
    }


    public void removeOnCondition(ShouldRemove rem, boolean notifyRemoval) {
        int tabLen = table.length();
        for (int j = 0; j < tabLen; ++j) {
            long p = table.getPos(j);
            if ((p != emptyMarkedOffset) && (isValidMarkedOffset(p))) {
                byte[] key = null;
                byte[] data = null;
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);

                if (!isValueMarkedOffset(p)) { //* use the fastest
                    key = getKeyFromKPD(kpd);
                } else {
                    data = getDataFromKPD(kpd,p);
                }
                if (rem.remove(key,data)) {
                    tableSetPos(j, emptyMarkedOffset);
                    size--;

                    if (notifyRemoval)
                        this.afterNodeRemoval(key, data, null);
                }
            }
        }
    }


    void resize() {
        resized = true;
        int oldCap = tableIsNull() ? 0 : table.length();
        int oldThr = this.threshold;
        int newThr = 0;
        int newCap;
        if (oldCap > 0) {
            if (oldCap >= 1073741824) {
                this.threshold = 2147483647;
            }

            if ((newCap = oldCap << 1) < 1073741824 && oldCap >= 16) {
                newThr = oldThr << 1;
            }
        } else if (oldThr > 0) {
            newCap = oldThr;
        } else {
            newCap = 16;
            newThr = 12;
        }

        if (newThr == 0) {
            float ft = (float) newCap * this.loadFactor;
            newThr = newCap < 1073741824 && ft < 1.07374182E9F ? (int) ft : 2147483647;
        }

        this.threshold = newThr;
        try {
            renewTable(newCap);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
    
    public void renewTable(int newCap) throws IOException {
        Table oldTab = this.table;
        // TO-DO: If table is memory mapped, this does not work,
        // because it maps to the same file
        if ((memoryMappedIndex) && (oldTab!=null))
            throw new RuntimeException("Memorymapped table resize not implemented");
        Table newTab = createTable(newCap);
        // Filling is not required anymore because the empty element
        // is all zeros. newTab.fill(empty );

        this.table = newTab;
        if (oldTab != null) {
            for(int j = 0; j < oldTab.length(); ++j) {
                long  p= oldTab.getPos(j);
                // Invalid slots are left behind, not copied
                if ((p != emptyMarkedOffset) && (isValidMarkedOffset(p))) {
                    byte[] key;
                    ByteArrayWrapper k;
                    long pureOffset = getPureOffsetFromMarkedOffset(p);
                    byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                    k = getWrappedKeyFromKPD(kpd,p);
                    int hc = hash(k);
                    addToTable(newTab, newCap, hc, p);
                }
            }
        }
    }


    public void addToTable(Table tab, int cap, int hash,long markedOffset) {
        int i = cap - 1 & hash;
        long  p = tab.getPos(i);
        do {
            if (p == emptyMarkedOffset) {
                tab.setPos(i, markedOffset);
                break;
            }
            i = (i + 1) & (cap - 1);
        } while (true);
    }

    public void putAll(Map<? extends ByteArrayWrapper, ? extends byte[]> m) {
        this.putMapEntries(m, true);
    }

    // This method returns the data associated with the key removed
    public byte[] remove(Object key) {
        long markedOffset;
        if (!allowRemovals) {
            throw new RuntimeException("Removals are not allowed");
        }
        markedOffset = this.removeNode(hash(key), key);

        if (markedOffset==-1) {
            return null;
        }

        if (!isValueMarkedOffset(markedOffset)) { //*
            return null;
        }
        long pureOffset = getPureOffsetFromMarkedOffset(markedOffset);
        byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
        return getDataFromKPD(kpd);
    }

    int debugOffset = 954396;
    int debugIndex = 1;

    // removeNode() returns the makedOffset of the element removed
    // or -1 if no element was removed.
    final long  removeNode(int hash, Object key) {

        int n;
        long p;
        int index;
        if (tableIsNull()) return -1;
        n = table.length();
        if (n == 0) return -1;
        byte[] exdata = null;
        byte[] exkey = null;
        int counter =0;
        index = (n - 1) & hash;
        do {
            counter++;

            exkey = null;
            exdata = null;

            if (counter % 100_000==0) {
                System.out.println("scanning table index: "+index+" counter "+counter);
            }
            p = table.getPos(index);
            if (p == emptyMarkedOffset) {
                return -1;
            }
            if (isValidMarkedOffset(p)) {
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                boolean sameKey = fastCompareKPDWithKey(kpd, (ByteArrayWrapper) key, p);
                if (sameKey) {
                    if (isValueMarkedOffset(p)) //*
                        baHeap.removeObjectByOfs(pureOffset);
                    // We don't fill the slot with empty to allow asynchronous
                    // flushes in the future. We overwrite the entry.
                    // tableSetPos(index, emptyMarkedOffset);
                    emptySlotAndFillGap(index, n);
                    break;
                }
            }

            index = (index + 1) & (n - 1);
        } while (true);

        --this.size;

        this.afterNodeRemoval(exkey,exdata,getOptionalMetadata(p) );
        return p;
    }

    public byte[] getOptionalMetadata(long markedOffset)  {
        // We don't use metadata in this class, child classes may use it
        byte[] metadata = null;
        return metadata;
    }



    public void clear() {
        ++this.modCount;
        if (tableIsNull()) return;
        this.size = 0;
        table.fill(emptyMarkedOffset);
        table.clearPageTracking();
    }

    public boolean containsValue(Object value) {

        if ((!tableIsNull()) && this.size > 0) {
            ByteArrayWrapper key = computeWrappedKey( (byte[]) value);
            return containsKey(key);
        }
        return false;
    }

    public Set<ByteArrayWrapper> keySet() {
        Set<ByteArrayWrapper> ks = new HashSet<ByteArrayWrapper>();
        int tabLen = tableLength();

        for (int i = 0; i < tabLen; ++i) {
            long p =table.getPos(i);
            if (p== emptyMarkedOffset)
                continue;
            if (!isValidMarkedOffset(p))
                continue;
            long pureOffset = getPureOffsetFromMarkedOffset(p);
            byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
            byte[] key = getKeyFromKPD(kpd,p);
            ks.add(new ByteArrayWrapper(key));
        }

        return ks;
    }

    public Collection<byte[]> values() {
        // I wonder if a set of byte[] works because byte[] has no
        // hash by value.
        Set<byte[]> vs = new HashSet<byte[]>();

        int tabLen = table.length();

        for (int i = 0; i < tabLen; ++i) {
            long p =table.getPos(i);
            if (p== emptyMarkedOffset)
                continue;
            if (isValueMarkedOffset(p)) { //*
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                byte[] data = getDataFromKPD(baHeap.retrieveDataByOfs(pureOffset),p);
                vs.add(data);
            }
        }

        return vs;
    }

    public Set<Entry<ByteArrayWrapper, byte[]>> entrySet() {
        // TO DO
        return null;
    }

    public byte[] getOrDefault(Object key, byte[] defaultValue) {
        long e;
        GetNodeResult result = new GetNodeResult();
        return (e = this.getNode(hash(key), key,result)) == -1 ? defaultValue : result.data;
    }

    public byte[] putIfAbsent(ByteArrayWrapper key, byte[] value) {
        return this.putVal(hash(key), key, value, true, true);
    }

    public boolean remove(Object key, Object value) {
        if (!allowRemovals) {
            throw new RuntimeException("Removals are not allowed");
        }
        return this.removeNode(hash(key), key) != -1;
    }


    public byte[] computeIfAbsent(ByteArrayWrapper key, Function<? super ByteArrayWrapper, ? extends byte[]> mappingFunction) {
        // TO DO
        return null;
    }

    public byte[] computeIfPresent(ByteArrayWrapper key, BiFunction<? super ByteArrayWrapper, ? super byte[], ? extends byte[]> remappingFunction) {
        // TO DO
        return null;
    }

    public byte[] compute(ByteArrayWrapper key, BiFunction<? super ByteArrayWrapper, ? super byte[], ? extends byte[]> remappingFunction) {
        return null; // TO DO ?
    }

    public byte[] merge(ByteArrayWrapper key, byte[] value, BiFunction<? super byte[], ? super byte[], ? extends byte[]> remappingFunction) {
        return null; // TO DO ?
    }

    public void forEach(BiConsumer<? super ByteArrayWrapper, ? super byte[]> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            if ((this.size > 0) && (!tableIsNull())) {
                int mc = this.modCount;
                int tabLen = table.length();

                for (int i = 0; i < tabLen; ++i) {
                    long p =table.getPos(i);
                    if (p== emptyMarkedOffset)
                        continue;
                    if (!isValidMarkedOffset(p))
                        continue;
                    long pureOffset = getPureOffsetFromMarkedOffset(p);
                    byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                    ByteArrayWrapper aKey = getWrappedKeyFromKPD(kpd,p);
                    byte[] data = getDataFromKPD(kpd,p);
                    action.accept(aKey, data);
                }

                if (this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public byte[] computeKey(byte[] e) {
        return BAKeyValueRelation.computeKey(e);
    }

    public ByteArrayWrapper computeWrappedKey(byte[] e) {
        return new ByteArrayWrapper(computeKey(e));
    }

    public Object clone() {
        ByteArrayRefHashMap result;
        try {
            result = (ByteArrayRefHashMap)super.clone();
        } catch (CloneNotSupportedException var3) {
            throw new InternalError(var3);
        }

        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    final float loadFactor() {
        return this.loadFactor;
    }

    final int capacity() {
        return (!tableIsNull()) ? this.table.length() : (this.threshold > 0 ? this.threshold : 16);
    }

    private void writeObject(ObjectOutputStream s) throws IOException {
        int buckets = this.capacity();
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(this.size);
        this.internalWriteEntries(s);
    }

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        this.reinitialize();
        if (!(this.loadFactor <= 0.0F) && !Float.isNaN(this.loadFactor)) {
            s.readInt();
            int mappings = s.readInt();
            if (mappings < 0) {
                throw new InvalidObjectException("Illegal mappings count: " + mappings);
            } else {
                if (mappings > 0) {
                    float lf = Math.min(Math.max(0.25F, this.loadFactor), 4.0F);
                    float fc = (float)mappings / lf + 1.0F;
                    int cap = fc < 16.0F ? 16 : (fc >= 1.07374182E9F ? 1073741824 : tableSizeFor((int)fc));
                    float ft = (float)cap * lf;
                    this.threshold = cap < 1073741824 && ft < 1.07374182E9F ? (int)ft : 2147483647;
                    //????? TO DO: SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Entry[].class, cap);
                    table = createTable(cap);
                    //table.fill(empty );
                    for(int i = 0; i < mappings; ++i) {
                        ByteArrayWrapper key = (ByteArrayWrapper) s.readObject();
                        byte[] value = (byte[]) s.readObject();
                        this.putVal(hash(key), key, value, false, false);
                    }
                }

            }
        } else {
            throw new InvalidObjectException("Illegal load factor: " + this.loadFactor);
        }
    }

    protected abstract Table createTable(int cap) throws IOException;


    void reinitialize() {
        this.table = null;
        //this.entrySet = null;
        /* TO DO
        this.keySet = null;
        this.values = null;
         */
        this.modCount = 0;
        this.threshold = 0;
        this.size = 0;
    }

    void afterNodeAccess(long markedOffset, byte[] p) {
    }


    void beforeNodeInsertion() {

    }

    void afterNodeInsertion(long markedOffset,byte[] key, byte[] data, boolean evict) {

    }




    public List<TableItem> exportTable() {
        List<TableItem> export = new ArrayList<>();
        int count = table.length();
        for (int c = 0; c < count; ++c) {
            long p = table.getPos(c);
            if ((p!= emptyMarkedOffset) && (isValidMarkedOffset(p))) {
                TableItem ti = getTableItem(c);
                export.add(ti);
            }
        }
        return export;
    }

    public void fillTableItem(TableItem ti) {

    }

    public TableItem getTableItem(int c) {
        TableItem ti = new TableItem();
        ti.markedOffset = table.getPos(c);
        long pureOffset = getPureOffsetFromMarkedOffset(ti.markedOffset);
        byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
        ti.data = getDataFromKPD(kpd,ti.markedOffset);
        ti.key  = getWrappedKeyFromKPD(kpd,ti.markedOffset);
        ti.metadata = baHeap.retrieveMetadataByOfs(pureOffset);

        fillTableItem(ti);

        ti.dataHash = hash(ti.key) ;
        ti.bucket = ti.dataHash & (table.length()-1);
        return ti;
    }

    public void dumpTable() {
        int count = table.length();
        for (int c = 0; c < count; ++c) {
            long p = table.getPos(c);
            if ((p!= emptyMarkedOffset) && (isValueMarkedOffset(p))) {
                TableItem ti = getTableItem(c);
                System.out.println("[" + c +"] " +
                        ByteUtil.toHexString(ti.key.getData() ,0,4)+
                        "=" + ByteUtil.toHexString(ti.data,0,4) +
                        " (bucket "+ti.bucket+
                        " prio " + ti.priority + ")");


            }
        }
    }

    void checkHeap() {
        if (!debugCheckHeap) return;

        int count = table.length();
        for (int c = 0; c < count; ++c) {
            long p = table.getPos(c);
            if ((p != emptyMarkedOffset) && (isValueMarkedOffset(p))) {
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                baHeap.checkObjectByOfs(p);
            }
        }
    }

    void compressHeap() {
        if (logEvents)
            System.out.println("Usage before: "+ baHeap.getUsagePercent());
        baHeap.beginRemap();
        int count = table.length();
        for (int c = 0; c < count; ++c) {
            long p = table.getPos(c);
            if ((p != emptyMarkedOffset) && (isValueMarkedOffset(p))) {
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                baHeap.remapByOfs(pureOffset);
            }
        }
        baHeap.endRemap();
        System.out.println("Usage after: "+ baHeap.getUsagePercent());
    }

    void afterNodeRemoval(byte[] key,byte[] data,byte[] metadata) {

    }



    boolean removeItem(int j,int boundary) {
        size--;
        return emptySlotAndFillGap(j,boundary);
    }

    /////////////////////////////////////////////////////////////////////////////////
    // Warning: This method (emptySlotAndFillGap) is the most complex part of FlatDb.
    // Don't change this method unless you know exactly why each sentence is there
    /////////////////////////////////////////////////////////////////////////////////
    // This method will try to fill the slot j with an element down the hashtable.
    // This slot j will be overwritten (as if it were empty)
    // Then it will move the element at position i to the slot j, and call itself
    // recursively on i, without erasing j first.
    // IF no element i is found, it will set the position i to empty.
    // Returns true if the element used to fill the empty slot j was at an index
    // equal or higher than the one given by argument boundary.
    // Currently, if the table is 100% full, it will loop forever.

    boolean emptySlotAndFillGap(int j, int boundary) {
        int i = j;
        boundary = boundary % table.length();
        boolean crossedBoundary = false;
        boolean wrapAroundZero = false;
        int n = table.length();
        do {
            i = (i + 1) & (n- 1);
            if (i==boundary)
                crossedBoundary = true;
            if (i==0)
                wrapAroundZero = true;
            long h = table.getPos(i);
            if (h == emptyMarkedOffset) {
                tableSetPos(j, emptyMarkedOffset); // empty original slot
                return false;
            }
             if (!isValidMarkedOffset(h)) {
               continue;
             }
            byte[] key;
            long pureOffset = getPureOffsetFromMarkedOffset(h);
            byte[] kpd =  baHeap.retrieveDataByOfs(pureOffset);
            key = getKeyFromKPD(kpd,h);
            int keyHash = hashOfBA(key) ;
            int index = keyHash & (n - 1);

            boolean move = false;

            if (index==j)
                move = true;
            else
            if (index<j) {
                if (wrapAroundZero)
                    move = (index>i);
                else
                    move = true;
            }

            if (move) {
                tableSetPos(j,h);
                crossedBoundary |= emptySlotAndFillGap(i,boundary);
                return crossedBoundary;
            }
        } while (true);
    }


    void internalWriteEntries(ObjectOutputStream s) throws IOException {

        if (this.size > 0 && (!tableIsNull())) {
            int tabLen = table.length();

            for(int i = 0; i < tabLen; ++i) {
                if (table.getPos(i)!= emptyMarkedOffset) {
                    long p =table.getPos(i);
                    if (isValidMarkedOffset(p)) {
                        if (!isValueMarkedOffset(p)) { //*
                            // This is not supported, because we should write the key and flag somehow that
                            // this is not a data entry, but a key entry. TO DO
                            throw new RuntimeException("null entries not supported");
                        }
                        byte[] kpd = baHeap.retrieveDataByOfs(p);
                        //s.writeObject(computeKey(data));
                        s.writeObject(getDataFromKPD(kpd, p));
                    }
                }
            }
        }

    }

    public int countElements() {
        // this should return the same value than size()
        if (tableIsNull()) return 0;
        int count = 0;
        int mc = this.modCount;
        int tabLen = table.length();
        for (int i = 0; i < tabLen; ++i) {
            long p = table.getPos(i);
            if ((p != emptyMarkedOffset) && (isValueMarkedOffset(p))) {
                count++;
            }
        }

        if (this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return count;
    }

    public int longestFilledRun() {
        if (tableIsNull()) return 0;
        int maxRun = 0;
        int run = 0;
        int mc = this.modCount;
        int tabLen = table.length();
        for (int i = 0; i < tabLen; ++i) {
            long p = table.getPos(i);
            if (p != emptyMarkedOffset) {
                run++;
                if (run > maxRun)
                    maxRun = run;
            } else run = 0;
        }

        if (this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return maxRun;
    }

    public double averageFilledRun() {
        if (tableIsNull()) return 0;

        double acum = 0;
        int count = 0;
        int mc = this.modCount;
        int tabLen = table.length();
        for (int i = 0; i < tabLen; ++i) {
            long p = table.getPos(i);
            if (p != emptyMarkedOffset) {
                count++;
                // I need to recover the slot to see if it is in the
                // right slot.
                if (isValidMarkedOffset(p)) {
                    byte[] aKey;
                    long pureOffset = getPureOffsetFromMarkedOffset(p);
                    byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                    aKey = getKeyFromKPD(kpd, p);
                    int pos = hashOfBA(aKey) & (table.length() - 1);
                    if (pos <= i)
                        acum += (i - pos + 1);
                    else {
                        acum += (table.length() - pos) + i;
                    }
                }
            }
        }
        // wrap-around element not counted

        if (this.modCount != mc) {
            throw new ConcurrentModificationException();
        }

        return acum/count;
    }
    // For each entry in the map. Each entry contains key and value.
    public final void forEach(Consumer<? super byte[]> action) {
        if (action == null) {
            throw new NullPointerException();
        } else {
            if (this.size > 0 && (!tableIsNull())) {
                int mc = this.modCount;

                int tabLen = table.length();
                for (int i = 0; i < tabLen; ++i) {
                    long p =table.getPos(i);
                    if ((p!= emptyMarkedOffset) && (isValidMarkedOffset(p))) {
                        if (!isValueMarkedOffset(p)) { //*
                            // Does it make sense to accept a null? Probably not
                        } else {
                            byte[] kpv = baHeap.retrieveDataByOfs(p);
                            byte[] data = getDataFromKPD(kpv,p);
                            action.accept(data);
                        }

                    }
                }

                if (this.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }

        }
    }

    public boolean dataFileExists() {
            File f = mapPath.toFile();
            return (f.exists() && !f.isDirectory());
    }

    public boolean headerFileExists() {
        File f = mapPath.toFile();
        String fileName = f.getAbsolutePath();
        String headerFileName = fileName+".hdr";
        File f2 = new File(headerFileName);
        return (f.exists() && !f.isDirectory());
    }



    public void convertFiles() throws IOException {
        File file = mapPath.toFile();
        String fileName = file.getAbsolutePath();
        Header header = readHeaderFromFile(fileName,false);

        String tmpFileName = fileName+".tmp";
        // Now move the file into a new temporary file, taking out the header
        copySkip(fileName,tmpFileName,12);


        // File (or directory) with old name
        File file1 = new File(tmpFileName);

        // File (or directory) with new name
        File  file2 = new File(fileName);
        file2.delete();

        if (file2.exists())
            throw new java.io.IOException("file still exists");

        // Rename file (or directory)
        boolean success = file1.renameTo(file2);

        String headerFileName = fileName+".hdr";
        writeHeader(headerFileName,header);

    }

    public void copySkip(String srcFileName,String dstFileName,int skip) throws IOException {
        File src = new File(srcFileName);
        File dst = new File(dstFileName);
        try (
                InputStream in = new BufferedInputStream(
                        new FileInputStream(src));
                OutputStream out = new BufferedOutputStream(
                        new FileOutputStream(dst))) {

            in.skip(skip);
            byte[] buffer = new byte[8192];
            int lengthRead;
            while ((lengthRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, lengthRead);
                out.flush();
            }
        }
    }

    public void load()  throws IOException {
        // support old version

        /*if (!headerFileExists()){
            convertFiles();
        }*/
        if ((autoUpgrade) && (headerFileExists())) {
            upgradeHeader();

        }
        if (dataSource!=null) {
            loadFromDataSource(true);
        } else {
            loadFromFiles(true);
        }
    }
    protected void upgradeHeader() throws IOException {
        Header header = loadHeaderFromFile();
        String headerFilename =  getHeaderFilename();
        writeHeaderToFile(headerFilename ,header);
        Files.delete(Paths.get(headerFilename));
    }

    protected class Header  {
        public short magic;
        public short dbVersion;
        public int totalSize;
        public int size ;
        public int threshold;
        public int flags;
        public byte tableSlotSize;
        public long maxOffset;
    }

    public Header loadHeaderFromDataSource() throws IOException {
        Header header = new Header();
        byte[] data= dataSource.get(headerKey);
        ByteArrayInputStream bin = new ByteArrayInputStream(data);
        DataInputStream hdin = new DataInputStream(bin);
        try {
            readFromInputStream( header, hdin);
        } finally {
            hdin.close();
        }
        return header;
    }

    public Header readHeaderFromFile(String fileName, boolean includeVersion) throws IOException {
        Header header = new Header();
        File hfile = new File(fileName);
        FileInputStream hfin = new FileInputStream(hfile);
        BufferedInputStream hbin = new BufferedInputStream(hfin);
        DataInputStream hdin = new DataInputStream(hbin);
        try {
            readFromInputStream( header, hdin);
        } finally {
            hdin.close();
        }
        return header;
    }

    private void readFromInputStream( Header header, DataInputStream hdin) throws IOException {
        header.magic = hdin.readShort();
        header.dbVersion = hdin.readShort();
        header.totalSize =  hdin.readInt();
        header.size = hdin.readInt();
        header.threshold = hdin.readInt();
        try {
            header.flags = hdin.readInt();
            header.tableSlotSize = hdin.readByte();
            header.maxOffset = hdin.readLong();
        }  catch (EOFException e) {
            header.flags = 0;
            header.tableSlotSize = 5;
            header.maxOffset = Long.MAX_VALUE; // Allow anything
        }
    }

    public void loadFromDataSource(boolean map) throws IOException {
        System.out.println("Reading hash table header from DB");
        Header header = loadHeaderFromDataSource();
        fillMapDataWithHeader(header);
        if (!memoryMappedIndex)
            loadTableFromFile(header);
        else
            mapTable(header);
    }

    public void loadFromFiles(boolean map) throws IOException {
        Header header = loadHeaderFromFile();
        fillMapDataWithHeader(header);
        if (!memoryMappedIndex)
            loadTableFromFile(header);
        else
            mapTable(header);
    }

    private Header loadHeaderFromFile() throws IOException {
        System.out.println("Reading hash table header from file");
        File file = mapPath.toFile();
        String fileName = file.getAbsolutePath();
        String headerFileName = fileName + ".hdr";
        Header header = readHeaderFromFile(headerFileName, true);
        return header;

    }

    private void fillMapDataWithHeader(Header header) {
        this.threshold = header.threshold;
        this.size = header.size;
        this.format.dbVersion = header.dbVersion;
        this.maxOffset = header.maxOffset;
        // to-do: read header.tableSlotSize
        // to-do: read header.flags
    }

    void mapTable(Header header) throws IOException {
        table = createTable(header.totalSize);
    }

    void loadTableFromFile(Header header) throws IOException {
        table = createTable(header.totalSize);
        System.out.println("Reading hash table");
        File file = mapPath.toFile();
        FileInputStream fin = new FileInputStream(file);
        BufferedInputStream bin = new BufferedInputStream(fin);
        DataInputStream din = new DataInputStream(bin);
        try {
            table.readFrom(din, header.totalSize);
            loaded = true;
        } finally {
            din.close();
        }
        System.out.println("done");
    }

    public void setAutoUpgrade(boolean autoUpgrade) {
        this.autoUpgrade = autoUpgrade;
    }

    public void setDataSource(KeyValueDataSource ds) {
        this.dataSource =ds;
    }

    public void setPath(Path mapPath) {
        this.mapPath = mapPath;
    }

    public boolean modified() {
        return (!loaded) || ((table!=null) && (table.modified()));
    }

    protected int tableLength() {
        if (table==null)
            return 0;
        return table.length();
    }

    protected void saveToFile() throws IOException {
        String fileName = mapPath.toAbsolutePath().toString();
        createAndWriteHeader(fileName);
        if (!memoryMappedIndex) {
            if (tableLength() != 0) {
                writeTable(fileName);
            }
        }
    }

    protected void saveToDataSource(FailureTrack failureTrack) throws IOException {

        // First we write the pages of the table. This allows to keep consistency
        // In the future the table will also be stored in the datasource, split in pages
        if ((!memoryMappedIndex) && (tableLength()!=0)) {
            String fileName =  mapPath.toAbsolutePath().toString();
            writeTable(fileName);
        } else {
            if (table!=null)
                table.sync();
        }

        if (FailureTrack.shouldFailNow(failureTrack))
            return;
        // Now we write the description, which may include maxOffset for consistency
        createAndWriteHeaderToDataSource();
    }

    public void save(FailureTrack failureTrack) throws IOException {
        if (modified()) {
            if (dataSource!=null) {
                saveToDataSource(failureTrack);
            } else {
                saveToFile();
            }

        }
        loaded = true;
        resized = false;
    }

    public void save() throws IOException {
        save(null);
    }

    protected Header createHeader() {
        Header header = new Header();
        header.flags = (byte) CreationFlag.toBinary(format.creationFlags);
        header.dbVersion = (short) format.dbVersion;
        header.totalSize = tableLength();
        header.size = size;
        header.threshold = threshold;
        header.tableSlotSize = (byte) getElementSize();
        header.maxOffset = maxOffsetToCommit;
        return header;
    }

    protected void createAndWriteHeaderToDataSource() throws IOException  {
        Header header = createHeader();
        writeHeaderToDataSource(header);
    }

    protected void createAndWriteHeader(String fileName) throws IOException {
        Header header = createHeader();
        writeHeaderToFile(fileName, header);
    }

    protected String getHeaderFilename() {
        String fileName =  mapPath.toAbsolutePath().toString();
        String headerFileName = fileName + ".hdr";
        return headerFileName;
    }
    protected void writeHeaderToFile(String fileName,Header header) throws IOException {
        String headerFileName = fileName + ".hdr";
        writeHeader(headerFileName, header);
    }
    private void writeTable(String fileName) throws IOException {
        RandomAccessFile sc
                = new RandomAccessFile(fileName, "rw");
        FileChannel fileChannel = sc.getChannel();

        // Size cannot exceed Integer.MAX_VALUE !! Horrible thing in file.map().
        // However, we can map in parts.
        //ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, 0, 4L * 3);
        try {
            if ((loaded) && (!resized)) {
                System.out.println("Updating table..");
                table.update(fileChannel, 0);
            } else
                table.copyTo(fileChannel, 0);
        } finally {
            fileChannel.close();
        }
    }

    protected void writeHeader(String headerFileName,Header header) throws IOException {
        DataOutputStream hos = new DataOutputStream(
                new FileOutputStream(headerFileName));
        try {
            writeHeaderToOutputStream(header, hos);
        } finally {
            hos.close();
        }
    }
    static final protected byte[] headerKey = "header".getBytes(StandardCharsets.UTF_8);

    void writeHeaderToDataSource(Header header) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream hos = new DataOutputStream(bos);

        try {
            writeHeaderToOutputStream(header, hos);
            dataSource.put(headerKey,bos.toByteArray());
        } finally {
            hos.close();
        }
    }
    private void writeHeaderToOutputStream(Header header, DataOutputStream hos) throws IOException {
        hos.writeShort(header.magic);
        hos.writeShort(header.dbVersion);
        hos.writeInt(header.totalSize);
        hos.writeInt(header.size);
        hos.writeInt(header.threshold);
        hos.writeInt(header.flags);
        hos.writeByte(header.tableSlotSize);
        hos.writeLong(header.maxOffset);
    }


}
