package co.rsk.spaces;

import java.io.IOException;
import java.nio.ByteBuffer;

public class UnifiedSpace {
    public Space[] spaces;
    // Only used for flaty
    public int pageSize;
    boolean storeHeadmap;
    int pageHeaderSize;
    int usablePageSize; // = pageSize-headMapSize
    public boolean memoryMapped;
    public int maxSpaces;
    public String baseFileName;
    public int spaceSize;
    public int usableSpaceSize; // How much of each space is usable ?
    public static int default_maxSpaces = 2;

    public long maxPointer;

    public void resetInternalConstants() {
        usableSpaceSize = spaceSize/pageSize*usablePageSize;
        maxPointer = 1L * maxSpaces * usableSpaceSize;
    }

    public long getCapacity() {
        return maxPointer;
    }

    public long size() {
        return maxSpaces*spaceSize;
        // spaceSize*spaces.length;
    }

    public UnifiedSpace(int megas) {
        maxSpaces = default_maxSpaces;
        spaceSize = megas * 1000 * 1000;
    }

    // Input:  a Suof (SpaceNum , usedOfs),
    // Output: A pointer only points to the payload part of the paged memory
    public long buildUOfs(int spaceNum, long internalOfs) {
        if (internalOfs == -1)
            return internalOfs;
        if (internalOfs >= usableSpaceSize)
            throw new RuntimeException("Invalid space offset given(1)");
        return (1L * spaceNum * usableSpaceSize) + internalOfs;
    }

    public int getInternalOfsFromUOfs(int checkSpaceNum, long uofs) {
        if (uofs == -1)
            return -1;
        long ofsSpace = uofs / usableSpaceSize;
        if (ofsSpace != checkSpaceNum)
            throw new RuntimeException("Invalid space offset given(2) spaceNum=" + checkSpaceNum + " ofs=" + uofs);
        return (int) (uofs % usableSpaceSize);
    }

    public int getSpaceNumFromUOfs(long uofs) {
        if (uofs < 0)
            throw new RuntimeException("Invalid space offset given(3)");
        return (int) (uofs / usableSpaceSize);
    }
/*
    public boolean spaceAvailFor(long encodedOfs,int msgLength) {
        if ((msgLength <= 127) && (msgLength >= 0)) {
            // no additional data
        } else {
            msgLength += maxEncodedLengthFieldSize;
        }
        long pageStart = (encodedOfs/pageSize)*pageSize;
        long pageEnd = pageStart + pageSize;
        long msgEnd = encodedOfs+msgLength;
        return (msgEnd<=pageEnd) && (msgEnd<=spaceSize);
    }
*/


    public String getSpaceFileName(String abaseFileName,int n) {
        return abaseFileName+ "." + n + ".space";
    }

    public String getSpaceFileName(int n) {
        return getSpaceFileName(baseFileName,n);
    }

    public void createDataFilesForSpace(int spNum)  {
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
            spaces[spNum].createBuffer(spaceSize);
    }

    public void setPageHeaderSize(int phs) {
        this.pageHeaderSize = phs;
    }

    public void reset() {

        spaces = new Space[maxSpaces];

        // Add them in reverse order so the first to take is always 0.
        // This loop will create the space files if they don't already exists in disk!
        for (int i = maxSpaces-1; i >=0; i--) {
            spaces[i] = newSpace();
        }
        usablePageSize = pageSize - pageHeaderSize;
        resetInternalConstants();
        for (int i = maxSpaces-1; i >=0; i--) {
            createDataFilesForSpace(i);
        }

    }


    public int getRealOffset(long internalOffset) {
        long usedPage = internalOffset / usablePageSize;
        int offsetWithinPage = (int) (internalOffset - usedPage*usablePageSize);
        long realOffset = usedPage*pageSize+offsetWithinPage+ pageHeaderSize;
        return (int) realOffset;
    }

    void internalSetBytes(long uOfs, byte[] data, int offset, int length) {

        int ptrSpaceNum = getSpaceNumFromUOfs(uOfs);
        Space space = spaces[ptrSpaceNum];
        int internalOfs = getInternalOfsFromUOfs(ptrSpaceNum, uOfs);

    }

    public void saveOrSync() {
        if (!memoryMapped) {
            save();
        } else {
            sync();
        }
    }
    public void save() {

            doForAllSpaces( num -> spaces[num].saveToFile(getSpaceFileName(num)));

    }

    interface SpaceNumMethod {
        public void myMethod(int spaceNum);
    }

    public void doForAllSpaces(SpaceNumMethod method) {
        for(int i=0;i<spaces.length;i++) {
            method.myMethod(i);
        }
    }

    public void load() {
        for (int i = 0; i < spaces.length; i++) {
            String name = getSpaceFileName(i);
            spaces[i].readFromFile(name,memoryMapped);
        }
    }
    public void closeFiles() {
        // We close all mapped files. This object will be unusable afterwards
        // we can close files to simulate a power failure and re-open a database.
        doForAllSpaces( num -> spaces[num].destroy());
    }

