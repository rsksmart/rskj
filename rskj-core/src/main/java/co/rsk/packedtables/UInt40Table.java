package co.rsk.packedtables;

import co.rsk.dbutils.FileMapUtil;
import co.rsk.dbutils.ObjectIO;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.BitSet;

public class UInt40Table implements Table {

    public BitSet modifiedPages = new BitSet();
    public int modifiedPageCount;
    int pageSize;

    byte table[];


    public UInt40Table(int cap,int pageSize) {
        table = new byte[cap*5];
        this.pageSize = pageSize;
    }

    @Override
    public long getPos(int i) {
        return ObjectIO.getLong5(table,i*5);
    }

    @Override
    public void setPos(int i, long value) {
        int ofs =  i*5;
        ObjectIO.putLong5(table,ofs,value);

        if (pageSize!=0) {
            int page = ofs /pageSize;
            if (!modifiedPages.get(page)) {
                modifiedPages.set(page);
                modifiedPageCount++;
            }
        }
    }

    @Override
    public boolean isNull() {
        return table==null;
    }

    @Override
    public int length() {
        if (isNull())
            return 0;
        else
            return table.length/5;
    }


    public boolean modified() {
        return (modifiedPageCount>0);
    }
    @Override
    public void update(FileChannel file, int ofs) throws IOException {
        System.out.println("pageSize: "+pageSize);
        if (pageSize==0) {
            copyTo(file, ofs);
            return;
        }

        float updateRatio = 0.8f;
        int pageCount = (table.length+pageSize-1)/pageSize; // round up

        System.out.println("ModifiedPages: "+modifiedPageCount+" of "+pageCount);
        // more than 80% must be re-written ?
        if (modifiedPageCount > updateRatio*pageCount) {
            copyTo(file, ofs);
            return;
        }
        FileMapUtil.mapAndCopyByteArrayPages(file,ofs,table.length,table,modifiedPages,pageSize,pageCount);
        modifiedPageCount = 0;
        modifiedPages.clear();
    }

    @Override
    public void copyTo(FileChannel file, int ofs) throws IOException {
        // Child to -do
        System.out.println("Writing full file");
        FileMapUtil.mapAndCopyByteArray(file,ofs,table.length,table);
        modifiedPageCount = 0;
        modifiedPages.clear();
    }

    @Override
    public
    void fillWithZero() {
        Arrays.fill(table, (byte) 0);


    }

    @Override
    public int getSlotByteSize() {
        return 5;
    }

    @Override
    public
    void clearPageTracking() {
        if (pageSize!=0) {
            modifiedPages.clear();
            modifiedPageCount =0;
        }
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
        for(int i=0;i<table.length/5;i++){
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
}
