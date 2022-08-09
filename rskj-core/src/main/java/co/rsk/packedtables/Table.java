package co.rsk.packedtables;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.BitSet;

public interface Table {
    long getPos(int i) ;
    void setPos(int i,long value);
    boolean isNull();
    int length();
    void copyTo(FileChannel file, int ofs) throws IOException;
    void update(FileChannel file, int ofs) throws IOException;
    void readFrom(DataInputStream din, int count) throws IOException;
    void clearPageTracking();
    void fill(long value);
    void fillWithZero();

    boolean modified();
}
