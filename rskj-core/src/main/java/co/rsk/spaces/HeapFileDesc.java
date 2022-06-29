package co.rsk.spaces;

import co.rsk.dbutils.ObjectIO;

import java.io.*;

public class HeapFileDesc {

    public int[] filledSpaces;
    public int[] emptySpaces;
    public int currentSpace;
    public long rootOfs;
    public int metadataLen;

    public void saveToFile(String fileName) {
        try {
            //out = new BufferedOutputStream(new FileOutputStream(fileName));
            FileOutputStream out = new FileOutputStream(fileName);
            writeArray(out,filledSpaces);
            writeArray(out,emptySpaces);
            ObjectIO.writeInt(out,currentSpace);
            ObjectIO.writeLong(out,rootOfs);
            ObjectIO.writeInt(out,metadataLen);

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

    public void writeArray(FileOutputStream out,int[] a) throws IOException {
        ObjectIO.writeInt(out,a.length);
        for(int i=0;i<a.length;i++) {
            ObjectIO.writeInt(out,a[i]);
        }

    }
    public static HeapFileDesc loadFromFile(String fileName) {
        HeapFileDesc d = new HeapFileDesc();
        InputStream in;
        try {

            //in = new BufferedInputStream(new FileInputStream(fileName));
            in = new FileInputStream(fileName);
            d.filledSpaces =loadArray(in);
            d.emptySpaces = loadArray(in);
            d.currentSpace = ObjectIO.readInt(in);
            d.rootOfs = ObjectIO.readLong(in);
            d.metadataLen = ObjectIO.readInt(in);
            in.close();


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return d;
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
