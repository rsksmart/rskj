package co.rsk.util;

/**
 * Created by martin.coll on 16/08/2017.
 */
public class RLPElementInfo {

    /** RLP encoding rules are defined as follows: */

    /*
     * For a single byte whose value is in the [0x00, 0x7f] range, that byte is
     * its own RLP encoding.
     */

    /**
     * [0x80]
     * If a string is 0-55 bytes long, the RLP encoding consists of a single
     * byte with value 0x80 plus the length of the string followed by the
     * string. The range of the first byte is thus [0x80, 0xb7].
     */
    private static final int OFFSET_SHORT_ITEM = 0x80;

    /**
     * [0xb7]
     * If a string is more than 55 bytes long, the RLP encoding consists of a
     * single byte with value 0xb7 plus the length of the length of the string
     * in binary form, followed by the length of the string, followed by the
     * string. For example, a length-1024 string would be encoded as
     * \xb9\x04\x00 followed by the string. The range of the first byte is thus
     * [0xb8, 0xbf].
     */
    private static final int OFFSET_LONG_ITEM = 0xb7;

    /**
     * [0xc0]
     * If the total payload of a list (i.e. the combined length of all its
     * items) is 0-55 bytes long, the RLP encoding consists of a single byte
     * with value 0xc0 plus the length of the list followed by the concatenation
     * of the RLP encodings of the items. The range of the first byte is thus
     * [0xc0, 0xf7].
     */
    private static final int OFFSET_SHORT_LIST = 0xc0;

    /**
     * [0xf7]
     * If the total payload of a list is more than 55 bytes long, the RLP
     * encoding consists of a single byte with value 0xf7 plus the length of the
     * length of the list in binary form, followed by the length of the list,
     * followed by the concatenation of the RLP encodings of the items. The
     * range of the first byte is thus [0xf8, 0xff].
     */
    private static final int OFFSET_LONG_LIST = 0xf7;

    private int length;
    private int offset;

    public int getLength() {
        return length;
    }

    public int getOffset() {
        return offset;
    }

    public static RLPElementInfo calculateElementInfo(byte[] data, int index) {
        if (data.length <= index) {
            throw new RuntimeException("invalid index RLP raw data array");
        }

        RLPElementInfo info = new RLPElementInfo();
        int firstByte = data[index] & 0xFF;

        if (firstByte < OFFSET_SHORT_ITEM) {
            info.length = 1;
            info.offset = index;
        } else if (firstByte < OFFSET_LONG_ITEM) {
            info.length = firstByte - OFFSET_SHORT_ITEM;
            info.offset = index + 1;
        } else if (firstByte < OFFSET_SHORT_LIST) {
            int lengthOfLength = data[index] - OFFSET_LONG_ITEM;
            info.length = calcLengthRaw(lengthOfLength, data, index);
            info.offset = index + 1 + lengthOfLength;
        } else {
            throw new RuntimeException("wrong decode attempt");
        }

        if (data.length - info.offset < info.length) {
            throw new RuntimeException("the RLP byte array doesn't have enough space to hold an element with the specified length");
        }

        return info;
    }

    private static int calcLengthRaw(int lengthOfLength, byte[] msgData, int index) {
        byte pow = (byte) (lengthOfLength - 1);
        int length = 0;
        for (int i = 1; i <= lengthOfLength; ++i) {
            length += msgData[index + i] << (8 * pow);
            pow--;
        }
        return length;
    }
}
