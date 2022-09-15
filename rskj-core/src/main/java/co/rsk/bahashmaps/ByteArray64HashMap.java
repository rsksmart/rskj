package co.rsk.bahashmaps;

import co.rsk.datasources.flatdb.LogManager;
import co.rsk.packedtables.LongTable;
import co.rsk.packedtables.Table;
import co.rsk.baheaps.AbstractByteArrayHeap;

import java.util.EnumSet;

public class ByteArray64HashMap extends AbstractByteArrayHashMap {

    public ByteArray64HashMap(int initialCapacity, float loadFactor,
                              BAKeyValueRelation BAKeyValueRelation,
                              long newBeHeapCapacity,
                              AbstractByteArrayHeap sharedBaHeap,
                              int maxElements,
                              Format format, LogManager logManager) {
        super(initialCapacity,  loadFactor,
        BAKeyValueRelation,
        newBeHeapCapacity,
         sharedBaHeap,
                maxElements,
                format,logManager);
    }

    protected int getElementSize() {
        return LongTable.getElementSize();
    }

    protected Table createTable(int cap,int predefinedSlotSize)
    {
        if ((predefinedSlotSize!=0) && (predefinedSlotSize!=8))
            throw new RuntimeException("Invalid predefined slot size");

        LongTable table;
        table = new LongTable(cap);
        return table;
    }
}
