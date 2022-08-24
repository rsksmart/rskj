package co.rsk.bahashmaps;

import co.rsk.datasources.flatdb.LogManager;
import co.rsk.packedtables.Table;
import co.rsk.packedtables.UInt40Table;
import co.rsk.baheaps.AbstractByteArrayHeap;

import java.util.EnumSet;

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
        return UInt40Table.getElementSize();
    }

    protected Table createTable(int cap)
    {
        UInt40Table table;
        table = new UInt40Table(cap,format.pageSize);
        return table;
    }

}
