package co.rsk.baheaps;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import co.rsk.spaces.*;
import org.ethereum.datasource.KeyValueDataSource;

// This class represents a heap where elements are addressed by their physical
// position on the heap.
public class FreeHeapBase {
    // The number of maxObjects must be at least 5 times higher than the number of
    // elements that will be inserted in the trie because intermediate nodes
    // consume handles also.
    public static int default_spaceMegabytes = 1600;

    public static boolean debugCheckAll = false;
    public static boolean debugLogInfo = true;
    public static boolean logOperations = true;

    public int megas;

    long used;


    static final int maxEncodedLengthFieldSize = 4;
    static final int maxDataSize = Integer.MAX_VALUE; // 1 bit reserved to mark extended length
    static final int extendedSize  = (1<<31); // 0x80000000


    protected KeyValueDataSource descDataSource;
    boolean autoUpgrade;

    String baseFileName;
    int lastMetadataLen = -1;

    protected final ReadWriteLock dataLock = new ReentrantReadWriteLock();

    UnifiedSpace space;

    /////////////////////////// Only used for remapping
    boolean remapping;
    long remappedSize;
    int compressionPercent;
    ///////////////////////////

    public FreeHeapBase() {
        megas = default_spaceMegabytes;
        space = new UnifiedSpace(megas);
        space.resetInternalConstants();
        space.memoryMapped = false;
        // Must call initialize
    }


    public void setPageSize(int pageSize) {
        space.setPageSize(pageSize);
    }

    public void setStoreHeadmap(boolean storeHeadmap)  {
        space.setStoreHeadmap( storeHeadmap);
    }

    public void setFileMapping(boolean fileMapping) {
        // when using memory mapping maxSpaces should be set dynamically so
        // that every space fits in RAM.

        space.memoryMapped = fileMapping;
    }

    // Pass null to use external files
    public void setDescriptionFileSource(KeyValueDataSource ds) {
        this.descDataSource =ds;
    }
    public void setAutoUpgrade(boolean autoUpgrade) {
        this.autoUpgrade =autoUpgrade;
    }


    public void setFileName(String fileName) {
        baseFileName = fileName;
        space.baseFileName = fileName;
    }

    public void initialize() {
        // creates the memory set by setMaxMemory()
        reset();
    }

    public long getMaxMemory() {
        return getCapacity();
    }

    long desiredMaxMemory;

    public void computeSpaceSizes() {
        if (space.memoryMapped) {
            // Dynamically set the number of spaces. Half a gigabyte each.
            int desiredSpaceSize =  (1<<29);
            space.maxSpaces = (int) ((desiredMaxMemory+desiredSpaceSize-1)/desiredSpaceSize);
            space.spaceSize = desiredSpaceSize;
        }
        long q = desiredMaxMemory / space.maxSpaces;
        if (q > Integer.MAX_VALUE)
            throw new RuntimeException("Cannot support memory requested");
        space.spaceSize = (int) q;
        megas = (int) (getMaxMemory() / 1000 / 1000);
    }

    public void setMaxMemory(long m) {
        desiredMaxMemory = m;
    }


    public void readLock() {
        dataLock.readLock().lock();
    }

    public void readUnlock() {
        dataLock.readLock().unlock();
    }

    public void writeLock() {
        dataLock.writeLock().lock();
    }

    public void writeUnlock() {
        dataLock.writeLock().unlock();
    }

    protected void saveSpaces() {
        readLock(); try {
            space.save();
        } finally {
            readUnlock();
        }
    }

    protected void syncSpaces() {
        space.sync();
    }

    public void save() throws IOException {
        readLock(); try {
            space.saveOrSync();

        saveDesc();
        } finally {
            readUnlock();
        }
    }

    void saveDesc() {
        HeapFileDesc desc = new HeapFileDesc();
        desc.metadataLen = lastMetadataLen;
        if (descDataSource!=null) {
            desc.saveToDataSource(descDataSource,"desc");
        } else {
            desc.saveToFile(baseFileName + ".desc");
        }
    }

    protected boolean descFileExists() {
        return Files.exists(Paths.get(baseFileName + ".desc"));
    }

    protected void deleteDescFile() {
        try {
            Files.delete(Paths.get(baseFileName + ".desc"));
        } catch (IOException e) {
            // ignore
        }
    }

    public void load() throws IOException {
        HeapFileDesc desc;
        if (descDataSource!=null) {
            desc = HeapFileDesc.loadFromDataSource(descDataSource,"desc");
        } else {
            desc = HeapFileDesc.loadFromFile(baseFileName + ".desc");
        }

        space.load();


    }


    public void reset() {
        computeSpaceSizes();
        space.reset();

        clearRemapMode();
        System.out.println("megas = " + megas);
        System.out.println("spaceSize = " + space.spaceSize / 1000 / 1000 + "M bytes");
        System.out.println("usableSpaceSize= " + space.usableSpaceSize / 1000 / 1000 + "M bytes");
        System.out.println("maxSpaces = " + space.maxSpaces);
        System.out.println("pageSize = " + space.pageSize);

    }

    public void clearRemapMode() {
        remapping = false;
    }

