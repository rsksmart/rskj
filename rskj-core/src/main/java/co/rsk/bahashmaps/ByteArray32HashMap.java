package co.rsk.bahashmaps;

import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.datasources.flatdb.LogManager;
import co.rsk.packedtables.Int32Table;
import co.rsk.packedtables.Table;

import java.util.EnumSet;

public class ByteArray32HashMap extends AbstractByteArrayHashMap {

    public ByteArray32HashMap(int initialCapacity, float loadFactor,
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
        return Int32Table.getElementSize();
    }

    protected Table createTable(int cap)
    {
        Int32Table table;
        table = new Int32Table(cap);
        return table;
    }
}
