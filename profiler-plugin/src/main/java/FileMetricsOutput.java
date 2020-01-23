
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileMetricsOutput implements MetricsOutput {

    private final OutputStream outputFile;

    public FileMetricsOutput(String fileName) {
        try {
            outputFile = Files.newOutputStream(Paths.get("observations.csv"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            outputFile.write("Category,Time(ns)\n".getBytes());
        } catch (IOException ex) {
            throw new RuntimeException("Exception when initializing profiler", ex);
        }
    }

    @Override
    public void newObservation(Observation observation) {
        String line = String.format("%s,%d\n", observation.getCategory(), observation.getDuration().toNanos());
        try {
            outputFile.write(line.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