    public boolean supportsGarbageCollection() {
        return true;
    }

    public boolean isRemapping() {
        return remapping;
    }

    public void beginRemap() {
        remappedSize = 0;

        remapping = true;
    }


    public void endRemap() {
        writeLock();
        try {
            clearRemapMode();
            if (debugCheckAll)
                checkAll();
            remapping = false;
        } finally {
            writeUnlock();
        }
    }



    public void checkAll() {

    }


    public boolean verifyOfsChange(long oldo, long newo) {
        if ((oldo == -1) && (newo != -1)) return false;
        if ((oldo != -1) && (newo == -1)) return false;
        return true;
    }


    final int debugHeaderSize = 2;
    final int M1 = 101;
    final int M2 = 74;

    public void writeDebugFooter(long uofs) {
        if (uofs > space.size() - debugHeaderSize) return;
        space.putByte(uofs,(byte)M1);
        space.putByte(uofs + 1, (byte)M2);
    }

    public void writeDebugHeader(int uofs) {
        if (uofs < debugHeaderSize) return;
        space.putByte(uofs - 2, (byte) M1);
        space.putByte(uofs - 1, (byte) M2);
    }

    public void checkDeugMagicWord(long uofs) {
        if ((space.getByte(uofs) != M1) || (space.getByte(uofs + 1) != M2))
            throw new RuntimeException("no magic word: ofs=" + uofs + " bytes=" + space.getByte(uofs) + "," + space.getByte(uofs + 1));

    }

    public void checkDebugHeader(long uofs) {
        if (uofs == 0) return;
        if (uofs < debugHeaderSize)
            throw new RuntimeException("invalid ofs");
        uofs -= 2;
        checkDeugMagicWord(uofs);
    }

    public void checkDebugFooter(long uofs) {
        if (uofs > space.size() - debugHeaderSize) return;
        checkDeugMagicWord(uofs);
    }


    public boolean heapIsAlmostFull() {
        // There must be one empty space in the empty queue, because this may be needed during
        // remap if new elements are added with multi-threading.
        // Since currently we're using single-thread, and we're stopping adding new objects
        // during gargage collection, then we can fill all spaces before doing gc.
        return (getUsagePercent() > 90);
    }



    public List<String> getStats() {
        List<String> res = new ArrayList<>(20);
        res.add("usage[%]: " + getUsagePercent());
        res.add("usage[Mb]: " + getMemUsed() / 1000 / 1000);
        res.add("alloc[Mb]: " + getMemAllocated() / 1000 / 1000);
        res.add("max[Mb]: " + getMemMax() / 1000 / 1000);
        return res;
    }


    public long findUOfsForObject(long uofs,int length) {
        long headFound = -1;
        for (long i=uofs;i>=0;i--) {
            if (space.getHeadBit(i)) {
                headFound = i;
                break;
            }
        }
        long nextOfs = uofs;
        long initialOfs = uofs;
        if (headFound != -1) {
            boolean wrapAroundZero = false;
            do {
                // Now skip the object.
                checkDebugHeader(headFound);
                long dataOfs = headFound + lastMetadataLen;
                int elen = getEncodedLength(dataOfs);
                byte[] d = new byte[elen];
                int lenLength = getEncodedLengthLength(dataOfs);
                nextOfs = headFound + lastMetadataLen + lenLength + elen;
                if (nextOfs>space.maxPointer)
                     nextOfs -=space.maxPointer;
                if (nextOfs < initialOfs) {
                    wrapAroundZero = true;
                } else
                if ((nextOfs >= initialOfs) && (wrapAroundZero)) {
                    break; // wrap around the initial point
                }
                if (space.getHeadBit(nextOfs)) {
                    headFound = nextOfs;
                } else
                    break;
            } while (true);
        }

        // now we have a nextOfs ready to write
        // TO-DO check for wrap-around
        return nextOfs;
    }

    public long storeObject(long uOffset,
                           byte[] encoded,
                           int objectLength,
                           byte[] metadata,int metadataLength) {

        if (lastMetadataLen>=0) {
            if (metadataLength != lastMetadataLen)
                throw new RuntimeException("Metadata must be always the same length");
        } else
            lastMetadataLen = metadataLength;

        if (objectLength > maxDataSize)
            throw new RuntimeException("encoding too long");


        int totalLength = debugHeaderSize+metadataLength+calcEncodedLengthLength(objectLength)+objectLength;
        uOffset = findUOfsForObject(uOffset,totalLength);
        long start = uOffset;
        if (metadata!=null) {
            space.setBytes(uOffset,metadata,0,metadataLength);
            uOffset += metadataLength;
        }
        int lenLength = putEncodedLength(uOffset,objectLength);

        uOffset += lenLength;

        space.setBytes(uOffset,encoded,0,objectLength);
        uOffset += encoded.length;
        //writeDebugFooter(destSpace, newMemTop);
        //newMemTop += debugHeaderSize;
        space.setHeadBit(start);
        return start;
    }



