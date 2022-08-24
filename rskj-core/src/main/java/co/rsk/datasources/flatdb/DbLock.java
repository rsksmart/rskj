package co.rsk.datasources.flatdb;


import com.google.common.base.Throwables;
import com.google.common.io.Closeables;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class DbLock
{
    private final File lockFile;
    private final FileChannel channel;
    private final FileLock lock;

    public DbLock(File lockFile)
            throws IOException
    {
        requireNonNull(lockFile, "lockFile is null");
        this.lockFile = lockFile;
        //if (!this.lockFile.exists()) {
        //    this.lockFile.createNewFile();
        //}

        // open and lock the file
        channel = new RandomAccessFile(lockFile, "rw").getChannel();
        try {
            lock = channel.tryLock();
        }
        catch (IOException e) {
            closeQuietly();
            throw e;
        }

        if (lock == null) {
            throw new IOException(format("Unable to acquire lock on '%s'", lockFile.getAbsolutePath()));
        }
    }

    public void closeQuietly() {
        try {
            channel.close();
        }
        catch (IOException ignored) {
        }
    }
    public boolean isValid()
    {
        return lock.isValid();
    }

    public void release()
    {
        try {
            lock.release();
        }
        catch (IOException e) {
            Throwables.propagate(e);
        }
        finally {
            closeQuietly();
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("DbLock");
        sb.append("{lockFile=").append(lockFile);
        sb.append(", lock=").append(lock);
        sb.append('}');
        return sb.toString();
    }
}
