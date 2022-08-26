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
        super(initialCapacity,  loadFactor,
                BAKeyValueRelation,
                newBeHeapCapacity,
                sharedBaHeap,
                maxElements,format,logManager);
    }

    protected int getElementSize() {

        //return UInt40Table.getElementSize();
        return 5;
    }

    protected Table createTable(int cap) throws IOException {
        if (((long) cap * 5 > Integer.MAX_VALUE)) {
            throw new RuntimeException("Hashtable does not fit in Java array");
        }
        Table table;
        if (!memoryMappedIndex) {
            table = new UInt40Table(cap, format.pageSize);
        } else {
            String fileName =  mapPath.toAbsolutePath().toString();

            table = new MemoryMappedTable(cap,0,fileName,5);

        }
        return table;
    }

}
