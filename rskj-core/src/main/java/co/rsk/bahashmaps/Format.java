package co.rsk.bahashmaps;

import java.util.EnumSet;

public class Format {
    public static final int latestDBVersion = 1;
    public int dbVersion = latestDBVersion;
    public int tablePosSize;
    public int pageSize = 4096;
    public EnumSet<AbstractByteArrayHashMap.CreationFlag> creationFlags =
            AbstractByteArrayHashMap.CreationFlag.All;

}
