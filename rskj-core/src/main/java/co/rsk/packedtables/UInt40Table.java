package co.rsk.packedtables;

import co.rsk.dbutils.FileMapUtil;
import co.rsk.dbutils.ObjectIO;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.BitSet;

public class UInt40Table implements Table {

    public BitSet modifiedPages = new BitSet();
    public int modifiedPageCount;
    int pageSize;
    boolean align;
    byte table[];
    int slotsPerPage;
    int pages;
    int cap;

    public UInt40Table(int cap,int pageSize,boolean align) {
        int size = (int) getTableSize(cap,pageSize, align);
        table = new byte[size];
        this.align = align;
        this.pageSize = pageSize;
        this.cap = cap;
        if (align) {
            slotsPerPage = pageSize/5;
            pages = (cap+slotsPerPage-1)/slotsPerPage;
        }
    }

    static public long getTableSize(int cap,int pageSize, boolean align) {
        if (align) {
            int slotsPerPage = pageSize/5;
            int pages = (cap+slotsPerPage-1)/slotsPerPage;
            long size = pages*pageSize;
            return size;
        } else
            return ((long)cap)*5;
    }

    @Override
    public long getPos(int i) {
        int ofs;
        if (!align)
            ofs =i*5;
        else
            ofs = pageSize*(i/slotsPerPage)+(i%slotsPerPage);
          return ObjectIO.getLong5(table,ofs);
    }

    @Override
    public void setPos(int i, long value) {
        int ofs;
        if (!align)
            ofs =i*5;
        else
            ofs = pageSize*(i/slotsPerPage)+(i%slotsPerPage);
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
            return cap;
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
        // This seems to be faster
        ByteBuffer bb =ByteBuffer.wrap(table);
        file.write(bb);

        //FileMapUtil.mapAndCopyByteArray(file,ofs,table.length,table);
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
        for(int i=0;i<cap;i++){
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
