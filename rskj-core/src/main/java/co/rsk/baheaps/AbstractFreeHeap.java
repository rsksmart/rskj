package co.rsk.baheaps;

import java.io.IOException;
import java.util.List;

public interface AbstractFreeHeap {

    public List<String> getStats();;

    public byte[] retrieveDataByOfs(long encodedOfs);

    public long retrieveNextDataOfsByOfs(long encodedOfs);
    public boolean isOfsAvail(long encodedOfs);
    public boolean isObjectStoredAtOfs(long encodedOfs);
    public void addObjectAtOfs(long ecodedOfs, byte[] encoded, byte[] metadata);

    public byte[] retrieveMetadataByOfs(long encodedOfs);

    public void setMetadataByOfs(long encodedOfs,byte [] metadata);

    public void checkObjectByOfs(long encodedOfs);

    public void removeObjectByOfs(long encodedOfs);

    public boolean isRemapping();
    public void beginRemap() ;
    public void endRemap();
    public void remapByOfs(long encodedOfs);
    public int getUsagePercent();

    public void load() throws IOException;
    public void save() throws IOException;


    public void powerFailure();
    public void processLogEntry(long i,long value);

}
