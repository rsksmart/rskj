package co.rsk.spaces;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class MemoryMappedSpace extends Space {


    MappedByteBuffer out;
    RandomAccessFile file;
    int size;
    boolean bigEndian;

    public void createMemoryMapped(int size,String fileName) throws IOException {
        file = new RandomAccessFile(fileName, "rw");
        this.out = file.getChannel()
                .map(FileChannel.MapMode.READ_WRITE, 0, size);
        this.size = size;
        bigEndian = (out.order()== ByteOrder.BIG_ENDIAN);

    }
    public void sync() {
            if (out==null) {
                return;
            }
            // file.getFD().sync();
            // file.getChannel().force();
            // out.force() this is the recommended way according to https://stackoverflow.com/questions/14011398/do-i-need-filedescriptor-sync-and-mappedbytebuffer-force
            out.force();
    }
    @Override
    public byte getByte(long pos) {
        return out.get( (int) pos);
    }

    @Override
    public int getInt(long pos) {
        return out.getInt((int) pos);
    }

    @Override
    public long getLong(long pos) {
        return out.getLong((int) pos);
    }

    @Override
    public void putByte(long pos, byte val) {
        out.put((int) pos,val);
    }

    @Override
    public void putInt(long pos, int val) {
        out.putInt((int) pos,val);
    }

    @Override
    public void putLong(long pos, long val) {
        out.putLong((int) pos,val);
    }

    @Override
    public void getBytes(long pos, byte[] data, int offset, int length) {
        out.position((int)pos);
        out.get(data,offset,length);
    }

    @Override
    public void setBytes(long pos, byte[] data, int offset, int length) {
        out.position((int)pos);
        out.put(data,offset,length);
    }

    public static long getLong5be(ByteBuffer buf, int off) {
        long r;
        if (off>=3)
            r = buf.getLong(off-3 ) & 0xffffffffffL;
        else {
            r = ((buf.get(off + 4) & 0xFFL)) +
                    ((buf.get(off + 3) & 0xFFL) << 8) +
                    ((buf.get(off + 2) & 0xFFL) << 16) +
                    ((buf.get(off + 1) & 0xFFL) << 24) +
                    ((buf.get(off + 0) & 0xFFL) << 32); // Big endian
        }
        if (r>=1L<<39) {
            //r2 =r2;
            // negative
            r = r - (1L<<40);
        }
        return r;
    }

    public static long getLong5le(ByteBuffer buf, int off) {
        long r;
        r= ((buf.get(off + 0) & 0xFFL)      ) +
                ((buf.get(off + 1) & 0xFFL) <<  8) +
                ((buf.get(off + 2) & 0xFFL) << 16) +
                ((buf.get(off + 3) & 0xFFL) << 24) +
                ((buf.get(off + 4) & 0xFFL) << 32); // Little endian

        if (r>=1L<<39) {
            // negative
            r = r - (1L<<40);
        }
        return r;
    }

    @Override
    public long getLong5(int off) {
        if (bigEndian)
            return getLong5be(out,off);
        else
            return getLong5le(out,off);
    }

    public static void putLong5le(ByteBuffer buf, int off, long val) {
        if ((val>=(1L<<39)) || (val<-(1L<<39)))
            throw new RuntimeException("Cannot encode "+val);
        buf.put(off + 0, (byte) (val       ));
        buf.put(off + 1, (byte) (val >>>  8));
        buf.put(off + 2, (byte) (val >>> 16));
        buf.put(off + 3, (byte) (val >>> 24));
        buf.put(off + 4, (byte) (val >>> 32));
    }

    public static void putLong5be(ByteBuffer buf, int off, long val) {
        if ((val>=(1L<<39)) || (val<-(1L<<39)))
            throw new RuntimeException("Cannot encode "+val);
        buf.put(off + 4, (byte) (val       ));
        buf.put(off + 3, (byte) (val >>>  8));
        buf.put(off + 2, (byte) (val >>> 16));
        buf.put(off + 1, (byte) (val >>> 24));
        buf.put(off + 0, (byte) (val >>> 32));
    }
    @Override
    public void putLong5(int off, long val) {
        if (bigEndian)
         putLong5be(out,off,val);
        else
            putLong5le(out,off,val);
    }

    @Override
    public void copyBytes(int srcPos, Space dest, int destPos, int length) {
        out.position(srcPos);
        byte[] dst = new byte[length];
        out.get(dst,0,length);
        dest.setBytes(destPos,dst,0,length);
    }

    @Override
    public ByteBuffer getByteBuffer(int offset, int length) {
        //return ByteBuffer.wrap(out.array(),offset,length);
        // duplicate shares the buffer content
        return out.duplicate().position(offset).limit(length);
    }

    @Override
    public int spaceSize() {
        return size;// I'm not sure if limit() changes on reads, out.limit();
    }

    public void readFromFile(String fileName, boolean map) {
        if (map) {
            try {
                createMemoryMapped((int) Files.size(Path.of(fileName)), fileName);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
            return;
        }

    }

    @Override
    public void saveToFile(String fileName) {

    }
    @Override
    public void destroy() {
     super.destroy();
     out = null; // hope this is garbage collected and the memory is unmapped
    }

}
