package co.rsk.spaces;

import co.rsk.dbutils.ObjectIO;

import java.io.*;
import java.nio.ByteBuffer;

public class DirectAccessSpace extends Space {
    public byte[] mem;


    public boolean created() {
        return mem!=null;
    }

    public byte getByte(long pos) {
        return mem[(int)pos];
    }

    public int getInt(long pos) {
        return ObjectIO.getInt(mem,(int)pos);
    }

    public long getLong(long pos) {
         return ObjectIO.getLong(mem,(int)pos);

    }

    public void putByte(long pos, byte val) {
        mem[(int) pos] =val;
    }

    public ByteBuffer getByteBuffer(int offset, int length) {
        return ByteBuffer.wrap(mem,offset,length);
    }

    public void copyBytes(int srcPos, Space dest, int destPos, int length) {
        System.arraycopy(mem, srcPos, ((DirectAccessSpace)dest).mem, destPos, length);
    }

    public void getBytes(long pos, byte[] data, int offset, int length) {
        System.arraycopy(mem,(int) pos, data,offset,length);
    }

    public void setBytes(long pos, byte[] data, int offset, int length) {
        System.arraycopy(data,offset,mem,(int) pos,length);
    }

    public long getLong5(int off) {
        return ObjectIO.getLong5(mem,off);
    }

    public void putLong5(int off, long val) {
        ObjectIO.putLong5(mem,off,val);
    }

    public void putLong(long pos, long val) {
        ObjectIO.putLong(mem,(int) pos,val);
    }

    public void putInt(long pos, int val) {
        ObjectIO.putInt(mem,(int) pos,val);

    }
    public void create(int size) {

        if (mem == null)
            mem = new byte[size];
        memTop = 0;
        inUse = true;
    }

    public void destroy() {
        mem = null;
        super.destroy();
    }

    public int spaceSize() {
        return mem.length ;
    }

    public void readFromFile(String fileName,boolean map) {

        // Now load it all again and put it on a hashmap.
        // We'll see how much time it takes.
        long started = System.currentTimeMillis();

        InputStream in;

        try {

            //in = new BufferedInputStream(new FileInputStream(fileName));
            in = new FileInputStream(fileName);
            System.out.println("Used Before MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);


            try {
                int avail =in.available();

                //mem = ObjectIO.readNBytes(in,avail);
                if (in.read(mem,0,avail)!=avail) {
                    throw new RuntimeException("not enough data");
                }
                memTop = avail;
            }
            catch (EOFException exc)
            {
                // end of stream
            }
            long currentTime = System.currentTimeMillis();
            System.out.println("Time[s]: "+(currentTime-started)/1000);

            System.out.println("Used After MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024);

            //
            in.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //out.writeObject(s1);


    }
    public void sync() {
        // nothing to do
    }
    public void saveToFile(String fileName )  {

        try {
            //out = new BufferedOutputStream(new FileOutputStream(fileName));
            FileOutputStream out = new FileOutputStream(fileName);
            out.write(mem,0,memTop);
            out.flush();
            //closing the stream
            out.close();
            System.out.println("File "+fileName+" written.");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
