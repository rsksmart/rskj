package co.rsk.baheaps;


import co.rsk.spaces.HeapFileDesc;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ByteArrayHeap extends ByteArrayHeapBase implements AbstractByteArrayHeap {

    // Here we need to return if the actual key in the datasource exists
    // NOT if the database exists, because the database has already been
    // initialized at this point, so all files exsits.
    public boolean dataSourceExists() {
        /*
        String fileName = baseFileName + ".desc";


        String fileName = baseFileName + ....
        Path path = Paths.get(fileName);
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());

         */
        byte[] key = "desc".getBytes(StandardCharsets.UTF_8);
        return descDataSource.get(key)!=null;
    }

    public boolean firstSpaceFileExists() {
        // here we could check the presence of certain key in the data source
        // now we simply check that there is at least one space
        String fileName = getSpaceFileName(0);
        Path path = Paths.get(fileName);
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());
    }

    public boolean fileExists() {
        if (descDataSource==null) {
            return descFileExists();
        } else {
            if (dataSourceExists())
                return true;
            if ((autoUpgrade) && (descFileExists())) {
                return true;
            }
            return false;
        }
    }


    public long load() throws IOException {
        if ((autoUpgrade) && (descFileExists())) {
            if (dataSourceExists()) {
                // if both the old format and the new format co-exists
                // we simple remove the old format file
                deleteDescFile();
            }
            HeapFileDesc desc;
            desc = HeapFileDesc.loadFromFile(baseFileName + ".desc");
            desc.saveToDataSource(descDataSource,"desc");
            autoUpgrade = false;
        }

        long r = super.load();
        return r;
    }

    public void save() throws IOException {
        super.save();
    }

    @Override
    public void powerFailure() {
        super.closeFiles();
    }

}
