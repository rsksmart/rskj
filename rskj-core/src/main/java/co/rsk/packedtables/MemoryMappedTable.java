package co.rsk.packedtables;

import co.rsk.dbutils.FileMapUtil;
import co.rsk.dbutils.ObjectIO;
import co.rsk.spaces.MemoryMappedSpace;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class MemoryMappedTable implements Table {
     MemoryMappedSpace space;
     boolean wasModified;
     int size;
     int slotSize;

     public void preLoad() {
         // We will try to load the space all in memory by peeking
         // into one byte every 512.
         int sizeInBytes = size*5;
         int p =0;
         long fstart = System.currentTimeMillis();
         while (p<sizeInBytes) {
             space.getByte(p);
             p +=512;
         }
         long fstop = System.currentTimeMillis();
         System.out.println("Time to preload: "+(fstop-fstart)+" ms");
     }

    public MemoryMappedTable(int cap, int pageSize, String fileName,int slotSize) throws IOException {
        space = new MemoryMappedSpace();
        this.size = cap;
        this.slotSize = slotSize;
        space.createMemoryMapped(cap*5,fileName);
        preLoad();
    }

    @Override
    public long getPos(int i) {
         if (slotSize==5)
            return space.getLong5(i*5);
         if (slotSize==8)
             return  space.getLong(i*8);
         return 0;
    }

    @Override
    public void setPos(int i, long value) {
        int ofs =  i*slotSize;
        if (slotSize==5)
            space.putLong5(ofs,value);
        else
            if (slotSize==8)
                space.putLong(ofs,value);
        wasModified=true;
    }

    @Override
    public boolean isNull() {
        return space==null;
    }

    @Override
    public int length() {
        if (isNull())
            return 0;
        else
            return size;
    }


    public boolean modified() {
        return wasModified;
    }

    @Override
    public void update(FileChannel file, int ofs) throws IOException {
    }

    @Override
    public void copyTo(FileChannel file, int ofs) throws IOException {
    }

    @Override
    public
    void fillWithZero() {
        // TO-DO: when is this needed ?
        //space.fillWithZero(); // Arrays.fill(table, (byte) 0);


    }

    @Override
    public int getSlotByteSize() {
        return 5;
    }

    @Override
    public
    void clearPageTracking() {
       wasModified = false;
    }

    public
    static
    int  getElementSize() { // in bytes
        return 5;
    }

    @Override
    public
    void fill(long value) {
        if (value==0) {
            fillWithZero();
            return;
        }
        for(int i=0;i<size;i++){
            setPos(i,value);
        }
    }

    @Override
    public
    void readFrom(DataInputStream din, int count) throws IOException  {
        for (int i = 0; i < count; i++) {
            setPos(i, ObjectIO.readLong5(din));
        }
        clearPageTracking();
    }

    @Override
    public void sync() {
         space.sync();
    }
}
