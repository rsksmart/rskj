package co.rsk.baheaps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import co.rsk.spaces.*;
import org.ethereum.datasource.KeyValueDataSource;

// This class represents a heap where elements are addressed by their physical
// position on the heap.
public class ByteArrayHeapBase {
    // The number of maxObjects must be at least 5 times higher than the number of
    // elements that will be inserted in the trie because intermediate nodes
    // consume handles also.
    public static int default_spaceMegabytes = 1600;
    public static int default_maxSpaces = 2;
    public static int default_freeSpaces = 1;
    public static int default_compressSpaces = default_maxSpaces;
    public static int default_remapThreshold = 95;

    public static boolean debugCheckAll = false;
    public static boolean debugLogInfo = true;
    public static boolean logOperations = true;

    public final int remapThreshold;

    public int megas;
    public int spaceSize;
    int maxSpaces;
    long MaxPointer;
    final int freeSpaces;
    final int compressSpaces; // == maxSpaces means compress all

    static final int maxEncodedLengthFieldSize = 4;
    static final int maxDataSize = Integer.MAX_VALUE; // 1 bit reserved to mark extended length
    static final int extendedSize  = (1<<31); // 0x80000000

    public Space[] spaces;
    public BitSet oldSpacesBitmap = new BitSet();
    long rootOfs; // user-provided
    protected KeyValueDataSource descDataSource;

    SpaceHead headOfPartiallyFilledSpaces = new SpaceHead();
    SpaceHead headOfFilledSpaces = new SpaceHead();

    int curSpaceNum;
    boolean memoryMapped;
    String baseFileName;
    int lastMetadataLen = -1;

    /////////////////////////// Only used for remapping
    boolean remapping;
    long remappedSize;
    int compressionPercent;
    ///////////////////////////
    public ByteArrayHeapBase() {
        megas = default_spaceMegabytes;
        maxSpaces = default_maxSpaces;
        freeSpaces = default_freeSpaces;
        compressSpaces = default_compressSpaces;
        remapThreshold = default_remapThreshold;
        spaceSize = megas * 1000 * 1000;
        resetInternalConstants();
        memoryMapped = false;
        // Must call initialize
    }

    public void resetInternalConstants() {
        MaxPointer = 1L * maxSpaces * spaceSize;
    }

    public void setFileMapping(boolean fileMapping) {
        // when using memory mapping maxSpaces should be set dynamically so
        // that every space fits in RAM.

        memoryMapped = fileMapping;
    }

    // Pass null to use external files
    public void setDescriptionFileSource(KeyValueDataSource ds) {
        this.descDataSource =ds;
    }

    public void setFileName(String fileName) {
        baseFileName = fileName;
    }

    public void initialize() {
        // creates the memory set by setMaxMemory()
        reset();
    }

    public long getMaxMemory() {
        return 1L*spaceSize * maxSpaces;
    }

    long desiredMaxMemory;

    public void computeSpaceSizes() {
        if (memoryMapped) {
            // Dynamically set the number of spaces. Half a gigabyte each.
            int desiredSpaceSize =  (1<<29);
            maxSpaces = (int) ((desiredMaxMemory+desiredSpaceSize-1)/desiredSpaceSize);
            spaceSize = desiredSpaceSize;
        }
        long q = desiredMaxMemory / maxSpaces;
        if (q > Integer.MAX_VALUE)
            throw new RuntimeException("Cannot support memory requested");
        spaceSize = (int) q;
        megas = (int) (getMaxMemory() / 1000 / 1000);
    }

    public void setMaxMemory(long m) {
        desiredMaxMemory = m;
    }


    public void setRootOfs(long rootOfs) {
        this.rootOfs = rootOfs;
    }

