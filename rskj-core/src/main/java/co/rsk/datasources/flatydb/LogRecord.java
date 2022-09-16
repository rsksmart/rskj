package co.rsk.datasources.flatydb;

import co.rsk.dbutils.ObjectIO;

public class LogRecord {
    public int recordType;
    public long htPos;
    public int htValue;

    public void fromBytes(byte[] src) {
        recordType =ObjectIO.getInt(src,0);
        htPos = ObjectIO.getLong(src,4);
        htValue = ObjectIO.getInt(src,8);
    }

    public byte[] toBytes() {
        byte[] result = new byte[16];
        ObjectIO.putInt(result,0,recordType);
        ObjectIO.putLong(result,4,htPos);
        ObjectIO.putInt(result,8,htValue);
        return result;

    }
}
