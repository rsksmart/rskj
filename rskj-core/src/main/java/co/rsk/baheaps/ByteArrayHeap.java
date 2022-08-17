package co.rsk.baheaps;


import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ByteArrayHeap extends ByteArrayHeapBase implements AbstractByteArrayHeap {

    public boolean fileExists() {
        String fileName = null;
        if (descDataSource!=null) {
            // here we could check the presence of certain key in the data source
            // now we simply check that there is at least one space
            fileName = getSpaceFileName(0);
        } else {
            fileName =baseFileName + ".desc";
        }
        Path path = Paths.get(fileName);
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());
    }


    public long load() throws IOException {
        long r = super.load();
        return r;
    }

    public void save() throws IOException {
        super.save();
    }

}
