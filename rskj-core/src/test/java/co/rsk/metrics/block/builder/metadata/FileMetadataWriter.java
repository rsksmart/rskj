package co.rsk.metrics.block.builder.metadata;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileMetadataWriter implements MetadataWriter {

    BufferedWriter writer;

    public FileMetadataWriter(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if(Files.exists(path)){
            Files.delete(path);
        }

        writer = Files.newBufferedWriter(path);
    }

    @Override
    public void write(String line) {
        try {
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
