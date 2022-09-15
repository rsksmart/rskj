package co.rsk.bahashmaps;

import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.datasources.flatdb.LogManager;
import co.rsk.packedtables.*;

import java.io.IOException;

public class ByteArrayVarHashMap extends AbstractByteArrayHashMap {

    int elementSize ;
    public ByteArrayVarHashMap(int initialCapacity, float loadFactor,
                              co.rsk.bahashmaps.BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              Format format, LogManager logManager) {
        super(initialCapacity, loadFactor,
                BAKeyValueRelation,
                newBeHeapCapacity,
                sharedBaHeap,
                maxElements, format, logManager);
    }

    protected int getElementSize() {


        return elementSize;
    }


    static public long getTableSize(int cap,int pageSize, int slotSize, boolean align) {
        if (align) {
            int slotsPerPage = pageSize/slotSize;
            int pages = (cap+slotsPerPage-1)/slotsPerPage;
            long size = pages*pageSize;
            return size;
        } else
            return ((long)cap)*slotSize;
    }

    public static int log2(int N) {
        int result = (int) Math.ceil(Math.log(N) / Math.log(2));
        return result;
    }
    protected Table createTable(int cap,int predefinedSlotSize) throws IOException {
        boolean align =(format.creationFlags.contains(CreationFlag.AlignSlotInPages));
        if ((align) && (format.pageSize<=5))
            throw new RuntimeException("pageSize must be defined");
        
        int bytes;
        if (predefinedSlotSize==0) {
            int bits = log2(cap) + getReservedBitCount();
            bytes = (bits + 7) / 8;
            if (bytes <= 4)
                bytes = 4;  // Minimum entry size is 32 bits
            if (bytes > 5)
                bytes = 8;  // Maximum entry size is 64 bits
        }
         else
             bytes = predefinedSlotSize;
        elementSize = bytes;

        if (memoryMappedIndex) {
            // Only supports a single page that must fit in a single mapped file
            long size = getTableSize(cap,format.pageSize,bytes,align);
            if ((size > Integer.MAX_VALUE)) {
                throw new RuntimeException("Hashtable does not fit in Java array");
            }
            String fileName =  mapPath.toAbsolutePath().toString();

            table = new MemoryMappedTable(cap,0,fileName,bytes);
            return table;
        }

        Table table;
        if (bytes==4) {
            table = new Int32Table(cap);
        } else if (bytes==5) {
            table = new UInt40Table(cap, format.pageSize,align);
        } else if (bytes==8) {
            table = new LongTable(cap);
        } else {
            throw new RuntimeException("Invalid slot size");
        }
        return table;
    }

}
