package co.rsk.packedtables;

import co.rsk.dbutils.FileMapUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class BigTable implements Table {

    Table table[];
    final int length;
    boolean isModified;
    int pageSize ;
    final int slotSize;

    Table newTable(int pageSize,int intraPageSize,boolean align,int bytes) {
        if (bytes==4) {
            return new Int32Table(pageSize);
        } else if (bytes==5) {
            return new UInt40Table(pageSize,intraPageSize,align);
        } else if (bytes==8) {
            return  new LongTable(pageSize);
        } else {
            throw new RuntimeException("Invalid slot size");
        }
    }
    Table[] newTableArray(int pages,int bytes) {
        if (bytes==4) {
            return new Int32Table[pages];
        } else if (bytes==5) {
            return new UInt40Table[pages];
        } else if (bytes==8) {
            return  new LongTable[pages];
        } else {
            throw new RuntimeException("Invalid slot size");
        }
    }

    // cap is the number of elements.
    public BigTable(int cap,int intraPageSize,boolean align, int slotSize,int fixNumPages) {
        length = cap;
        this.slotSize = slotSize;
        // pageSize is the number of entries (not the number of bytes)
        if (fixNumPages != 0) {
            pageSize = (cap + fixNumPages - 1) / fixNumPages;
            if (pageSize > Integer.MAX_VALUE / slotSize) {
                throw new RuntimeException("Invalid number of pages");
            }
        } else {
            pageSize = Integer.MAX_VALUE / slotSize;
        }

        if (cap < pageSize) {
            pageSize = cap;
        }


        int pages = (int) ((1L * cap + pageSize - 1) / pageSize);
        int i = 0;
        table = newTableArray(pages, slotSize);
        while (i < pages) {
            int tableSize = Math.min(cap, pageSize);
            table[i] = newTable(tableSize, intraPageSize, align, slotSize);
            cap -= tableSize;
            i++;
        }
    }

    @Override
    public long getPos(int i) {
        int page = i/pageSize;
        return table[page].getPos(i % pageSize);

    }


    @Override
    public void setPos(int i, long value) {
        int page = i/pageSize;
        table[page].setPos(i % pageSize,value);
        isModified = true;
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
            return length;
    }


    @Override
    public void copyTo(FileChannel file, long ofs) throws IOException {
        // Child to -do
        for(int i=0;i<table.length;i++) {
            System.out.println("writting table "+i+" at offset"+ofs);
            table[i].copyTo(file,ofs);
            ofs += 1L*pageSize*slotSize;
        }
        isModified =false;
    }

    @Override
    public void update(FileChannel file, long ofs) throws IOException {
        for(int i=0;i<table.length;i++) {
            table[i].update(file,ofs);
            ofs += pageSize*slotSize;
        }
        isModified =false;
    }

    @Override
    public
    void fillWithZero() {
        for(int i=0;i<table.length;i++) {
            table[i].fillWithZero();
        }
    }

    @Override
    public int getSlotByteSize() {
        return slotSize;
    }

    @Override
    public boolean modified() {
        return isModified;
    }

    public
    static
    int  getElementSize() { // in bytes
        throw new RuntimeException("variable");
    }

    @Override
    public
    void fill(long value) {
        for(int i=0;i<table.length;i++) {
            table[i].fill(value);
        }
    }

    @Override
    public
    void readFrom(DataInputStream din, int count) throws IOException {
        for(int i=0;i<table.length;i++) {
            int read = Math.min(pageSize,count);
            table[i].readFrom(din,read);
            count -=pageSize;
        }

        isModified =false;
    }

    @Override
    public void clearPageTracking() {
        isModified =false;
    }
}
