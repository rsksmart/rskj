package co.rsk.datasources;


import co.rsk.bahashmaps.AbstractByteArrayHashMap;
import co.rsk.bahashmaps.Format;
import co.rsk.baheaps.AbstractByteArrayHeap;
import co.rsk.baheaps.ByteArrayHeap;
import co.rsk.baheaps.ByteArrayHeapRefProxy;
import co.rsk.baheaps.ByteArrayRefHeap;
import co.rsk.bahashmaps.ByteArrayRefHashMap;

import org.ethereum.db.ByteArrayWrapper;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


public class DataSourceWithRefHeap extends DataSourceWithHeap {

    Format format;

    public DataSourceWithRefHeap(int maxNodeCount, long beHeapCapacity,
                                 String databaseName, LockType lockType,
                                 Format format, boolean additionalKV,boolean readOnly) throws IOException {
        super(maxNodeCount,  beHeapCapacity,
        databaseName,lockType,format, additionalKV,readOnly);
        this.format = format;

    }


    public String getModifiers() {
        return "";
    }

    public String getName() {
            return "DataSourceWithRefHeap-"+databaseName;
    }


    AbstractByteArrayHeap createByteArrayHeap(float loadFactor, long maxNodeCount, long maxCapacity) throws IOException {
        ByteArrayRefHeap baHeap = new ByteArrayRefHeap();
        baHeap.setMaxMemory(maxCapacity); //730_000_000L); // 500 Mb / 1 GB

        //int expectedReferences = (int) (maxNodeCount*loadFactor+1);

        int expectedReferences =(int) maxNodeCount;
        baHeap.setMaxReferences(expectedReferences);

        Files.createDirectories(Paths.get(databaseName));

        baHeap.setFileName(dbPath.toString());
        baHeap.setFileMapping(true);
        baHeap.initialize();
        if (baHeap.fileExists())
            baHeap.load(); // We throw away the root...

        AbstractByteArrayHeap bah = new ByteArrayHeapRefProxy(baHeap);
        return bah;
    }

    protected Map<ByteArrayWrapper, byte[]> makeCommittedCache(int maxNodeCount, long beHeapCapacity) throws IOException {
        if (maxNodeCount==0) return null;

        Map<ByteArrayWrapper, byte[]> cache;

        MyBAKeyValueRelation myKR = new MyBAKeyValueRelation();

        float loadFActor =getDefaultLoadFactor();
        int initialSize = (int) (maxNodeCount/loadFActor);

        // Since we are not compressing handles, we must prepare for wost case

        sharedBaHeap =
                createByteArrayHeap(loadFActor,maxNodeCount,beHeapCapacity);


        this.bamap =  new ByteArrayRefHashMap(initialSize,loadFActor,myKR,
                (long) beHeapCapacity,
                sharedBaHeap,0,
                format);

        this.bamap.setPath(mapPath);
        if (bamap.dataFileExists()) {
            bamap.load();
        }
        return bamap;
    }


}
