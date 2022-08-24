package co.rsk.datasources.flatdb;
import co.rsk.bahashmaps.AbstractByteArrayHashMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.EnumSet;

import static java.util.Objects.requireNonNull;

public class LogWriter {
    private final File file;
    private final FileChannel fileChannel;

    public LogWriter(File file)
            throws IOException
    {
        requireNonNull(file, "file is null");
        //checkArgument(fileNumber >= 0, "fileNumber is negative");

        this.file = file;
        this.fileChannel = new FileOutputStream(file,true).getChannel();

    }

    public synchronized void close()
    {
        //closed.set(true);

        // try to forces the log to disk
        try {
            fileChannel.force(true);
        }

        catch (IOException ignored) {
        }
        // close the channel
        closeQuietly();

    }

    public void closeQuietly() {
        try {
            fileChannel.close();
        }
        catch (IOException ignored) {
        }
    }
    public synchronized void delete()
    {
        //closed.set(true);

        // close the channel
        closeQuietly();

        // try to delete the file
        file.delete();
    }

    public enum LogRecordType {
        BeginHeader,Entry, EndHeader;
      }

    public synchronized void addEndHeader(boolean force) throws IOException {
        addBeginOrEndHeader(LogRecordType.EndHeader,force);
    }
    public synchronized void addBeginHeader(boolean force) throws IOException {
        addBeginOrEndHeader(LogRecordType.BeginHeader,force);
    }
    public synchronized void addBeginOrEndHeader(LogRecordType recordType,boolean force) throws IOException {
        LogRecord lr = new LogRecord();
        lr.recordType = recordType.ordinal();
        addRecord(lr,force);
    }

    public synchronized void addEntry(LogRecord lr,boolean force) throws IOException {
        lr.recordType = LogRecordType.Entry.ordinal();
        addRecord(lr,force);
    }

    public synchronized void addRecord(LogRecord record, boolean force)
            throws IOException {
        //checkState(!closed.get(), "Log has been closed");
        fileChannel.write(ByteBuffer.wrap(record.toBytes()));

        if (force) {
            fileChannel.force(false);
        }
    }
}
