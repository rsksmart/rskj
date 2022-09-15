package co.rsk.bahashmaps;

import co.rsk.datasources.flatdb.LogManager;
import co.rsk.packedtables.Table;
import co.rsk.packedtables.MemoryMappedTable;
import co.rsk.packedtables.UInt40Table;
import co.rsk.baheaps.AbstractByteArrayHeap;

import java.io.IOException;

public class ByteArray40HashMap extends AbstractByteArrayHashMap {


    public ByteArray40HashMap(int initialCapacity, float loadFactor,
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

        //return UInt40Table.getElementSize();
        return 5;
    }


    protected Table createTable(int cap,int predefinedSlotSize) throws IOException {
        if ((predefinedSlotSize!=0) && (predefinedSlotSize!=5))
            throw new RuntimeException("Invalid predefined slot size");

        boolean align =(format.creationFlags.contains(CreationFlag.AlignSlotInPages));
        if ((align) && (format.pageSize<=5))
            throw new RuntimeException("pageSize must be defined");

        long size = UInt40Table.getTableSize(cap,format.pageSize,align);


        Table table;
        if (!memoryMappedIndex) {
            table = new UInt40Table(cap, format.pageSize,align);
        } else {
            if ((size > Integer.MAX_VALUE)) {
                throw new RuntimeException("Hashtable does not fit in Java array");
            }
            String fileName =  mapPath.toAbsolutePath().toString();

            table = new MemoryMappedTable(cap,0,fileName,5);

        }
        return table;
    }

}
