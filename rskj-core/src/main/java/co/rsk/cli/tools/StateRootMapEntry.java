package co.rsk.cli.tools;

import org.ethereum.datasource.ObjectIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StateRootMapEntry {
    public byte[] hash;
    public byte[] value;

    public StateRootMapEntry(byte[] hash, byte[] value) {
        this.hash = hash;
        this.value = value;
    }

    public StateRootMapEntry(InputStream in) throws IOException {
        readObject(in);
    }

    public void readObject(InputStream stream)
            throws IOException {

        this.hash = ObjectIO.readNBytes(stream, 32);
        this.value = ObjectIO.readNBytes(stream, 32);
    }

    public void writeObject(OutputStream out)
            throws IOException {
        if (hash.length != 32) {
            throw new RuntimeException("Invalid hash");
        }
        if (value.length != 32) {
            throw new RuntimeException("Invalid hash");
        }

        out.write(hash);
        out.write(value);
    }
}
