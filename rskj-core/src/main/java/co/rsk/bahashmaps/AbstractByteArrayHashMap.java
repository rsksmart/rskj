package co.rsk.bahashmaps;

import co.rsk.baheaps.ByteArrayHeap;
import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.packedtables.Table;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
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


    static final boolean debugCheckHeap = false;
    static final String debugKey=null;
    transient Table table;
    transient int size;
    transient int modCount;
    int threshold;

    float loadFactor;

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
    // Management of marked handle methods
    boolean supportNullValues;
    boolean supportBigValues;
    boolean allowRemovals;
    // This masks are only used if supportNullValues is true.
    final static long nullMarkedOffsetBitMask = 0x8000000000L;
    final static long emptyMarkedOffset = 0x0L;
    final static long bigMarkedOffsetBitMask = 0x4000000000L;
    long removeMarksMask;
    int keysize;

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
                                    Format format) {
        this.loadFactor = 0;
        this.format =format;
        if (format != null) {
            this.supportNullValues = format.creationFlags.contains(CreationFlag.supportNullValues);
            this.allowRemovals = format.creationFlags.contains(CreationFlag.allowRemovals);
            this.supportBigValues = format.creationFlags.contains(CreationFlag.supportBigValues);
        } else
            format = new Format();

        keysize = BAKeyValueRelation.getKeySize();

        // Try to use all possible heap space if some features are not supported.
        removeMarksMask = Long.MAX_VALUE;
        if (supportBigValues)
            removeMarksMask = bigMarkedOffsetBitMask -1;
        else
            if (supportNullValues)
                removeMarksMask = nullMarkedOffsetBitMask-1;


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

    public enum CreationFlag {
        supportNullValues, allowRemovals,supportBigValues;

        public static final EnumSet<CreationFlag> All = EnumSet.allOf(CreationFlag.class);
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
                0,format);
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
        return ((pureOffset+1) | nullMarkedOffsetBitMask);
    }

    public long getValueMarkedOffsetFromPureOffset(long pureOffset) {
         return (pureOffset+1);
    }

    public long getBigValueMarkedOffsetFromPureOffset(long pureOffset) {
        return (pureOffset+1) | bigMarkedOffsetBitMask;
    }

    public boolean isValueMarkedOffset(long markedOffset) {
        if (!supportNullValues)
            return true;
        return ((markedOffset & nullMarkedOffsetBitMask)==0);
    }

    public boolean isBigValueMarkedOffset(long markedOffset) {
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
        byte[] c = new byte[keysize];
        System.arraycopy(keyPlusData, 0, c,0 , keysize);
        return c;

    }
    public byte[] getKeyFromKPD(byte[] kpd,long markedOffset) {
        if (!isValueMarkedOffset(markedOffset)) { //*
            return kpd;
        }

        if (isBigValueMarkedOffset(markedOffset))
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

        if (!isBigValueMarkedOffset(markedOffset))
            return keyPlusData;

        return getDataFromKPD(keyPlusData);

    }
    public byte[] getDataFromKPD(byte[] keyPlusData) {
        byte[] c = new byte[keyPlusData.length - keysize];
        System.arraycopy(keyPlusData, keysize, c,0 , c.length);
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
        int idx = (n - 1) & hash;
        long markedOffset;
        do {
            markedOffset  = table.getPos(idx);
            tableSlotChecks++;
            if (markedOffset == emptyMarkedOffset)
                return -1;
            long pureOffset = getPureOffsetFromMarkedOffset(markedOffset);
            byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
            if (fastCompareKPDWithKey(kpd,(ByteArrayWrapper)key,markedOffset)) {
                if (result != null) {
                    result.data =  getDataFromKPD(kpd,markedOffset);
                }
                return markedOffset;
            }

            idx = (idx+1) & (n-1);
        } while (true);
    }

    public boolean fastCompareKPDWithKey(byte[] kpd,ByteArrayWrapper key,long markedOffset) {
        byte[] keyBytes2 = key.getData();
        byte[] keyBytes1 = getKeyFromKPD(kpd, markedOffset);

        return ByteUtil.fastEquals(keyBytes1,keyBytes2);
    }

    public boolean fastCompareKPDWithKeyOrValue(byte[] kpd,ByteArrayWrapper key,byte[] data,long markedOffset) {
        if ((data!=null) && (isValueMarkedOffset(markedOffset))) { //*
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
        if (table.getPos(i)!= emptyMarkedOffset) {
            // We can't remove data from the baHeap now. But we'll keep
            // removal functionality if needed later.
            long pureOffset = getPureOffsetFromMarkedOffset(table.getPos(i));
            baHeap.removeObjectByOfs(pureOffset);
            tableSetPos(i, emptyMarkedOffset);
        } else
            this.size++;

        long markedOffset;

        if (data==null) {
            offset = baHeap.addObjectReturnOfs(key, metadata);
            markedOffset =getNullMarkedOffsetFromPureOffset(offset);
        }   else {
            if (data.length>StoreKeyThreshold) {
                // Append the key before the data
                byte[] keyPlusData = concat(key,data);
                offset = baHeap.addObjectReturnOfs(keyPlusData, metadata);
                markedOffset = getBigValueMarkedOffsetFromPureOffset(offset);
            } else {
                offset = baHeap.addObjectReturnOfs(data, metadata);
                markedOffset = getValueMarkedOffsetFromPureOffset(offset);
            }
        }
        tableSetPos(i, markedOffset);

        if (offset== debugOffset)
            offset = offset;
        if (i==debugIndex) {
            i = i;
            int tabp = (table.length() - 1) & hash;
            tabp = tabp;
        }

        this.afterNodeInsertion(markedOffset,key,data,evict);
    }

    final int StoreKeyThreshold = 1024;

    byte[] concat(byte[] a,byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    void tableSetPos(int i,long value) {
        table.setPos(i, value);
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
            if (p == emptyMarkedOffset) {
                setItemInTable(i,hash,key.getData(),value, getNewMetadata(),evict);
                break;
            }

            long pureOffset = getPureOffsetFromMarkedOffset(p);
            oldKPD =baHeap.retrieveDataByOfs(pureOffset);
            boolean sameKey =(fastCompareKPDWithKeyOrValue(oldKPD,key,value,p));
            if (!isValueMarkedOffset(p)) { //*
                if (sameKey) {
                    // Key matches
                    if (value==null)  {
                        // the existing value is associated with null, and also the new value
                        return null;
                    }
                    if (!evict) {
                        // The value (null) already exists
                        return null;
                    }

                    // replace a null value by a non-null value
                    // Do not increase this.size.
                    setItemInTable(i,hash,key.getData(),value, getNewMetadata(),evict);
                    break;
                }
            } else {
                if (value==null) {
                    if (sameKey) {
                        if (!evict)
                            return getDataFromKPD(oldKPD,p);

                        // replace
                        setItemInTable(i,hash,key.getData(), null, getNewMetadata(),evict);
                        break;
                    }
                } else {
                    if (sameKey) {
                        if (!evict)
                            return getDataFromKPD(oldKPD,p);

                        // It the keys depend on values, there is no point in replacing
                        // the value by itself (unless to increase the priority
                        // specified in the metadata).
                        // Anyway, do not increase the size.
                        setItemInTable(i, hash,key.getData(), value, getNewMetadata(),evict);
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
            if (p != emptyMarkedOffset) {
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
        renewTable(newCap);
    }
    
    public void renewTable(int newCap) {
        Table oldTab = this.table;
        Table newTab = createTable(newCap);
        // Filling is not required anymore because the empty element
        // is all zeros. newTab.fill(empty );

        this.table = newTab;
        if (oldTab != null) {
            for(int j = 0; j < oldTab.length(); ++j) {
                long  p= oldTab.getPos(j);
                if (p != emptyMarkedOffset) {
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

    public byte[] remove(Object key) {
        long e;
        if (!allowRemovals) {
            throw new RuntimeException("Removals are not allowed");
        }
        e = this.removeNode(hash(key), key);

        if (e==-1) {
            return null;
        }

        if (!isValueMarkedOffset(e)) { //*
            return null;
        }
        byte[] kpd = baHeap.retrieveDataByOfs(e);
        return getDataFromKPD(kpd);
    }

    int debugOffset = 954396;
    int debugIndex = 1;

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
            long pureOffset = getPureOffsetFromMarkedOffset(p);
            byte[] kpd =baHeap.retrieveDataByOfs(pureOffset);
            boolean sameKey =fastCompareKPDWithKey(kpd,(ByteArrayWrapper) key,p);
            if (sameKey) {
                if (isValueMarkedOffset(p)) //*
                    baHeap.removeObjectByOfs(pureOffset);
                tableSetPos(index, emptyMarkedOffset);
                fillGap(index,n);
                break;
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
        int tabLen = table.length();

        for (int i = 0; i < tabLen; ++i) {
            long p =table.getPos(i);
            if (p== emptyMarkedOffset)
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

    protected abstract Table createTable(int cap);


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
            if (p!= emptyMarkedOffset) {
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
            if (p!= emptyMarkedOffset) {
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
            if (p != emptyMarkedOffset) {
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
            if (p != emptyMarkedOffset) {
                baHeap.remapByOfs(p);
            }
        }
        baHeap.endRemap();
        System.out.println("Usage after: "+ baHeap.getUsagePercent());
    }

    void afterNodeRemoval(byte[] key,byte[] data,byte[] metadata) {

    }



    boolean removeItem(int j,int boundary) {
        tableSetPos(j, emptyMarkedOffset);
        size--;
        return fillGap(j,boundary);
    }

    // This method will try to fill the empty slot j.
    // Returns true if the element used to fill the empty slot j was at an index equal or higher than
    // the one given by argument boundary.
    boolean fillGap(int j,int boundary) {

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
            if (h == emptyMarkedOffset)
                return false;
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
                tableSetPos(j,table.getPos(i));
                tableSetPos(i, emptyMarkedOffset);
                crossedBoundary |=fillGap(i,boundary);
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
                    if (!isValueMarkedOffset(p)) { //*
                        // This is not supported, because we should write the key and flag somehow that
                        // this is not a data entry, but a key entry. TO DO
                        throw new RuntimeException("null entries not supported");
                    }
                    byte[] kpd = baHeap.retrieveDataByOfs(p);
                    //s.writeObject(computeKey(data));
                    s.writeObject(getDataFromKPD(kpd,p));
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
            if (p != emptyMarkedOffset) {
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
                byte[] aKey;
                long pureOffset = getPureOffsetFromMarkedOffset(p);
                byte[] kpd = baHeap.retrieveDataByOfs(pureOffset);
                aKey = getKeyFromKPD(kpd,p);
                int pos = hashOfBA(aKey) & (table.length()-1);
                if (pos<=i)
                    acum +=(i-pos+1);
                else {
                    acum +=(table.length()-pos)+i;
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
                    if (p!= emptyMarkedOffset) {
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
        Header header = readHeader(fileName,false);

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
        readFromFiles(true);
    }

    protected class Header  {
        public int dbVersion;
        public int totalSize;
        public int size ;
        public int threshold;
    }

    public Header readHeader(String fileName,boolean includeVersion) throws IOException {
        Header header = new Header();
        File hfile = new File(fileName);
        FileInputStream hfin = new FileInputStream(hfile);
        BufferedInputStream hbin = new BufferedInputStream(hfin);
        DataInputStream hdin = new DataInputStream(hbin);

        try {
            if (includeVersion)
                header.dbVersion = hdin.readInt();
            header.totalSize =  hdin.readInt();
            header.size = hdin.readInt();
            header.threshold = hdin.readInt();
        } finally {
            hdin.close();
        }
        return header;
    }

    public void readFromFiles( boolean map) throws IOException {
        System.out.println("Reading hash table header");
        File file = mapPath.toFile();
        String fileName = file.getAbsolutePath();
        String headerFileName = fileName+".hdr";

        Header header = readHeader(headerFileName,true);
        this.threshold = header.threshold;
        this.size = header.size;
        this.format.dbVersion = header.dbVersion;

        table = createTable(header.totalSize);
        System.out.println("Reading hash table");
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

    public void setPath(Path mapPath) {
        this.mapPath = mapPath;
    }

    public boolean modified() {
        return (!loaded) || (table.modified());
    }

    public void save() throws IOException {

        if ((!loaded) || (table.modified())) {
            File file = mapPath.toFile();
            String fileName = file.getAbsolutePath();
            String headerFileName = fileName + ".hdr";
            Header header = new Header();

            header.dbVersion = format.dbVersion;
            header.totalSize = table.length();
            header.size = size;
            header.threshold = threshold;

            writeHeader(headerFileName, header);

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
        loaded = true;
        resized = false;
        /* Slower
        DataOutputStream os = new DataOutputStream(
                new FileOutputStream(fileName));

        //write the length first so that you can later know how many ints to read
        os.writeInt(table.length());
        os.writeInt(size);
        os.writeInt(threshold);
        for (int i =0 ; i < table.length(); ++i){
            os.writeInt(table.getPos(i]);
        }
        os.close();
         */
    }

    void writeHeader(String headerFileName,Header header) throws IOException {
        DataOutputStream hos = new DataOutputStream(
                new FileOutputStream(headerFileName));
        try {
            hos.writeInt(header.dbVersion);
            hos.writeInt(header.totalSize);
            hos.writeInt(header.size);
            hos.writeInt(header.threshold);
        } finally {
            hos.close();
        }
    }


}
