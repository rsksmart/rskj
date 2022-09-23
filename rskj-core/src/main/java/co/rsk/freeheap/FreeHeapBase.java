package co.rsk.freeheap;

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

    List<ScanMethod> list;
    public int megas;

    long used;


    static final int maxEncodedLengthFieldSize = 4;
    static final int maxDataSize = Integer.MAX_VALUE; // 1 bit reserved to mark extended length
    static final int extendedSize  = (1<<31); // 0x80000000


    protected KeyValueDataSource descDataSource;
    boolean autoUpgrade;

    String baseFileName;
    int lastMetadataLen = -1;
    int maxObjectSize;
    boolean twoLevelHeader;

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
        twoLevelHeader = true;
        space.memoryMapped = false;
        // Must call initialize
    }

    public int getMaxObjectSize() { return maxObjectSize; }

    public void setMaxObjectSize(int mos) {
        maxObjectSize = mos;
    }

    int         filesystemPageSize;
;
    public void setFileSystemPageSize(int pageSize) {
        filesystemPageSize = pageSize;
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
        createScanList();
    }

    public void createScanList() {
            list = new ArrayList();
            list.add(new ScanMethod(header0CompressionBytes, 0, false));
            list.add(new ScanMethod(header0CompressionBytes , 0, true));
            list.add(new ScanMethod(header1CompressionBytes, header1start, false));
            list.add(new ScanMethod(header1CompressionBytes , header1start, true));
            for (int i=0;i<list.size()-1;i++) {
                list.get(i).nextBoundaryBytes = list.get(i+1).getRequiredAlignment();
            }
    }
    public long getMaxMemory() {
        return getCapacity();
    }

    long desiredMaxMemory;



    final static int header0CompressionBits = 8;
    final static int header1CompressionBits = 8*16;
    final static int header0CompressionBytes = 1;
    final static int header1CompressionBytes = 16;
    final static int header0bytesPerKibibyte = 1024/ header0CompressionBits;
    final static int header1bytesPerKibibyte = 1024/ header1CompressionBits;
    final static int headerTotalBytesPerKibibyte = header0bytesPerKibibyte + header1bytesPerKibibyte;
    int header0bytes ;
    int header1bytes;
    int header1start;

    public void computeSpaceSizes() {
        int usablePageSize = filesystemPageSize/2;
        header0bytes = usablePageSize /header0CompressionBits;
        header1bytes= usablePageSize /header1CompressionBits;
        int headerSize = header0bytes + header1bytes;
        int pageSize = usablePageSize + headerSize;
        space.setPageSize(pageSize);
        space.setPageHeaderSize(headerSize);
        space.setUsablePageSize(usablePageSize);
        header1start = header0bytes;
        if (usablePageSize % 16!=0)
            throw new RuntimeException("bad boundary");

        // We account for the need of the bitmap.
        // For the fine granularity header, we take 1 bit every byte
        // so it's 128 bytes
        // For the coarse granularity header, we take 1 bit every 16 bytes,
        // so the space needed is 8 bytes.

        long pages1024 = (maxMemory+1023)/1024;
        long overhead = pages1024* headerTotalBytesPerKibibyte;
        //long usable = pages1024* (1024-headerTotalBytesPerKibibyte);
        desiredMaxMemory = maxMemory+overhead;

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

    long maxMemory;

    public void setMaxMemory(long m) {
        maxMemory = m;

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
        if (uofs > space.getCapacity() - debugHeaderSize) return;
        space.putByte(uofs,(byte)M1);
        space.putByte(uofs + 1, (byte)M2);
    }

    public void writeDebugHeader(long uofs) {
        if (uofs < debugHeaderSize) return;
        space.putByte(uofs , (byte) M1);
        space.putByte(uofs +1, (byte) M2);
    }

    public void checkDeugMagicWord(long uofs) {
        if ((space.getByte(uofs) != M1) || (space.getByte(uofs + 1) != M2))
            throw new RuntimeException("no magic word: ofs=" + uofs + " bytes=" + space.getByte(uofs) + "," + space.getByte(uofs + 1));

    }

    public void checkDebugHeader(long uofs) {
        if (uofs < 0)
            throw new RuntimeException("invalid ofs");
        checkDeugMagicWord(uofs);
    }

    public void checkDebugFooter(long uofs) {
        if (uofs > space.getCapacity() - debugHeaderSize) return;
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

    public long scanHeaderBit(int headerOffset,int compressionBytes,long uofs,int maxObjectSlotSize,
                              int direction,boolean value,boolean set) {
        long headFound = -1;

        long i = uofs;
        int c = compressionBytes*direction;
        for (int count=0; count < maxObjectSlotSize; c++) {
            if (set) {
                  space.setHeadBit(headerOffset, compressionBytes, i, value);
                } else {
                if (space.getHeadBit(headerOffset, compressionBytes, i) == value) {
                    headFound = i;
                    break;
                }
            }
            i +=c;
            if (i>space.maxPointer)
                i -=space.maxPointer;
            else if (i<0)
                    i +=space.maxPointer;
        }
        return headFound;
    }

    public long scanHeaderByte(int headerOffset,int compressionBytes,long uofs,int maxObjectSlotSize,
                               int direction,boolean value,boolean set) {
        long headFound = -1;

        int c = compressionBytes*8*direction;
        long i = uofs;
        for (int count=0; count < maxObjectSlotSize; c++) {
            if (set) {
                space.setHeadByte(headerOffset,compressionBytes, i,value);
            } else {
                if (space.getHeadByte(headerOffset, compressionBytes, i) == value) {
                    headFound = i;
                    break;
                }
            }
            i +=c;
            if (i>space.maxPointer)
                i -=space.maxPointer;
            else if (i<0)
                i +=space.maxPointer;
        }
        return headFound;
    }


    class ScanMethod {
        int boundaryBytes;
        int headerOffset;
        boolean byteSearch;
        public int nextBoundaryBytes;

        int getRequiredAlignment() {
            if (byteSearch)
                return boundaryBytes*8;
            else
                return boundaryBytes;
        }

        public ScanMethod(int boundaryBytes,
                int headerOffset,
                boolean byteSearch) {
            this.boundaryBytes = boundaryBytes;
            this.headerOffset = headerOffset;
            this.byteSearch = byteSearch;
        }
    }

/*

        // First we check bits until we fill a byte
        remScan = (int) Math.min(length, uofs % 8);
        if (remScan>0) {
            headFound0_bit = scanHeaderBit(0, header0CompressionFactor, uofs, remScan);
            length -=remScan;
            uofs -=remScan;
        }

        if (headFound0_bit==-1) {
            // Until the header1CompressionFactor limit, jumping 8 bits a time,
            int scanStep = header1CompressionFactor;
            long uofs_start1 = (uofs / scanStep) * scanStep;
            remScan = (int) Math.min(length, (uofs - uofs_start1));
            headFound0_byte = scanHeaderByte(0, header0CompressionFactor*8, uofs, remScan);
            length -= remScan;
            uofs -=remScan;

            if (headFound0_byte == -1) {

                int scanStep = header1CompressionFactor*8;
                remScan = (length / scanStep) * scanStep;
                headFound1 = scanHeader(header0bytes, header1CompressionFactor, uofs_start1, remScan);
                length -= remScan;

                // Now we're at the begining of a header1CompressionFactor boundary
                // continue scanning but jumping header1CompressionFactor;
                remScan = (length / header1CompressionFactor) * header1CompressionFactor;
                headFound1 = scanHeader(header0bytes, header1CompressionFactor, uofs_start1, remScan);
                length -= remScan;
                if (headFound1 == -1) {
                    // continue with the remainder
                    long uofs_start0 = uofs_start1 - remScan;
                    headFound0 = scanHeader(0, header0CompressionFactor, uofs_start0, remScan);
                } else {
                    // If found in header1, look inside this header to find the exact position with header0
                    long uofs_start0 = headFound1 + header0CompressionFactor - 1;
                    headFound0 = scanHeader(0, header0CompressionFactor, uofs_start0, header1CompressionFactor);
                    if (headFound0 == -1) {
                        throw new RuntimeException("Invalid map");
                    }
                }
            } else {
                // Look for the bit within the byte
                remScan = 1;
                headFound0_bit = scanHeaderBit(0, header0CompressionFactor, headFound0_byte, remScan);
                if (headFound0_bit == -1) {
                    throw new RuntimeException("Invalid map");
            }
        }

 */

    public long scanForward(List<ScanMethod> list,long uofs,int length, boolean setToTrue) {
        // We'll try to find the first zero bit
        int index = 0;
        long headFound = -1;
        ScanMethod s = null;
        boolean value;
        if (setToTrue) {
            value = true;
        } else
            value = false;
        // go from fine to coarse
        for (index=0; index<list.size();index++) {
            s = list.get(index);
            int remScan;
            if (s.nextBoundaryBytes>0) {
                // if we're already in the last byte of the boundary, then
                // there is nothing to do
                if (uofs % s.nextBoundaryBytes==0)
                    continue;

                // Number of bytes required to reach the boundary
                int boundaryBytes = (int) (s.nextBoundaryBytes - (uofs % s.nextBoundaryBytes));
                remScan = Math.min(length, boundaryBytes);
            } else
                remScan = length;
            if (s.byteSearch)
                headFound = scanHeaderByte(s.headerOffset,s.boundaryBytes, uofs, remScan,1,value,setToTrue);
            else
                headFound = scanHeaderBit(s.headerOffset,s.boundaryBytes, uofs, remScan,1,value,setToTrue);
            uofs -=remScan;
            length -=remScan;
            if (headFound!=-1) break;

            if (length==0) break;
        }
        if (headFound==-1)
            return -1;

        // now it has found it at a certain index, we have to look into the current block boundary
        // from coarse to fine
        while (index>0) {
            int boundaryBytes = s.getRequiredAlignment();
            if (boundaryBytes!=0) {
                if (headFound % boundaryBytes != 0)
                    throw new RuntimeException("bad robot");

            } else {
                boundaryBytes = length; // ??????
            }
            uofs = headFound; // we move to the end of the boundary
            index--;
            s = list.get(index);
            int remScan = boundaryBytes;
            if (s.byteSearch)
                headFound = scanHeaderByte(s.headerOffset,s.boundaryBytes, uofs, remScan,1,value,setToTrue);
            else
                headFound = scanHeaderBit(s.headerOffset,s.boundaryBytes, uofs, remScan,1,value,setToTrue);
        }
        return headFound;
    }

    public long scan(List<ScanMethod> list,long uofs,int length) {
          int index = 0;
          long headFound = -1;
          ScanMethod s = null;
          // go from fine to coarse
          for (index=0; index<list.size();index++) {
              s = list.get(index);
              int remScan;
              if (s.nextBoundaryBytes>0) {
                  // if we're already in the last byte of the boundary, then
                  // there is nothing to do
                  if (uofs % s.nextBoundaryBytes==(s.nextBoundaryBytes-1))
                      continue;

                  // Number of bytes required to reach the boundary
                  int boundaryBytes = (int) (uofs % s.nextBoundaryBytes) + 1;
                  //if (boundaryBytes == 0)  // never zero
                  //    boundaryBytes = s.boundary;
                  remScan = Math.min(length, boundaryBytes);
                  if (remScan>uofs+1)
                    remScan = (int) uofs+1;
              } else
                  remScan = length;
              if (s.byteSearch)
                 headFound = scanHeaderByte(s.headerOffset,s.boundaryBytes, uofs, remScan,-1,false,false);
              else
                  headFound = scanHeaderBit(s.headerOffset,s.boundaryBytes, uofs, remScan,-1,false,false);
              uofs -=remScan;
              length -=remScan;
              if (headFound!=-1) break;

              if (length==0) break;
          }
        if (headFound==-1)
            return -1;
         // now it has found it at a certain index, we have to look into the current block boundary
         // from coarse to fine
        while (index>0) {
            int boundaryBytes = s.getRequiredAlignment();
            if (boundaryBytes!=0) {
                if (headFound % boundaryBytes != (boundaryBytes - 1))
                    throw new RuntimeException("bad robot");

            } else {
                boundaryBytes = length; // ??????
            }
            uofs = headFound; // we move to the end of the boundary
            index--;
            s = list.get(index);
            int remScan = boundaryBytes;
            if (s.byteSearch)
                headFound = scanHeaderByte(s.headerOffset,s.boundaryBytes, uofs, remScan,-1,false,false);
            else
                headFound = scanHeaderBit(s.headerOffset,s.boundaryBytes, uofs, remScan,-1,false,false);
        }
        return headFound;
    }

    public long findUOfsForObject(long uofs) {
        return findUOfsForObject(uofs,maxObjectSize,true);
    }

    public long findNearestMark(long uofs) {
        long headFound = -1;
        long initialOfs = uofs;

        headFound = scan(list,uofs,maxObjectSize);
        return headFound;
    }

    public long forwardFindUOfsForObject(long uofs) {
        long headFound = -1;
        long initialOfs = uofs;

        headFound = scanForward(list, uofs, maxObjectSize,false);

        // We always leave 1 byte empty
        if (isObjectStoredAtOfs(headFound-1))
            headFound++;

        if (debug) {
            System.out.println("found at: " + headFound);
        }
        return headFound;
    }

    public long findUOfsForObject(long uofs,int length,boolean backwards) {
        long headFound = -1;
        long initialOfs = uofs;
        if (backwards) {
            headFound = scan(list, uofs, length);

            if (headFound == -1) {
                return uofs;
            }
        } else
            headFound = uofs;

        if (debug) {
            System.out.println("found at: " + headFound);
        }
        if (!space.getHeadBit(0,1,headFound)) {
            // error...
            // repeat for debugging
            // headFound = scan(list,uofs,length);
            throw new RuntimeException("Object expected at position");

        }
        long nextOfs = uofs;

        boolean wrapAroundZero = false;
        boolean pastInitialPoint = false;
        do {
            // Now skip the object.
            checkDebugHeader(headFound);
            long dataOfs = debugHeaderSize + headFound + lastMetadataLen;
            int elen = getEncodedLength(dataOfs);
            byte[] d = new byte[elen];
            int lenLength = getEncodedLengthLength(dataOfs);
            nextOfs = dataOfs + lenLength + elen;
            if (nextOfs > space.maxPointer) {
                nextOfs -= space.maxPointer;
                wrapAroundZero = true;
            }

            if (!pastInitialPoint) {
                if (nextOfs > initialOfs) {
                    pastInitialPoint = true;
                }
            } else {
                if (wrapAroundZero) {
                    // no more place to store objects
                    throw new RuntimeException("no more space");
                }
            }


            if (space.getHeadBit(0, header0CompressionBytes, nextOfs)) {
                headFound = nextOfs;
            } else
                break;
        } while (true);

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
        int recordLength = debugHeaderSize + metadataLength +
                getEncodedLengthLength(objectLength) + objectLength;
        int c;
        uOffset = forwardFindUOfsForObject(uOffset);
        /*
        boolean backwards = true;
        long initialOfs =uOffset;
        do {
            //int totalLength = debugHeaderSize+metadataLength+calcEncodedLengthLength(objectLength)+objectLength;
            uOffset = findUOfsForObject(uOffset, maxObjectSize,backwards);

            // Now try to find a header to see if there is enough room FORWARD
            long testOffset = uOffset;
            c = recordLength;
            while (c > 0) {
                if (space.getByte(testOffset) != 0) {
                    //uOffset = findUOfsForObject(initialOfs, maxObjectSize,backwards);
                    break; // space not found.
                }
                testOffset++;
                c--;
            }
            if (c!=0) {
                backwards = false;
                uOffset = testOffset;
            }
        } while (c!=0);

        if (debug) {
            System.out.println("store at: " + uOffset);
            if (uOffset == 147365559) {
                System.out.println("break");
            }
        }

         */
        writeDebugHeader(uOffset);
        long start = uOffset;
        uOffset +=debugHeaderSize;

        if (metadata!=null) {
            space.setBytes(uOffset,metadata,0,metadataLength);
            uOffset += metadataLength;
        }
        int lenLength = putEncodedLength(uOffset,objectLength);

        uOffset += lenLength;

        space.setBytes(uOffset,encoded,0,objectLength);
        uOffset += encoded.length;
        if (debug) {
            if (start == 147365559) {
                System.out.println("end at " + uOffset);
            }
        }
        //writeDebugFooter(destSpace, newMemTop);
        //newMemTop += debugHeaderSize;
        markObjectAtOffset(start, (int) (uOffset-start+1),true);
        return start;
    }
    boolean debug = false;

    public void markObjectAtOffset(long start,int count,boolean value) {
        scanForward(list, start, count,true);

    }

    public void markObjectAtOffset(long start,boolean value) {
        space.setHeadBit(0, header0CompressionBytes, start,value);
        space.setHeadBit(header1start, header1CompressionBytes, start,value);
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

    public boolean objectExistsAt(long uofs) {
        if (uofs<0)
            uofs +=space.maxPointer;

        return space.getHeadBit(0, header0CompressionBytes,uofs);
    }

    public byte[] retrieveDataByOfs(long uofs) {
        // Does not require a read lock, because writes cannot
        // change already stored data.

        validMetadataLength();
        if (uofs < 0)
            throw new RuntimeException("Disposed reference used (offset "+uofs+")");


        if (!objectExistsAt(uofs)) {
            throw new RuntimeException("Invalid offset");
        }
        checkDebugHeader(uofs);
        long dataOfs = debugHeaderSize+uofs+lastMetadataLen;
        int elen = getEncodedLength(dataOfs);
        byte[] d = new byte[elen ];
        int lenLength = getEncodedLengthLength(dataOfs);
        //checkDebugFooter(uofs + lastMetadataLen + lenLength +  d.length);
        space.getBytes(dataOfs + lenLength, d, 0, d.length);
        return d;
    }


    public byte[] retrieveMetadataByOfs(long upofs) {
        validMetadataLength();

        checkDebugHeader(upofs);
        byte[] d = new byte[lastMetadataLen];
        space.getBytes( upofs+debugHeaderSize, d, 0, d.length);
        return d;
    }

    public void setMetadataByOfs(long upofs,byte [] metadata) {

        validMetadataLength();

        checkDebugHeader(upofs);
        space.setBytes(upofs+debugHeaderSize,metadata,0,lastMetadataLen);
     }

    public void checkObjectByOfs(long upofs) {

        checkDebugHeader(upofs);
        validMetadataLength();
        // Get the max size window
        int len = getEncodedLength(upofs+debugHeaderSize+lastMetadataLen);
        int lenLength = getEncodedLengthLength(upofs+lastMetadataLen);

        //checkDebugFooter( upofs + lastMetadataLen+ lenLength + len);
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
        return space.getCapacity();
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

        if (!objectExistsAt(upofs)) {
            throw new RuntimeException("Invalid offset");
        }
        // get size
        checkDebugHeader(upofs);
        long dataOfs = debugHeaderSize+upofs+lastMetadataLen;
        int elen = getEncodedLength(dataOfs);
        byte[] d = new byte[elen ];
        int lenLength = getEncodedLengthLength(dataOfs);
        long nextOfs = upofs + lastMetadataLen + lenLength +  elen;
        //checkDebugFooter(nextOfs);
        return nextOfs;
    }

    public boolean isObjectStoredAtOfs(long encodedOfs) {
        return (objectExistsAt(encodedOfs));
    }

    public boolean isOfsAvail(long uofs) {
        return (!objectExistsAt(uofs));
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

