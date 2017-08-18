package co.rsk.util;

import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;

import java.util.Arrays;

/**
 * Created by martin.coll on 16/08/2017.
 */
public class RLPElementView {

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
    private static final int OFFSET_LONG_ITEM = 0xb8;

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
    private static final int OFFSET_LONG_LIST = 0xf8;

    private RLPElement lazyElement;
    private byte[] data;
    private int rlpStart;
    private int length;
    private int offset;
    private RLPElementType type;

    public RLPElement getOrCreateElement() {
        if (lazyElement == null) {
            lazyElement = createElement(type, data, rlpStart, length, offset);
        }
        return lazyElement;
    }

    public int getLength() {
        return length;
    }

    public int getOffset() {
        return offset;
    }

    public RLPElementType getType() {
        return type;
    }

    /**
     * Decodes that that starts at given index
     */
    public static RLPElementView calculateElementInfo(byte[] data, int index) {
        if (data.length <= index) {
            throw new RuntimeException("invalid index RLP raw data array");
        }

        RLPElementView info = new RLPElementView();
        info.rlpStart = index;
        info.data = data;
        int prefix = data[index] & 0xFF;

        // Switch checking what kind of element is encoded
        // There are 2 kind of objects: elems and lists
        // There are 4 kind of elems:
        //   * empty
        //   * 1 byte long, < 128
        //   * up to 55 bytes long
        //   * more than 55 bytes long
        // There are 2 kind of lists
        //   * up to 55 bytes long
        //   * more than 55 bytes long
        if (prefix < OFFSET_SHORT_ITEM) {
            // single byte item
            info.type = RLPElementType.ITEM;
            info.length = 1;
            info.offset = index;
        } else if (prefix == OFFSET_SHORT_ITEM) {
            // null item
            info.type = RLPElementType.NULL_ITEM;
            info.length = 1;
            info.offset = index;
        } else if (prefix < OFFSET_LONG_ITEM) {
            // It's an item less than 55 bytes long,
            // data[0] - 0x80 == length of the item
            info.type = RLPElementType.ITEM;
            info.length = prefix - OFFSET_SHORT_ITEM;
            info.offset = index + 1;
        } else if (prefix < OFFSET_SHORT_LIST) {
            // It's an item with a payload more than 55 bytes
            // data[0] - 0xB7 = how much next bytes allocated for
            // the length of the string
            byte lengthOfLength = (byte) (data[index] - OFFSET_LONG_ITEM + 1);
            info.type = RLPElementType.ITEM;
            info.length = readLengthOfLength(data, index + 1, lengthOfLength);
            info.offset = index + 1 + lengthOfLength;
        } else if (prefix < OFFSET_LONG_LIST) {
            // It's a list with a payload less than 55 bytes
            info.type = RLPElementType.SHORT_LIST;
            info.length = (byte) (prefix - OFFSET_SHORT_LIST);
            //info.length = (byte) (((data[index] & 0xFF) - OFFSET_SHORT_LIST) & 0xFF);
            info.offset = index + 1;
        } else {
            // It's a list with a payload more than 55 bytes
            // data[0] - 0xF7 = how many next bytes allocated
            // for the length of the list
            byte lengthOfLength = (byte) (data[index] - OFFSET_LONG_LIST + 1);
            info.type = RLPElementType.LONG_LIST;
            info.length = readLengthOfLength(data, index + 1, lengthOfLength);
            info.offset = index + lengthOfLength + 1;
        }

        if (data.length - info.offset < info.length) {
            throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
        }

        return info;
    }

    private RLPElementView() {}

    private static RLPElement createElement(RLPElementType type, byte[] data, int rlpStart, int length, int offset) {
        if (type == RLPElementType.NULL_ITEM) {
            byte[] item = ByteUtil.EMPTY_BYTE_ARRAY;
            return new RLPItem(item);
        } else if (type == RLPElementType.ITEM) {
            byte[] item = Arrays.copyOfRange(data, offset, offset + length);
            return new RLPItem(item);
        } else { // list
            // because of how EthJ works, the RLPList has to have the full RLP data
            int longListLength = length + (offset - rlpStart);
            byte[] rlpData = Arrays.copyOfRange(data, rlpStart, rlpStart + longListLength);

            RLPList newList = new RLPList();
            newList.setRLPData(rlpData);
            return newList;
        }
    }

    private static int readLengthOfLength(byte[] data, int offset, byte lengthOfNumber) {
        if (data.length < offset + lengthOfNumber) {
            throw new RLPException("The length of the RLP item length can't possibly fit the data byte array");
        }

        long length = readBigEndianLong(data, offset, lengthOfNumber);

        if (Long.compareUnsigned(length, Integer.MAX_VALUE) > 0) {
            throw new RLPException("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have");
        }

        return (int)length;
    }

    private static long readBigEndianLong(byte[] data, int offset, byte lengthOfNumber) {
        byte pow = (byte)(lengthOfNumber - 1);
        long length = 0;
        for (int i = offset; i < offset + lengthOfNumber; ++i) {
            length += (long)(data[i] & 0xff) << (8 * pow);
            pow--;
        }

        return length;
    }
}
