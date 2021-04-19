package org.ethereum.datasource;

import java.io.*;

public class ObjectIO {

    /**
     * Utility methods for packing/unpacking primitive values in/out of byte arrays
     * using big-endian byte ordering.
     */
    /*
     * Methods for unpacking primitive values from byte arrays starting at
     * given offsets.
     */

    static boolean getBoolean(byte[] b, int off) {
        return b[off] != 0;
    }

    static char getChar(byte[] b, int off) {
        return (char) ((b[off + 1] & 0xFF) +
                (b[off] << 8));
    }

    static short getShort(byte[] b, int off) {
        return (short) ((b[off + 1] & 0xFF) +
                (b[off] << 8));
    }

    static int getInt(byte[] b, int off) {
        return ((b[off + 3] & 0xFF)      ) +
                ((b[off + 2] & 0xFF) <<  8) +
                ((b[off + 1] & 0xFF) << 16) +
                ((b[off    ]       ) << 24);
    }

    static float getFloat(byte[] b, int off) {
        return Float.intBitsToFloat(getInt(b, off));
    }

    static long getLong(byte[] b, int off) {
        return ((b[off + 7] & 0xFFL)      ) +
                ((b[off + 6] & 0xFFL) <<  8) +
                ((b[off + 5] & 0xFFL) << 16) +
                ((b[off + 4] & 0xFFL) << 24) +
                ((b[off + 3] & 0xFFL) << 32) +
                ((b[off + 2] & 0xFFL) << 40) +
                ((b[off + 1] & 0xFFL) << 48) +
                (((long) b[off])      << 56);
    }

    static double getDouble(byte[] b, int off) {
        return Double.longBitsToDouble(getLong(b, off));
    }

    /*
     * Methods for packing primitive values into byte arrays starting at given
     * offsets.
     */

    static void putBoolean(byte[] b, int off, boolean val) {
        b[off] = (byte) (val ? 1 : 0);
    }

    static void putChar(byte[] b, int off, char val) {
        b[off + 1] = (byte) (val      );
        b[off    ] = (byte) (val >>> 8);
    }

    static void putShort(byte[] b, int off, short val) {
        b[off + 1] = (byte) (val      );
        b[off    ] = (byte) (val >>> 8);
    }

    static void putInt(byte[] b, int off, int val) {
        b[off + 3] = (byte) (val       );
        b[off + 2] = (byte) (val >>>  8);
        b[off + 1] = (byte) (val >>> 16);
        b[off    ] = (byte) (val >>> 24);
    }

    static void putFloat(byte[] b, int off, float val) {
        putInt(b, off,  Float.floatToIntBits(val));
    }

    static void putLong(byte[] b, int off, long val) {
        b[off + 7] = (byte) (val       );
        b[off + 6] = (byte) (val >>>  8);
        b[off + 5] = (byte) (val >>> 16);
        b[off + 4] = (byte) (val >>> 24);
        b[off + 3] = (byte) (val >>> 32);
        b[off + 2] = (byte) (val >>> 40);
        b[off + 1] = (byte) (val >>> 48);
        b[off    ] = (byte) (val >>> 56);
    }

    static void putDouble(byte[] b, int off, double val) {
        putLong(b, off, Double.doubleToLongBits(val));
    }

    static public byte[] readNBytes(InputStream in,int n) throws IOException {
        byte[] buf = new byte[n];
        int  num = in.read(buf);
        if (num<n)
            throw new EOFException();

        return buf;
    }
    static public int readInt(InputStream in) throws IOException {
        byte[] buf = new byte[4];
        if (in.read(buf, 0, 4)<4)
            throw new EOFException();
        int v = ObjectIO.getInt(buf, 0);
        return v;
    }

    static public boolean readBoolean(InputStream in) throws IOException {
        int v = in.read();
        if (v < 0) {
            throw new EOFException();
        }
        return (v != 0);
    }

    static public byte readByte(InputStream in) throws IOException {
        int v = in.read();
        if (v < 0) {
            throw new EOFException();
        }
        return (byte) v;
    }

    static public int readUnsignedByte(InputStream in) throws IOException {
        int v = in.read();
        if (v < 0) {
            throw new EOFException();
        }
        return v;
    }

    static public char readChar(InputStream in) throws IOException {
        byte[] buf = new byte[2];
        if (in.read(buf, 0, 2)<2)
            throw new EOFException();
        char v = getChar(buf, 0);
        return v;
    }

    static public short readShort(InputStream in) throws IOException {
        byte[] buf = new byte[2];
        if (in.read(buf, 0, 2)<2)
            throw new EOFException();
        short v = getShort(buf, 0);
        return v;
    }

    static public int readUnsignedShort(InputStream in) throws IOException {
        byte[] buf = new byte[2];
        if (in.read(buf, 0, 2)<2)
            throw new EOFException();
        int v = getShort(buf,0) & 0xFFFF;
        return v;
    }



    static public float readFloat(InputStream in) throws IOException {
        byte[] buf = new byte[4];
        if (in.read(buf, 0, 4)<4)
            throw new EOFException();
        float v = getFloat(buf, 0);
        return v;
    }

    static public long readLong(InputStream in) throws IOException {
        byte[] buf = new byte[8];
        if (in.read(buf, 0, 8)<8)
            throw new EOFException();
        long v = getLong(buf, 0);
        return v;
    }

    static public double readDouble(InputStream in) throws IOException {
        byte[] buf = new byte[8];
        if (in.read(buf, 0, 8)<8)
            throw new EOFException();
        double v = getDouble(buf, 0);
        return v;
    }

    ////////////////////////////////////////////////////////////////////

    static public void writeBoolean(OutputStream out,boolean v) throws IOException {
        byte[] buf = new byte[4];
        putBoolean(buf, 0, v);
        out.write(buf);

    }

    static public void writeByte(OutputStream out,int v) throws IOException {
        out.write(v);
    }

    static public void writeChar(OutputStream out,int v) throws IOException {
        out.write(v); // check if it's 1 or 2 bytes
    }

    static public void writeShort(OutputStream out,int v) throws IOException {
        byte[] buf = new byte[4];
        putShort(buf, 0, (short) v);
        out.write(buf);
    }

    static public void writeInt(OutputStream out,int v) throws IOException {
        byte[] buf = new byte[4];
        putInt(buf, 0, v);
        out.write(buf);
    }

    static public void writeFloat(OutputStream out,float v) throws IOException {
        byte[] buf = new byte[4];
        putFloat(buf, 0, v);
        out.write(buf);

    }

    static public void writeLong(OutputStream out,long v) throws IOException {
        byte[] buf = new byte[8];
        putLong(buf, 0, v);
        out.write(buf);

    }

    static public void writeDouble(OutputStream out,double v) throws IOException {
        byte[] buf = new byte[8];
        putDouble(buf, 0, v);
        out.write(buf);

    }


}