    protected void saveSpaces() {
        int head = headOfFilledSpaces.head;
        while (head != -1) {
            spaces[head].saveToFile(getSpaceFileName(head));
            head = spaces[head].previousSpaceNum;
        }
        head = headOfPartiallyFilledSpaces.head;
        while (head != -1) {
            spaces[head].saveToFile(getSpaceFileName( head));
            head = spaces[head].previousSpaceNum;
        }
        getCurSpace().saveToFile(getSpaceFileName( curSpaceNum));
    }
    protected void syncSpaces() {
        // Here we MUST synch all spaces using specific OS commands
        // that guarantee the memory pages are actually written to disk.
        int head = headOfFilledSpaces.head;
        while (head != -1) {
            spaces[head].sync();
            head = spaces[head].previousSpaceNum;
        }
        head = headOfPartiallyFilledSpaces.head;
        while (head != -1) {
            spaces[head].sync();
            head = spaces[head].previousSpaceNum;
        }
        getCurSpace().sync();
    }

    public void save() throws IOException {
        if (!memoryMapped) {
            saveSpaces();
        } else {
            syncSpaces();
        }
        saveDesc();
    }

    void saveDesc() {
        HeapFileDesc desc = new HeapFileDesc();
        desc.filledSpaces = getSpaces(headOfFilledSpaces);
        desc.emptySpaces = getSpaces(headOfPartiallyFilledSpaces);
        desc.currentSpace = curSpaceNum;
        desc.rootOfs = rootOfs;
        desc.metadataLen = lastMetadataLen;
        if (descDataSource!=null) {
            desc.SaveToDataSource(descDataSource,"desc");
        } else {
            desc.saveToFile(baseFileName + ".desc");
        }
    }


    public long load() throws IOException {
        HeapFileDesc desc;
        if (descDataSource!=null) {
            desc = HeapFileDesc.loadFromDataSource(descDataSource,"desc");
        } else {
            desc = HeapFileDesc.loadFromFile(baseFileName + ".desc");
        }
        setHead(headOfFilledSpaces, desc.filledSpaces, true);
        setHead(headOfPartiallyFilledSpaces, desc.emptySpaces, false);

        for (int i = 0; i < desc.filledSpaces.length; i++) {
            int num = desc.filledSpaces[i];
            String name = getSpaceFileName(num);
            spaces[num].readFromFile(name,memoryMapped);
        }
        // This are partially filled spaces
        for (int i = 0; i < desc.emptySpaces.length; i++) {
            int num = desc.emptySpaces[i];
            String name = getSpaceFileName( num);
            // Empty spaces do not need to be read from a file
            // What they need is a method of delayed mapped creation,
            // that already exists.
            // spaces[num].readFromFile(name,memoryMapped);
        }
        curSpaceNum = desc.currentSpace;
        lastMetadataLen = desc.metadataLen;
        String name = getSpaceFileName(curSpaceNum);
        spaces[curSpaceNum].readFromFile(name,memoryMapped);
        return desc.rootOfs;
    }

    public void setHead(SpaceHead sh, int[] vec, boolean filled) {
        sh.count = vec.length;
        sh.head = linkSpaces(vec, filled);
    }

    public int[] getSpaces(SpaceHead sh) {
        int h = sh.head;
        int[] vec = new int[sh.count];
        int i = 0;
        while (h != -1) {
            vec[i] = h;
            i++;
            h = spaces[h].previousSpaceNum;
        }
        return vec;
    }

    public int linkSpaces(int[] vec, boolean filled) {
        int prev = -1;
        for (int i = vec.length - 1; i >= 0; i--) {
            int sn = vec[i];
            spaces[sn].previousSpaceNum = prev;
            spaces[sn].filled = filled;
            prev = sn;
        }
        return prev;
    }

    public int getCurSpaceNum() {
        return curSpaceNum;
    }

    public int getCompressionPercent() {
        return compressionPercent;
    }

    class SpaceHead {
        int head = -1; // inicialize in unlinked
        int count = 0;

        public String getDesc() {
            String s = "";
            int aHead = head;
            while (aHead != -1) {
                s = s + aHead + " ";
                aHead = spaces[aHead].previousSpaceNum;
            }
            return s;
        }

        public int removeLast() {
            //  This is iterative because generally there will be only a few spaces.
            // If many spaces are used, then a double-linked list must be used
            int aHead = head;
            int lastHead = -1;
            int preLastHead = -1;
            while (aHead != -1) {
                preLastHead = lastHead;
                lastHead = aHead;
                aHead = spaces[aHead].previousSpaceNum;
            }
            count--;
            if (preLastHead != -1)
                spaces[preLastHead].previousSpaceNum = -1;
            else
                head = -1;
            return lastHead;
        }

