package co.rsk.baheaps;

import java.util.List;

public interface AbstractByteArrayRefHeap {

    public boolean heapIsAlmostFull();
    public boolean isRemapping();
    public void beginRemap() ;
    public void endRemap();
    public int getUsagePercent();

    public void removeObjectByHandle(int handle);
    public byte[] retrieveDataByHandle(int handle) ;

    public void setMetadataByHandle(int handle, byte[] metadata) ;

    public byte[] retrieveMetadataByHandle(int handle) ;

    public int addAndReturnHandle(byte[] encoded, byte[] metadata);

    public void checkHandle(int handle);
    public void remapByHandle(int handle);

    public List<String> getStats();

}