    public void sync() {

        // Here we MUST sync all spaces using specific OS commands
        // that guarantee the memory pages are actually written to disk.
        doForAllSpaces(  num -> spaces[num].sync() );
    }

    public Space newSpace() {
        if (memoryMapped)
            return new MemoryMappedSpace();
        else
            return new DirectAccessSpace();
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public void setStoreHeadmap(boolean storeHeadmap)  {
        this.storeHeadmap = storeHeadmap;
    }


    public byte getByte(long uOfs)  {
        Pointer p = new Pointer(uOfs);


        return p.space.getByte(p.realOffset);
    }


    public int getInt(long uOfs) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(4)) {
            return p.space.getInt(p.realOffset);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            return  ((getByte(uOfs + 3) & 0xFF)      ) +
                    ((getByte(uOfs + 2) & 0xFF) <<  8) +
                    ((getByte(uOfs + 1) & 0xFF) << 16) +
                    ((getByte(uOfs+0)         ) << 24);

        }
    }


    public long getLong(long uOfs) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(8)) {
            return p.space.getLong(p.realOffset);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            return ((getByte(uOfs + 7) & 0xFFL)) +
                    ((getByte(uOfs + 6) & 0xFFL) << 8) +
                    ((getByte(uOfs + 5) & 0xFFL) << 16) +
                    ((getByte(uOfs + 4) & 0xFFL) << 24) +
                    ((getByte(uOfs + 3) & 0xFFL) << 32) +
                    ((getByte(uOfs + 2) & 0xFFL) << 40) +
                    ((getByte(uOfs + 1) & 0xFFL) << 48) +
                    (((long) getByte(uOfs)) << 56);


        }
    }


    public void putByte(long uOfs, byte val){
        Pointer p = new Pointer(uOfs);

        p.space.putByte(p.realOffset,val);
    }


    public void putInt(long uOfs, int val) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(4)) {
            p.space.putInt(p.realOffset,val);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            putByte(uOfs + 3, (byte) (val       ));
            putByte(uOfs + 2, (byte) (val >>>  8));
            putByte(uOfs + 1,(byte) (val >>> 16));
            putByte(uOfs    , (byte) (val >>> 24));

        }
    }


    protected class Pointer {
        public int ptrSpaceNum;
        public int internalOfs ;
        public int upageNum ;
        public int upageStart;
        public int realPageStart;
        public int offsetWithinUpage;
        public int offsetWithinRealPage;
        public int realOffset;
        Space space;

        public Pointer(long uOfs) {
            compute(uOfs);
        }

        void compute(long uOfs) {
            ptrSpaceNum = getSpaceNumFromUOfs(uOfs);
            space = spaces[ptrSpaceNum];
            internalOfs = getInternalOfsFromUOfs(ptrSpaceNum, uOfs);
            upageNum = (internalOfs / usablePageSize);
            upageStart = upageNum * usablePageSize;
            realPageStart = (int) upageNum * pageSize;
            offsetWithinUpage = (int) (internalOfs - upageStart * usablePageSize);
            offsetWithinRealPage = offsetWithinUpage + pageHeaderSize;
            realOffset = realPageStart+offsetWithinRealPage;
        }

        public void advance(int amount) {
            int avail =getPageSpaceAvail();

            while (avail<=amount) {
                // Advances one page at a time to the beginning og a page:
                // it could be more optimized,
                // but it's not needed because we don't expect advances of
                // longer lengths.
                ptrSpaceNum++;
                space = spaces[ptrSpaceNum];
                internalOfs += avail;
                upageNum += 1;
                upageStart += usablePageSize;
                realPageStart +=pageSize;
                offsetWithinUpage = 0;
                offsetWithinRealPage = pageHeaderSize;
                realOffset = realPageStart+offsetWithinRealPage;
                amount -=avail;
                avail =usablePageSize;
            }
            if (avail>amount) {
                offsetWithinUpage +=amount;
                offsetWithinRealPage +=amount;
                realOffset +=amount;
                return;
            }
        }

        public int getPageSpaceAvail() {
            return usablePageSize-offsetWithinUpage;
        }

        public boolean canFitInPage(int byteCount) {
            return (offsetWithinUpage+byteCount<=usablePageSize);
        }

    }

    public void putLong(long uOfs, long val) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(8)) {
            p.space.putLong(p.realOffset,val);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            putByte(uOfs + 7,(byte) (val       ));
            putByte(uOfs + 6, (byte) (val >>>  8));
            putByte(uOfs + 5, (byte) (val >>> 16));
            putByte(uOfs + 4, (byte) (val >>> 24));
            putByte(uOfs + 3, (byte) (val >>> 32));
            putByte(uOfs + 2, (byte) (val >>> 40));
            putByte(uOfs + 1, (byte) (val >>> 48));
            putByte(uOfs    , (byte) (val >>> 56));

        }
    }


    public void getBytes(long uOfs, byte[] data, int offset, int length) {
        Pointer p = new Pointer(uOfs);
        while (length>0) {
            int left = p.getPageSpaceAvail();
            p.space.getBytes(p.realOffset,data,offset,left);
            offset +=left;
            length -=left;
            p.advance(left);
        }
    }

    public void setBytes(long uOfs, byte[] data, int offset, int length) {
        Pointer p = new Pointer(uOfs);
        while (length>0) {
            int left = p.getPageSpaceAvail();
            p.space.setBytes(p.realOffset,data,offset,left);
            offset +=left;
            length -=left;
            p.advance(left);
        }
    }

    public long getLong5(int uOfs) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(8)) {
            return p.space.getLong(p.realOffset);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            return ((getByte(uOfs + 4) & 0xFFL)) +
                    ((getByte(uOfs + 3) & 0xFFL) << 8) +
                    ((getByte(uOfs + 2) & 0xFFL) << 16) +
                    ((getByte(uOfs + 1) & 0xFFL) << 24) +
                    ((getByte(uOfs + 0) & 0xFFL) << 32);
        }
    }

    void putLong5(int uOfs, long val) {
        Pointer p = new Pointer(uOfs);

        if (p.canFitInPage(8)) {
            p.space.putLong(p.realOffset,val);
        } else {
            // The value is half in one page, half in another, so we must reconstruct it... ugly.
            putByte(uOfs + 4,(byte) (val       ));
            putByte(uOfs + 3, (byte) (val >>>  8));
            putByte(uOfs + 2, (byte) (val >>> 16));
            putByte(uOfs + 1, (byte) (val >>> 24));
            putByte(uOfs + 0, (byte) (val >>> 32));
        }
    }

     public void copyBytes(int srcPos, Space dest, int destPos, int length) {
        throw new RuntimeException("unsupported");
     }

    public ByteBuffer getByteBuffer(int offset, int length){
        throw new RuntimeException("unsupported");
    }

    public class HeadBitPointer {
        int upageStart;
        int realPageStart;
        int byteIndexInUpage;
        int compressionBytes;
        int headerOffset;
        int byteIndex ;
        int bitIndex ;
        int internalOfs;
        int compressedByteIndexInPage;

        public HeadBitPointer(int headerOffset,int compressionBytes,int internalOfs) {
            compute(headerOffset,compressionBytes,internalOfs);
        }

        public void compute(int headerOffset,int compressionBytes,int internalOfs) {
            this.compressionBytes = compressionBytes;
            this.headerOffset = headerOffset;
            this.internalOfs = internalOfs;
            upageStart = (internalOfs/usablePageSize)*usablePageSize;
            realPageStart = (int) (internalOfs/usablePageSize)*pageSize;
            byteIndexInUpage = (int) (internalOfs-upageStart);
            compressedByteIndexInPage = byteIndexInUpage / compressionBytes;
            byteIndex = realPageStart+headerOffset+ compressedByteIndexInPage / 8;
            bitIndex = (compressedByteIndexInPage % 8);
        }
    }

    public void setHeadBit(Space space,int headerOffset,int compressionFactor,int internalOfs) {
        HeadBitPointer p = new HeadBitPointer(headerOffset,compressionFactor,internalOfs);
        space.putByte(p.byteIndex,(byte) (space.getByte(p.byteIndex) | (1<<p.bitIndex)));
    }

    public boolean getHeadBit(Space space,int headerOffset,int compressionFactor,int internalOfs) {
        HeadBitPointer p = new HeadBitPointer(headerOffset,compressionFactor,internalOfs);
        return ((space.getByte(p.byteIndex) & (1<<p.bitIndex))!=0);
    }

    public boolean getHeadByte(Space space,int headerOffset,int compressionFactor,int internalOfs) {
        HeadBitPointer p = new HeadBitPointer(headerOffset,compressionFactor,internalOfs);
        return (space.getByte(p.byteIndex) !=0);
    }
    public boolean getHeadBit(int headerOffset,int compressionFactor,long uOfs) {
        int ptrSpaceNum = getSpaceNumFromUOfs(uOfs);
        Space space = spaces[ptrSpaceNum];
        int internalOfs = getInternalOfsFromUOfs(ptrSpaceNum, uOfs);
        return getHeadBit(space,headerOffset,compressionFactor,internalOfs);
    }
    public boolean getHeadByte(int headerOffset,int compressionBytes,long uOfs) {
        int ptrSpaceNum = getSpaceNumFromUOfs(uOfs);
        Space space = spaces[ptrSpaceNum];
        int internalOfs = getInternalOfsFromUOfs(ptrSpaceNum, uOfs);
        return getHeadByte(space,headerOffset,compressionBytes,internalOfs);
    }
    public void setHeadBit(int headerOffset,int compressionBytes,long uOfs) {
        int ptrSpaceNum = getSpaceNumFromUOfs(uOfs);
        Space space = spaces[ptrSpaceNum];
        int internalOfs = getInternalOfsFromUOfs(ptrSpaceNum, uOfs);
        setHeadBit(space,headerOffset,compressionBytes,internalOfs);
    }
}
