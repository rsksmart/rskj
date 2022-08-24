package co.rsk.datasources.flatdb;

import co.rsk.dbutils.ObjectIO;

public class LogRecord {
    public int recordType;
    public int htPos;
    public long htValue;

    public void fromBytes(byte[] src) {
        recordType =ObjectIO.getInt(src,0);
        htPos = ObjectIO.getInt(src,4);
        htValue = ObjectIO.getLong(src,8);
    }

    public byte[] toBytes() {
        byte[] result = new byte[16];
        ObjectIO.putInt(result,0,recordType);
        ObjectIO.putInt(result,4,htPos);
        ObjectIO.putLong(result,8,htValue);
        return result;

    }
}