        public boolean empty() {
            return head == -1;
        }

        public void addSpace(int i) {
            int prev = head;
            head = i;
            spaces[head].previousSpaceNum = prev;
            count++;
        }

        public void clear() {
            head = -1;
            count = 0;
        }

        public int peekFirst() {
            return head;
        }

        public int removeFirst() {
            if (head == -1)
                throw new RuntimeException("no space avail");
            int s = head;
            head = spaces[head].previousSpaceNum;
            count--;
            return s;
        }
    }


    public int getNewSpaceNum() {
        int s = headOfPartiallyFilledSpaces.removeFirst();
        if (spaces[s].empty())
            createSpace(s);
        spaces[s].unlink();
        return s;
    }

    public Space newSpace() {
        if (memoryMapped)
            return new MemoryMappedSpace();
        else
            return new DirectAccessSpace();
    }

    public void reset() {
        computeSpaceSizes();
        resetInternalConstants();
        headOfPartiallyFilledSpaces.clear();
        spaces = new Space[maxSpaces];

        // Add them in reverse order so the first to take is always 0.
        // This loop will create the space files if they don't already exists in disk!
        for (int i = maxSpaces-1; i >=0; i--) {
            spaces[i] = newSpace();
            headOfPartiallyFilledSpaces.addSpace(i);
        }
        curSpaceNum = getNewSpaceNum();
        headOfFilledSpaces.clear();
        clearRemapMode();
        System.out.println("remapThreshold: " + remapThreshold);
        System.out.println("megas = " + megas);
        System.out.println("spaceSize = " + spaceSize / 1000 / 1000 + "M bytes");
        System.out.println("maxSpaces = " + maxSpaces);
        System.out.println("freeSpaces = " + freeSpaces);
        System.out.println("compressSpaces = " + compressSpaces);

    }

    public void clearRemapMode() {
        remapping = false;
        oldSpacesBitmap.clear();
    }

    public boolean supportsGarbageCollection() {
        return true;
    }

    public boolean isRemapping() {
        return remapping;
    }

    public void beginRemap() {
        oldSpacesBitmap.clear();
        remappedSize = 0;
       chooseSpacesToCompress();

        // Create a vector to mark all objects that need to be moved
        //move = new ArrayList<Integer>();
        remapping = true;
    }

    public void chooseSpacesToCompress() {


        // Compress all spaces ?
        if (compressSpaces == maxSpaces)
            chooseToCompressAllSpaces();
        else
            chooseToCompressSomeSpaces();

    }

    public void chooseToCompressAllSpaces() {
        fillCurrentSpace();
        for (int i = 0; i < maxSpaces; i++) {
            oldSpacesBitmap.set(i);
        }
    }

    public void chooseToCompressSomeSpaces() {
        boolean currentMapAdded = false;
        for (int i = 0; i < compressSpaces; i++) {
            // nothing filled, nothing to do
            if (headOfFilledSpaces.empty()) {
                if (currentMapAdded)
                    break;

                currentMapAdded = true;
                // There is no filled space. Then switch the current space
                // to a filled space and compress that.
                fillCurrentSpace();
            }

            int oldSpaceNum = headOfFilledSpaces.removeLast();
            if (logOperations)
                System.out.println(">> add compress space: " + oldSpaceNum);
            if ((oldSpaceNum == -1) || (spaces[oldSpaceNum] == null))
                throw new RuntimeException("Space should exists");

            if (oldSpaceNum == curSpaceNum) {
                // We're trying to compress the space that is currently active.
                // We should either stop all threads to avoid conflicts or advance
                // the curSpaceNum so that writes happen in the next space.
                // We'll do that.
                //curSpaceNum = (curSpaceNum +1) & maxSpaces;
                throw new RuntimeException("never happens");
            }

            if (!spaces[oldSpaceNum].filled) {
                // Warning: trying to compress a Space that is not filled
                // this can happen if compression is manually triggered
            }
            oldSpacesBitmap.set(oldSpaceNum);
        }
    }


