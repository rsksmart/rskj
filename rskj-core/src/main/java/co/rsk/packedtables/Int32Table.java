package co.rsk.packedtables;

import co.rsk.dbutils.FileMapUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class Int32Table implements Table {

    int table[];

    public Int32Table(int cap) {
        table = new int[cap];
    }

    @Override
    public long getPos(int i) {
        return table[i];

    }

    @Override
    public void setPos(int i, long value) {
        table[i] = (int) value;
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
            return table.length;
    }


    @Override
    public void copyTo(FileChannel file, int ofs) throws IOException {
        // Child to -do
        FileMapUtil.mapAndCopyIntArray(file,ofs,table.length,table);
    }

    @Override
    public void update(FileChannel file, int ofs) throws IOException {
        // update not implemented
        FileMapUtil.mapAndCopyIntArray(file,ofs,table.length,table);
    }

    @Override
    public
    void fillWithZero() {
        Arrays.fill(table, 0);
    }

    public
    static
    int  getElementSize() { // in bytes
        return 4;
    }

    @Override
    public
    void fill(long value) {
        Arrays.fill( table, (int)value );
    }

    @Override
    public
    void readFrom(DataInputStream din, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            // TO DO: compress here
            setPos(i, din.readInt());
        }
    }

    @Override
    public void clearPageTracking() {

    }
}
