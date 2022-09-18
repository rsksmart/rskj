package co.rsk.spaces;

import co.rsk.dbutils.ObjectIO;
import org.ethereum.datasource.KeyValueDataSource;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class HeapFileDesc {
    public int metadataLen;

    public void saveToDataSource(KeyValueDataSource ds, String key) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            saveToOutputStream(out);
            out.flush();
            //closing the stream
            ds.put(key.getBytes(StandardCharsets.UTF_8), out.toByteArray());
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void saveToFile(String fileName) {
        try {
            //out = new BufferedOutputStream(new FileOutputStream(fileName));
            FileOutputStream out = new FileOutputStream(fileName);
            saveToOutputStream(out);
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

    private void saveToOutputStream(OutputStream out) throws IOException {
        ObjectIO.writeInt(out,metadataLen);

    }

    public void writeArray(OutputStream out,int[] a) throws IOException {
        ObjectIO.writeInt(out,a.length);
        for(int i=0;i<a.length;i++) {
            ObjectIO.writeInt(out,a[i]);
        }

    }
    public static HeapFileDesc loadFromDataSource(KeyValueDataSource ds,String key) {
        HeapFileDesc d = new HeapFileDesc();

        byte[] data = ds.get(key.getBytes(StandardCharsets.UTF_8));
        InputStream in = new ByteArrayInputStream(data);
        try {
            readFromInputStream(d, in);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return d;

    }
    public static HeapFileDesc loadFromFile(String fileName) {
        HeapFileDesc d = new HeapFileDesc();
        InputStream in;
        try {

            //in = new BufferedInputStream(new FileInputStream(fileName));
            in = new FileInputStream(fileName);
            readFromInputStream(d, in);
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return d;
    }

    private static void readFromInputStream(HeapFileDesc d, InputStream in) throws IOException {
        d.metadataLen = ObjectIO.readInt(in);
    }

    public static int[] loadArray(InputStream in) throws IOException {
        int nFilled = ObjectIO.readInt(in);
        int[] ret = new int[nFilled];
        for (int i=0;i<nFilled;i++) {
            ret[i] = ObjectIO.readInt(in);
        }
        return ret;
    }
}