    final boolean useListForSorting = false;



    public void moveCompressedSpacesToPartiallyFilled() {
        // We clear all queues.
        // move all spaces to partially filled
        headOfFilledSpaces.clear();
        headOfPartiallyFilledSpaces.clear();

        for (int i = 0; i < maxSpaces; i++) {
            spaces[i].filled = false;
            headOfPartiallyFilledSpaces.addSpace(i);
        }
    }

    public void moveFilledSpacesToPartiallyFilled() {
        // I remove all filled spaces and move them to partially filled
        // Even if a space has not been compressed.
        // If not compressed, it will be moved back to the filled list once
        // an object is tried to be added to the space
        do {
            int spaceNum = headOfFilledSpaces.removeFirst();
            if (spaceNum == -1) break;
            spaces[spaceNum].filled = false;
            headOfPartiallyFilledSpaces.addSpace(spaceNum);
        } while (true);
    }

    public void endRemap() {
        moveCompressedSpacesToPartiallyFilled();
        clearRemapMode();
        chooseCurrentSpace();
        if (debugCheckAll)
            checkAll();
        remapping = false;
    }



    public void checkAll() {

    }

    public void emptyOldSpaces() {
        long originalSize = 0;
        for (int i = 0; i < maxSpaces; i++) {
            if (oldSpacesBitmap.get(i)) {
                int oldSpaceNum = i;
                originalSize += spaces[oldSpaceNum].memTop;
                spaces[oldSpaceNum].softDestroy();
                headOfPartiallyFilledSpaces.addSpace(oldSpaceNum);
            }
        }
        compressionPercent = (int) (remappedSize * 100 / originalSize);

    }



    public boolean verifyOfsChange(long oldo, long newo) {
        if ((oldo == -1) && (newo != -1)) return false;
        if ((oldo != -1) && (newo == -1)) return false;
        return true;
    }


    final int debugHeaderSize = 2;
    final int M1 = 101;
    final int M2 = 74;

    public void writeDebugFooter(Space space, int ofs) {
        if (ofs > space.spaceSize() - debugHeaderSize) return;
        space.putByte(ofs,(byte)M1);
        space.putByte(ofs + 1, (byte)M2);
    }

    public void writeDebugHeader(Space space, int ofs) {
        if (ofs < debugHeaderSize) return;
        space.putByte(ofs - 2, (byte) M1);
        space.putByte(ofs - 1, (byte) M2);
    }

    public void checkDeugMagicWord(Space space, int ofs) {
        if ((space.getByte(ofs) != M1) || (space.getByte(ofs + 1) != M2))
            throw new RuntimeException("no magic word: ofs=" + ofs + " bytes=" + space.getByte(ofs) + "," + space.getByte(ofs + 1));

    }

    public void checkDebugHeader(Space space, int ofs) {
        if (ofs == 0) return;
        if (ofs < debugHeaderSize)
            throw new RuntimeException("invalid ofs");
        ofs -= 2;
        checkDeugMagicWord(space, ofs);
    }

    public void checkDebugFooter(Space space, int ofs) {
        if (ofs > space.spaceSize() - debugHeaderSize) return;
        checkDeugMagicWord(space, ofs);
    }



    public boolean needsToMoveOfs(long ofs) {
        int objectSpaceNum = getSpaceNumOfPointer(ofs);
        boolean objectOffsetNeedsToChange = (oldSpacesBitmap.get(objectSpaceNum));
        return objectOffsetNeedsToChange;
    }

    public Space getCurSpace() {
        return spaces[curSpaceNum];
    }

    public int getMemSize() {

        return getCurSpace().spaceSize();

    }

    public boolean currentSpaceIsAlmostFull() {
        // To make it faster and test it
        return getCurSpace().getUsagePercent() > remapThreshold;
        //return (mem.length-memTop)<1_000_000;
    }

