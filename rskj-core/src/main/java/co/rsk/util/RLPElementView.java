package co.rsk.util;

import org.ethereum.util.*;

import javax.annotation.Nonnull;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

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
    private ByteBuffer dataWithPrefix;
    private ByteBuffer data;
    private RLPElementType type;

    public RLPElement getOrCreateElement() {
        if (lazyElement == null) {
            lazyElement = createElement();
        }
        return lazyElement;
    }

    public RLPElementType getType() {
        return type;
    }

    /**
     * Iterate items in an RLP-encoded buffer.
     * @param data a buffer containing RLP-encoded items
     */
    public static void forEachRlp(@Nonnull ByteBuffer data, Consumer<RLPElementView> callback) {
        data = data.slice();
        while (data.hasRemaining()) {
            RLPElementView info = calculateFirstElementInfo(data);
            callback.accept(info);
        }
    }

    /**
     * Decodes the first item in the byte buffer.
     * @param data: the buffer position will be after the end of the first object
     */
    public static RLPElementView calculateFirstElementInfo(@Nonnull ByteBuffer data) {
        try {
            // we slice it first so we can use 0-based indexes
            ByteBuffer newData = data.slice();
            RLPElementView info = new RLPElementView();
            int prefix = Byte.toUnsignedInt(newData.get(0));
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
                newData.position(0).limit(1);
            } else if (prefix == OFFSET_SHORT_ITEM) {
                // null item
                info.type = RLPElementType.NULL_ITEM;
                newData.position(0).limit(1);
            } else if (prefix < OFFSET_LONG_ITEM) {
                // It's an item less than 55 bytes long,
                // data[0] - 0x80 == length of the item
                info.type = RLPElementType.ITEM;
                newData.position(1)
                       .limit(1 + prefix - OFFSET_SHORT_ITEM);
            } else if (prefix < OFFSET_SHORT_LIST) {
                // It's an item with a payload more than 55 bytes
                // data[0] - 0xB7 = how much next bytes allocated for
                // the length of the string
                info.type = RLPElementType.ITEM;
                byte lengthOfLength = (byte) (newData.get(0) - OFFSET_LONG_ITEM + 1);
                int lengthOfData = getLengthOfData(newData, lengthOfLength);
                newData.position(1 + lengthOfLength)
                       .limit(1 + lengthOfLength + lengthOfData);
            } else if (prefix < OFFSET_LONG_LIST) {
                // It's a list with a payload less than 55 bytes
                info.type = RLPElementType.SHORT_LIST;
                // because of how EthJ works, the RLPList has to have the full RLP data
                newData.position(1)
                       .limit(1 + prefix - OFFSET_SHORT_LIST);
            } else {
                // It's a list with a payload more than 55 bytes
                // data[0] - 0xF7 = how many next bytes allocated
                // for the length of the list
                info.type = RLPElementType.LONG_LIST;
                byte lengthOfLength = (byte) (newData.get(0) - OFFSET_LONG_LIST + 1);
                int lengthOfData = getLengthOfData(newData, lengthOfLength);
                // because of how EthJ works, the RLPList has to have the full RLP data
                newData.position(1 + lengthOfLength)
                       .limit(1 + lengthOfLength + lengthOfData);
            }

            int nextStart = data.position() + newData.limit();
            // save with prefix specifically for RLPList
            info.dataWithPrefix = data.duplicate();
            info.dataWithPrefix.limit(nextStart);
            info.dataWithPrefix = info.dataWithPrefix.slice();
            info.data = newData.slice();
            // update position to point to the next element
            data.position(nextStart);
            return info;
        }
        catch (IllegalArgumentException ex) {
            throw new RLPException("The RLP byte array doesn't have enough space to hold an element with the specified length");
        }
    }

    private static int getLengthOfData(ByteBuffer data, byte lengthOfLength) {
        if (data.remaining() < 1 + lengthOfLength) {
            throw new RLPException("The length of the RLP item length can't possibly fit the data byte array");
        }

        ByteBuffer lenData = data.slice();
        lenData.position(1).limit(1 + lengthOfLength);

        long length = readBigEndianLong(lenData);

        if (Long.compareUnsigned(length, Integer.MAX_VALUE) > 0) {
            throw new RLPException("The current implementation doesn't support lengths longer than Integer.MAX_VALUE because that is the largest number of elements an array can have");
        }

        return Math.toIntExact(length);
    }

    private RLPElementView() {}

    private RLPElement createElement() {
        if (type == RLPElementType.NULL_ITEM) {
            byte[] item = ByteUtil.EMPTY_BYTE_ARRAY;
            return new RLPItem(item);
        } else if (type == RLPElementType.ITEM) {
            byte[] item = ByteBufferUtil.copyToArray(data);
            return new RLPItem(item);
        } else { // list
            byte[] rlpData = ByteBufferUtil.copyToArray(dataWithPrefix);
            RLPList newList = RLP.decodeList(rlpData);
            return newList;
        }
    }

    private static long readBigEndianLong(ByteBuffer data) {
        // 0-based
        data = data.slice();
        byte pow = (byte) (data.remaining() - 1);
        long length = 0;
        for (int i = 0; i < data.remaining(); i++) {
            length += Byte.toUnsignedLong(data.get(i)) << (8 * pow);
            pow--;
        }

        return length;
    }
}
