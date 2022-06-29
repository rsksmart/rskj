package co.rsk.spaces;

import java.nio.ByteBuffer;

public abstract class Space {

    public int memTop = 0;


    public boolean filled;
    public boolean inUse = false;
    public int previousSpaceNum = -1; // unlinked


    /**
     * Reads a byte from the specified position.
     * @param pos the position in the memory mapped file
     * @return the value read
     */
    abstract public byte getByte(long pos) ;

    /**
     * Reads an int from the specified position.
     * @param pos the position in the memory mapped file
     * @return the value read
     */
    abstract public int getInt(long pos);

    /**
     * Reads a long from the specified position.
     * @param pos position in the memory mapped file
     * @return the value read
     */
    abstract public long getLong(long pos);

    /**
     * Writes a byte to the specified position.
     * @param pos the position in the memory mapped file
     * @param val the value to write
     */
    abstract public void putByte(long pos, byte val);

    /**
     * Writes an int to the specified position.
     * @param pos the position in the memory mapped file
     * @param val the value to write
     */
    abstract public void putInt(long pos, int val);

    /**
     * Writes a long to the specified position.
     * @param pos the position in the memory mapped file
     * @param val the value to write
     */
    abstract public void putLong(long pos, long val);

    /**
     * Reads a buffer of data.
     * @param pos the position in the memory mapped file
     * @param data the input buffer
     * @param offset the offset in the buffer of the first byte to read data into
     * @param length the length of the data
     */
    abstract public void getBytes(long pos, byte[] data, int offset, int length);

    /**
     * Writes a buffer of data.
     * @param pos the position in the memory mapped file
     * @param data the output buffer
     * @param offset the offset in the buffer of the first byte to write
     * @param length the length of the data
     */
    abstract public void setBytes(long pos, byte[] data, int offset, int length);

    abstract public long getLong5(int off);

    abstract public void putLong5(int off, long val) ;

    abstract public void copyBytes(int srcPos, Space dest, int destPos, int length);

    abstract public  ByteBuffer getByteBuffer(int offset, int length);

    public boolean created() {
        return true;
    }

    public void create(int size) {
        memTop = 0;
        inUse = true;
    }

    public void unlink() {
        previousSpaceNum = -1; //
    }

    public void destroy() {
        filled = false;
    }

    public void softCreate() {
        memTop = 0;
        inUse = true;
    }

    public void softDestroy() {
        // do not remove the memory: this causes the Java garbage colelctor to try to move huge
        // objects around.
        filled = false;
        inUse = false;
    }

    public boolean empty() {
        return (!inUse);
    }

    abstract public int spaceSize();

    public int spaceAvail() {
        return spaceSize() - memTop;
    }

    public boolean spaceAvailFor(int msgLength) {
        return (spaceAvail() >= msgLength);
    }

    public int getUsagePercent() {
        return (int) ((long) memTop * 100 / spaceSize());
    }

    abstract public void readFromFile(String fileName,boolean map);

    abstract public void saveToFile(String fileName );


}