    public boolean heapIsAlmostFull() {
        // There must be one empty space in the empty queue, because this may be needed during
        // remap if new elements are added with multi-threading.
        // Since currently we're using single-thread, and we're stopping adding new objects
        // during gargage collection, then we can fill all spaces before doing gc.
        return (getUsagePercent() > 90);
    }

    public boolean heapIsAlmostFull_for_multithreading() {
        return (headOfPartiallyFilledSpaces.count <= freeSpaces) &&
                (getCurSpace().getUsagePercent() > remapThreshold);
    }

    public int getWriteSpaceNum() {
        int writeSpace;
        if (isRemapping())
            writeSpace = 1 - curSpaceNum;
        else
            writeSpace = curSpaceNum;
        return writeSpace;
    }

    public long buildPointer(int spaceNum, long ofs) {
        if (ofs == -1)
            return ofs;
        if (ofs >= spaceSize)
            throw new RuntimeException("Invalid space offset given(1)");
        return (1L * spaceNum * spaceSize) + ofs;
    }

    public int getSpaceOfsFromPointer(int spaceNum, long ofs) {
        if (ofs == -1)
            return -1;
        long ofsSpace = ofs / spaceSize;
        if (ofsSpace != spaceNum)
            throw new RuntimeException("Invalid space offset given(2) spaceNum=" + spaceNum + " ofs=" + ofs);
        return (int) (ofs % spaceSize);
    }

    public int getSpaceNumOfPointer(long ofs) {
        if (ofs < 0)
            throw new RuntimeException("Invalid space offset given(3)");
        return (int) (ofs / spaceSize);
    }

    public boolean spaceAvailFor(int msgLength) {
        msgLength += maxEncodedLengthFieldSize;
        return (getCurSpace().spaceAvailFor(msgLength));

    }

    public void checkInRightSpace(int handle) {
        //if (handle==-1) return;
        //long ofs = references[handle];
        //if (ofs==-1)
        //    return;
        //int spaceNum = getSpaceNumOfPointer(ofs);

    }

    public void fillCurrentSpace() {
        if (curSpaceNum == -1) return;
        getCurSpace().filled = true; // mark as filled
        headOfFilledSpaces.addSpace(curSpaceNum);
        curSpaceNum = -1; // No current space available.
    }

    public void moveToNextCurSpace() {
        int oldCurSpaceNum = curSpaceNum;
        if (logOperations)
            System.out.println(">> Filling space " + oldCurSpaceNum);
        fillCurrentSpace();
        chooseCurrentSpace();
    }

    public String getSpaceFileName(String abaseFileName,int n) {
        return abaseFileName+ "." + n + ".space";
    }

    public String getSpaceFileName(int n) {
        return getSpaceFileName(baseFileName,n);
    }

    public void createSpace(int spNum)  {
        if (memoryMapped) {
            try {
                ((MemoryMappedSpace) spaces[spNum]).createMemoryMapped(spaceSize,getSpaceFileName(spNum));
            } catch (IOException e) {
                // wrap now to avoid propagating the IOException
                // In production code, it should propagate it.
                throw new RuntimeException(e.getMessage());
            }
        }
        else
         spaces[spNum].create(spaceSize);
    }

    public void chooseCurrentSpace() {
        curSpaceNum = headOfPartiallyFilledSpaces.removeFirst();
        Space space = getCurSpace();

        if (logOperations)
            System.out.println(">> Switching curspace to " + curSpaceNum);
        if (space.empty())
            createSpace(curSpaceNum);
        else {
            if (logOperations)
                System.out.println(">> This is a partially filled space");
        }
        space.unlink();
        if (logOperations)
            System.out.println(">> Switching done");
    }

    public List<String> getStats() {
        List<String> res = new ArrayList<>(20);
        res.add("usage[%]: " + getUsagePercent());
        res.add("usage[Mb]: " + getMemUsed() / 1000 / 1000);
        res.add("alloc[Mb]: " + getMemAllocated() / 1000 / 1000);
        res.add("max[Mb]: " + getMemMax() / 1000 / 1000);
        res.add("PFilled spaces: " + getPartiallyFilledSpacesCount() + " (" + getPartiallyFilledSpacesDesc() + ")");
        res.add("Filled  spaces: " + getFilledSpacesCount() + " (" + getFilledSpacesDesc() + ")");
        res.add("cur space    : " + getCurSpaceNum());
        res.add("cur space usage[%]: " + getCurSpace().getUsagePercent());
        return res;
    }

