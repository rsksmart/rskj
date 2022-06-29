package co.rsk.baheaps;


import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ByteArrayHeap extends ByteArrayHeapBase implements AbstractByteArrayHeap {

    public boolean fileExists() {
        Path path = Paths.get(baseFileName + ".desc");
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());
    }


    public long load() throws IOException {
        long r = super.load();
        return r;
    }

    public void save(long rootOfs) throws IOException {
        super.save(rootOfs);
    }

}
