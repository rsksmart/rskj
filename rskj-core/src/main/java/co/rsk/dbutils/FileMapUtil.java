package co.rsk.dbutils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;

public class FileMapUtil {

    static boolean showProgressOnConsole = false;
    static public void mapAndCopyIntArray(FileChannel file, long offset, long size, int[] table) throws IOException {
        int count=0;
        int intCount=0;
        int intLeft = table.length;
        while (intLeft>0) {
            int len = Math.min(intLeft,(1<<24)); // 16M ints = 64 Mbytes Max
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset,
                    4L*len );
            for (int i=intCount;i<(intCount+len);i++) {
                buf.putInt(table[i]);
                count++;
                if (showProgressOnConsole)
                    if (count % 1_000_000 == 0)
                        System.out.println("" + (count * 100L / table.length) + "%");
            }
            offset +=4L*len;
            intLeft -=len;
            intCount +=len;
        }
    }

    static public void mapAndCopyLongArray(FileChannel file, long offset, long size, long[] table) throws IOException {
        int count=0;
        int longCount=0;
        int longLeft = table.length;
        while (longLeft>0) {
            int len = Math.min(longLeft,(1<<23)); // 8M longs = 64 Mbytes Max
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset,
                    8L*len );
            for (int i=longCount;i<(longCount+len);i++) {
                buf.putLong(table[i]);
                count++;
                if (showProgressOnConsole)
                  if (count % 1_000_000 == 0)
                    System.out.println("" + (count * 100L / table.length) + "%");
            }
            offset +=8L*len;
            longLeft -=len;
            longCount +=len;
        }
    }

    // size is the size of the table.
    // the size of the file would be "size+offset"
    static public void mapAndCopyByteArray(FileChannel file, long offset, long size, byte[] table) throws IOException {
        int count=0;
        int longCount=0;
        int longLeft = table.length;
        while (longLeft>0) {
            int len = Math.min(longLeft,(1<<23)); // 8M longs = 64 Mbytes Max
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, offset,
                    len );
            for (int i=longCount;i<(longCount+len);i++) {
                buf.put(table[i]);
                count++;
                if (showProgressOnConsole)
                  if (count % 1_000_000 == 0)
                    System.out.println("" + (count * 100L / table.length) + "%");
            }
            offset +=len;
            longLeft -=len;
            longCount +=len;
        }
    }
    static public void mapAndCopyByteArrayPages(FileChannel file, long offset, long size, byte[] table,
                                                BitSet modifiedPages,int pageSize,int pageCount) throws IOException {
        // For optimum performance offset should be a multiple of pageSize
        int count=0;
        if (offset+((long) pageSize)*pageCount > Integer.MAX_VALUE)
            throw new RuntimeException("Too big to map");

        ByteBuffer buf;

        // Maximum map size is 2GB, same as a byte array.
        long len = offset+size; // avoid increasing file size
        buf = file.map(FileChannel.MapMode.READ_WRITE, 0,  len );
        int logBase =0;
        int pagesWritten =0;

        for(int i=0;i<pageCount;i++) {
            if (modifiedPages.get(i)) {
                pagesWritten++;
                int pageOfs = pageSize*i;
                int mapOfs = i*pageSize+(int) offset;
                buf.position(mapOfs);
                ByteBuffer part = ByteBuffer.wrap(table,pageOfs,pageSize);
                buf.put(part);
                //for (int b=0;b<pageSize;b++) {
                //    buf.put(table[b+pageOfs]);
                //}
                count+=pageSize;
                if (showProgressOnConsole)
                  if (count > logBase ) {
                    logBase = logBase+1_000_000;
                    System.out.println("" + (count * 100L / size) + "%");
                  }
            }

        }
        System.out.println("Pages written: "+pagesWritten);
    }
}