    public long addObjectReturnOfs(byte[] encoded, byte[] metadata) {
        Space space;
        int metadataLen =0;
        if (metadata!=null)
            metadataLen = metadata.length;
        if (!spaceAvailFor(1+encoded.length + metadataLen +debugHeaderSize)) {
            moveToNextCurSpace();
            if (remapping)
                throw new RuntimeException("Not yet prepared to switch space during remap");
        }

        space = getCurSpace();


        // We need to store the length because
        // the encoded form does not encode the node length in it.
        int oldMemTop = space.memTop;

        int newMemTop = storeObject(space, oldMemTop,
                encoded, 0, encoded.length,
                metadata,0,metadataLen);

        space.memTop = newMemTop;
        long ofs = buildPointer(curSpaceNum, oldMemTop);
        return ofs;
    }

    public int storeObject(Space destSpace, int destOldMemTop,
                           byte[] encoded,
                           int encodedOffset, int encodedLength,
                           byte[] metadata,
                           int metadataOffset, int metadataLength) {
        int newMemTop = destOldMemTop;

        checkDebugHeader(destSpace, destOldMemTop);

        int len = encodedLength;

        if (lastMetadataLen>=0) {
            if (metadataLength != lastMetadataLen)
                throw new RuntimeException("Metadata must be always the same length");
        } else
            lastMetadataLen = metadataLength;

        if (encodedLength > maxDataSize)
            throw new RuntimeException("encoding too long");

        if (encodedOffset + encodedLength > encoded.length)
            throw new RuntimeException("bad pointers");

        if (metadata!=null) {
            if (metadataOffset + metadataLength > metadata.length)
                throw new RuntimeException("bad pointers");
            destSpace.setBytes(newMemTop,metadata,metadataOffset,metadataLength);
            newMemTop += metadataLength;
        }
        int lenLength = putEncodedLength(destSpace,newMemTop,encodedLength);

        newMemTop += lenLength;

        destSpace.setBytes(newMemTop,encoded,encodedOffset,encodedLength);
        newMemTop += len;
        writeDebugFooter(destSpace, newMemTop);
        newMemTop += debugHeaderSize;
        return newMemTop;
    }


    // returns the length of the field
    public int putEncodedLength(Space destSpace, int newMemTop, int encodedLength) {
        if (encodedLength<=127) {
            destSpace.putByte(newMemTop, (byte) encodedLength); // max 127 bytes
            return 1;
        }
        else {
            // Directly use an int32
            if (encodedLength>maxDataSize)
                throw new RuntimeException("encoded data too large");
            int value = encodedLength | extendedSize;
            // This will cause the first byte to be negative when read as a byte.
            destSpace.putInt(newMemTop, value);
            return 4;
        }

    }
    public int getEncodedLengthLength(Space space, int dataOfs) {
        int len = space.getByte(dataOfs);
        // Zero length is not permitted.
        if ((len <= 127) && (len >= 0)) {
            return 1;
        }
        return 4;
    }

    public int getEncodedLength( Space space, int dataOfs) {
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

    public byte[] retrieveDataByOfs(long encodedOfs) {
        Space space;
        validMetadataLength();


        if (encodedOfs < 0)
            throw new RuntimeException("Disposed reference used (offset "+encodedOfs+")");

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);
        checkDebugHeader(space, internalOfs);
        int dataOfs = internalOfs+lastMetadataLen;
        int elen = getEncodedLength(space,dataOfs);
        byte[] d = new byte[elen ];
        int lenLength = getEncodedLengthLength(space,dataOfs);
        checkDebugFooter(space, internalOfs + lastMetadataLen + lenLength +  d.length);
        space.getBytes(dataOfs + lenLength, d, 0, d.length);
        return d;
    }