    // returns the length of the field
    public int putEncodedLength(long uofs, int encodedLength) {
        if (encodedLength<=127) {
            space.putByte(uofs, (byte) encodedLength); // max 127 bytes
            return 1;
        }
        else {
            // Directly use an int32
            if (encodedLength>maxDataSize)
                throw new RuntimeException("encoded data too large");
            int value = encodedLength | extendedSize;
            // This will cause the first byte to be negative when read as a byte.
            space.putInt(uofs, value);
            return 4;
        }

    }

    public int calcEncodedLengthLength(int encodedLength) {
        if (encodedLength<=127) {
            return 1;
        }
        else {
            return 4;
        }

    }
    public int getEncodedLengthLength(long dataOfs) {
        int len = space.getByte(dataOfs);
        // Zero length is not permitted.
        if ((len <= 127) && (len >= 0)) {
            return 1;
        }
        return 4;
    }

    public int getEncodedLength(long dataOfs) {
        int len = space.getByte(dataOfs);

        if ((len <= 127) && (len >= 0)) {
            return len;
        }
        int value = space.getInt(dataOfs);
        return (value & maxDataSize);
    }

    public void validMetadataLength() {
        if (lastMetadataLen==-1)
            throw new RuntimeException("no data stored");
    }

    public byte[] retrieveDataByOfs(long uofs) {
        // Does not require a read lock, because writes cannot
        // change already stored data.

        validMetadataLength();
        if (uofs < 0)
            throw new RuntimeException("Disposed reference used (offset "+uofs+")");


        if (!space.getHeadBit(uofs)) {
            throw new RuntimeException("Invalid offset");
        }
        checkDebugHeader(uofs);
        long dataOfs = uofs+lastMetadataLen;
        int elen = getEncodedLength(dataOfs);
        byte[] d = new byte[elen ];
        int lenLength = getEncodedLengthLength(dataOfs);
        checkDebugFooter(uofs + lastMetadataLen + lenLength +  d.length);
        space.getBytes(dataOfs + lenLength, d, 0, d.length);
        return d;
    }


    public byte[] retrieveMetadataByOfs(long upofs) {
        validMetadataLength();

        checkDebugHeader(upofs);
        byte[] d = new byte[lastMetadataLen];
        space.getBytes( upofs, d, 0, d.length);
        return d;
    }

    public void setMetadataByOfs(long upofs,byte [] metadata) {

        validMetadataLength();

        checkDebugHeader(upofs);
        space.setBytes(upofs,metadata,0,lastMetadataLen);
     }

    public void checkObjectByOfs(long upofs) {

        checkDebugHeader(upofs);
        validMetadataLength();
        // Get the max size window
        int len = getEncodedLength(upofs+lastMetadataLen);
        int lenLength = getEncodedLengthLength(upofs+lastMetadataLen);

        checkDebugFooter( upofs + lastMetadataLen+ lenLength + len);
    }


    public long getMemUsed() {
        readLock();
        try {
            return getUsage();
        } finally {
            readUnlock();
        }
    }


    public long getMemAllocated() {
        readLock();
        try {
            long total = getCapacity();
            return total;
        } finally {
            readUnlock();
        }
    }

    public long getMemMax() {
        return getCapacity();
    }


    public long getUsage() {
        return used;
    }

    public long getCapacity() {
        return space.size();
    }

    public int getUsagePercent() {
        readLock(); try {
            long used = 0;
            long total = 0;

            used  = getUsage();
            total = getCapacity();
            if (total == 0)
                return 0;

            int percent = (int) ((long) used * 100 / total);
            return percent;
        } finally {
            readUnlock();
        }
    }


    public void removeObjectByOfs(long encodedOfs) {

    }

    public void remapByOfs(long encodedOfs) {

    }

   /// New
    public long retrieveNextDataOfsByOfs(long upofs) {

        if (!space.getHeadBit(upofs)) {
            throw new RuntimeException("Invalid offset");
        }
        // get size
        checkDebugHeader(upofs);
        long dataOfs = upofs+lastMetadataLen;
        int elen = getEncodedLength(dataOfs);
        byte[] d = new byte[elen ];
        int lenLength = getEncodedLengthLength(dataOfs);
        long nextOfs = upofs + lastMetadataLen + lenLength +  elen;
        checkDebugFooter(nextOfs);
        return nextOfs;
    }

    public boolean isObjectStoredAtOfs(long encodedOfs) {
        return (!isOfsAvail(encodedOfs));
    }

    public boolean isOfsAvail(long uofs) {
        return (space.getHeadBit(uofs)==false);
    }

    public void setMetadataLength(int metadataLen) {
        lastMetadataLen = metadataLen;
    }

    /*public boolean spaceAvailAtOfs(long encodedOfs,int encodedLength) {
        int msgLen = 1 + encodedLength + lastMetadataLen + debugHeaderSize;
        return spaceAvailFor(encodedOfs,msgLen);
    }

     */

    public void addObjectAtOfs(long uofs, byte[] encoded, byte[] metadata) {
        writeLock();
        try {

            int metadataLen = 0;
            if (metadata != null) {
                metadataLen = metadata.length;
            }

             storeObject(uofs,
                    encoded, encoded.length,
                    metadata,  metadataLen);

        } finally {
            writeUnlock();
        }
    }
}

