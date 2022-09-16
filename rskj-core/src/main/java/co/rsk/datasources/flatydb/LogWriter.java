package co.rsk.datasources.flatydb;
import org.bouncycastle.util.Arrays;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import static java.util.Objects.requireNonNull;

public class LogWriter {
    private final File file;
    private final FileChannel fileChannel;
    int pageSize = 32768;
    byte[] page = new byte[pageSize];
    ByteBuffer pageBuffer = ByteBuffer.wrap(page);

    public LogWriter(File file)
            throws IOException
    {
        requireNonNull(file, "file is null");
        //checkArgument(fileNumber >= 0, "fileNumber is negative");

        this.file = file;
        this.fileChannel = new FileOutputStream(file,true).getChannel();

    }

    public void writeRemaining() throws IOException {
        int count = pageBuffer.position();
        if (count>0) {
            pageBuffer.position(0);
            //long fs = fileChannel.size();
            fileChannel.write(pageBuffer.limit(count));
            //long fs2 = fileChannel.size();
            //System.out.println(""+fs+" "+fs2+" "+(fs2-fs)+ " "+count);
            pageBuffer.limit(pageSize); // restore
            //pageBuffer = ByteBuffer.wrap(page);
            pageBuffer.position(0);
        }
    }

    public synchronized void close()
    {
        //closed.set(true);


        // try to forces the log to disk
        try {
            writeRemaining();
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
        pageBuffer.put(ByteBuffer.wrap(record.toBytes()));
        if (pageBuffer.position()==pageSize) {
            fileChannel.write(pageBuffer);
            Arrays.clear(page);
            pageBuffer.position(0);
        }

        if (force) {
            fileChannel.force(false);
        }
    }
}