    public byte[] retrieveMetadataByOfs(long encodedOfs) {
        Space space;
        validMetadataLength();

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);
        checkDebugHeader(space, internalOfs);
        byte[] d = new byte[lastMetadataLen];
        space.getBytes( internalOfs, d, 0, d.length);
        return d;
    }

    public void setMetadataByOfs(long encodedOfs,byte [] metadata) {
        Space space;
        validMetadataLength();

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);
        checkDebugHeader(space, internalOfs);
        space.setBytes(internalOfs,metadata,0,lastMetadataLen);
     }

    public void checkObjectByOfs(long encodedOfs) {
        Space space;

        int ptrSpaceNum = getSpaceNumOfPointer(encodedOfs);
        space = spaces[ptrSpaceNum];
        int internalOfs = getSpaceOfsFromPointer(ptrSpaceNum, encodedOfs);

        checkDebugHeader(space, internalOfs);
        validMetadataLength();
        // Get the max size window
        int len = getEncodedLength(space,internalOfs+lastMetadataLen);
        int lenLength = getEncodedLengthLength(space,internalOfs+lastMetadataLen);

        checkDebugFooter(space, internalOfs + lastMetadataLen+ lenLength + len);
    }




    public long getMemUsed() {
        long used = 0;

        used += getUsage(headOfFilledSpaces);
        used += getUsage(headOfPartiallyFilledSpaces);

        // While remapping the current space is unusable
        if (curSpaceNum != -1) {
            used += spaces[curSpaceNum].memTop;
        }
        return used;
    }


    public long getMemAllocated() {
        long total = 0;

        total += getSize(headOfFilledSpaces);
        total += getSize(headOfPartiallyFilledSpaces);

        if (curSpaceNum != -1) {
            total += spaces[curSpaceNum].spaceSize();
        }
        return total;
    }

    public long getMemMax() {
        // +1 counts the current space
        long spaceCount = headOfFilledSpaces.count + headOfPartiallyFilledSpaces.count;
        if (curSpaceNum != -1)
            spaceCount++;
        return spaceCount * 1L * spaceSize;
    }


    public long getUsage(SpaceHead queue) {
        long used = 0;
        if (!queue.empty()) {
            int head = queue.peekFirst();
            while (head != -1) {
                used += spaces[head].memTop;
                head = spaces[head].previousSpaceNum;
            }
        }
        return used;
    }

    public long getSize(SpaceHead queue) {
        long total = 0;
        if (!queue.empty()) {
            int head = queue.peekFirst();
            while (head != -1) {
                if (spaces[head].created())
                    total += spaces[head].spaceSize();
                else
                    total += spaceSize; // it will be created later
                head = spaces[head].previousSpaceNum;
            }
        }
        return total;
    }

    public int getUsagePercent() {
        long used = 0;
        long total = 0;

        used += getUsage(headOfFilledSpaces);
        used += getUsage(headOfPartiallyFilledSpaces);

        total += getSize(headOfFilledSpaces);
        total += getSize(headOfPartiallyFilledSpaces);

        // While remapping the current space is unusable
        if (curSpaceNum != -1) {
            used += spaces[curSpaceNum].memTop;
            total += spaces[curSpaceNum].spaceSize();
        }

        if (total == 0)
            return 0;

        int percent =(int) ((long) used * 100 / total);
        return percent;
    }

    public int getFilledSpacesCount() {
        return headOfFilledSpaces.count;
    }

    public String getGarbageCollectionDescription() {
        return getRemovingSpacesDesc();
    }

    public String getRemovingSpacesDesc() {
        String s = "";
        for (int i = 0; i < maxSpaces; i++) {
            if (oldSpacesBitmap.get(i))
                s = s + i + " ";
        }
        return s;
    }

    public String getFilledSpacesDesc() {
        return headOfFilledSpaces.getDesc();
    }

    public String getPartiallyFilledSpacesDesc() {
        return headOfPartiallyFilledSpaces.getDesc();
    }

    public int getPartiallyFilledSpacesCount() {
        return headOfPartiallyFilledSpaces.count;
    }

    public void removeObjectByOfs(long encodedOfs) {

    }

    public void remapByOfs(long encodedOfs) {

    }
}

