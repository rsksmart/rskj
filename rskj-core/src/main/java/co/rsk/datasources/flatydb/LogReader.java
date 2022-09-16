package co.rsk.datasources.flatydb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class LogReader {
    private final FileChannel fileChannel;
    boolean corrupted;

    public LogReader(File file) throws IOException {
        this.fileChannel = new FileInputStream(file).getChannel();
    }

    public boolean eof() throws IOException {
        return (fileChannel.position() == fileChannel.size());
    }

    public LogRecord readRecord() throws IOException {
        LogRecord lr = new LogRecord();
        byte[] logRecordBuffer = new byte[16];

        try {
            if (fileChannel.read(ByteBuffer.wrap(logRecordBuffer))!=logRecordBuffer.length) {
                corrupted = true;
                return null;
            }
        } catch (IOException e) {
            corrupted = true;
            throw e;
        }
        lr.fromBytes(logRecordBuffer);
        return lr;
    }

    public void closeQuietly() {
        try {
            fileChannel.close();
        }
        catch (IOException ignored) {
        }
    }
}
