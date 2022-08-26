package co.rsk.datasources.flatdb;

import co.rsk.bahashmaps.AbstractByteArrayHashMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LogManager {
    LogWriter logWriter;
    Path dbPath;
    boolean batchBegan;
    Path logFilePath;

    public LogManager(Path dbPath) {
        this.dbPath = dbPath;
        logFilePath = dbPath.resolve("log.dat");

    }

    protected void CheckNoDoubleBegin() {
        if (batchBegan) {
            throw new RuntimeException("Invalid batch reentrancy");
        }
    }

    public void beginLog() throws IOException {
         CheckNoDoubleBegin();
         File logFile = logFilePath.toFile();
         logWriter = new LogWriter(logFile);
         logWriter.addBeginHeader(false);
         batchBegan = true;
    }

   public void  CheckBatchBegan() {
       if (!batchBegan) {
           throw new RuntimeException("Unmatched batch end");
       }
   }
    public void endLog() throws IOException {
        CheckBatchBegan();

        logWriter.addEndHeader(true);
        logWriter.close();
        logWriter = null;
        batchBegan = false;
    }

    public void logSetPos(int i, long markedOffset) {
        if (!batchBegan)
            return; // We're not batching
        LogRecord logRecord = new LogRecord();
        logRecord.htPos = i;
        logRecord.htValue = markedOffset;
        try {
            logWriter.addEntry(logRecord,false);
        } catch (IOException e) {
            // ignore, database will be corrupted later
        }
    }

    public boolean logExists() {
        File logFile = logFilePath.toFile();
        return logFile.exists();
    }
    public void putAllEntries(AbstractByteArrayHashMap bamap, List<LogRecord> entries) {
        for(LogRecord entry : entries) {
            bamap.processLogEntry(entry.htPos,entry.htValue);
        }
    }

    public void processLog(AbstractByteArrayHashMap bamap) throws IOException {
        File logFile = logFilePath.toFile();
        LogReader logReader = new LogReader(logFile);
        try {
            if (logReader.eof()) {
                // empty file without header / footer
                return;
            }
            boolean inBatch = false;


            List<LogRecord> entries = new ArrayList<>();
            while (!logReader.eof()) {
                LogRecord entry = logReader.readRecord();
                if (entry.recordType == LogWriter.LogRecordType.BeginHeader.ordinal()) {
                    if (inBatch) {
                        // corrupted file, continue removing the log
                        return;
                    }
                    inBatch = true;
                    continue;
                }
                if (!inBatch) {
                    // corrupted
                    return;
                }
                if (entry.recordType == LogWriter.LogRecordType.EndHeader.ordinal()) {
                    // commit again this changes
                    putAllEntries(bamap, entries);
                    bamap.save();
                    entries.clear();
                    inBatch = false;
                    continue;
                }
                if (entry.recordType != LogWriter.LogRecordType.Entry.ordinal()) {
                    // Corrupted file. Stop here, but do not process the last batch.
                    bamap.save();
                    entries.clear();
                    return;
                }
                entries.add(entry);
            }
            if (entries.size() != 0) {
                // some entries left: corrupted file
                // just  continue
            }
        } finally{
                logReader.closeQuietly();
                logFile.delete();
        }
    }

    public void deleteLog() {
        File logFile = logFilePath.toFile();
        logFile.delete();
    }
}
