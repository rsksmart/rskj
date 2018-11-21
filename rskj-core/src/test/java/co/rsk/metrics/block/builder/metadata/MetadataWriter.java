package co.rsk.metrics.block.builder.metadata;

import org.ethereum.core.Transaction;

import java.util.List;

public interface MetadataWriter {
    public void write(String line);

    public void close();
}
