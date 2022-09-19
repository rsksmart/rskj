package co.rsk.freeheap;

import java.util.EnumSet;

public class Format {
    public static final int latestDBVersion = 1;
    public int dbVersion = latestDBVersion;
    public int pageSize = 4096;
    public EnumSet<CreationFlag> creationFlags =
            CreationFlag.All;

}
