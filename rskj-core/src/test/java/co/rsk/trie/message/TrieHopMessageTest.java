package co.rsk.trie.message;

import co.rsk.core.types.ints.Uint8;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieValueTest;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Date;

import static co.rsk.trie.Trie.*;
import static org.bouncycastle.util.encoders.Hex.decode;
import static org.junit.Assert.*;


// TODO(FEDEJINICH) STILL NEED TO FINISH THIS TESTS (RIGH NOW I'M IGNORING THEM ALL)

// todo(fedejinich) currently the serialization process has one error
//   it serializes childs with the old internalToMessage -> this will be fixed with a strategy
public class TrieHopMessageTest {

    private static final long TEST_TIMESTAMP = new Date().getTime();

    @Test @Ignore
    public void emptyTrieToMessageHop() {
        Trie trie = new Trie();

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        assertNotNull(message);

        ByteBuffer messageBuffer = ByteBuffer.wrap(message);

        // flags (1 byte) + lastRentPaidTimestamp (8 bytes)
        assertEquals(FLAGS_SIZE + TIMESTAMP_SIZE, message.length);

        // check flags (version)
        assertEquals((byte) 0b10000000, messageBuffer.get());

        // check rent timestamp
        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithLeftEmbeddedChildToMessageHop() {
        byte[] value1 = {1};
        byte[] value2 = {7};

        Trie trie = new Trie()
                .put(decode("0a"), value1)
                .put(decode("0a00"), value2);

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        assertArrayEquals(new byte[]{1}, trie.getValue());
        assertTrue(!trie.getLeft().isEmpty());
        assertTrue(trie.getLeft().isEmbeddable());
        assertTrue(trie.getLeft().getNode().get().isTerminal());
        assertTrue(trie.getRight().isEmpty());
        assertArrayEquals(new byte[]{7}, trie.getLeft().getNode().get().getValue());

        byte[] message = trie.toMessage();

        assertNotNull(message);

        // flags + lastRentPaidTimestamp + leftEmbeddedChildLength + leftEmbeddedChild + value
        assertEquals(FLAGS_SIZE + TIMESTAMP_SIZE + EMBEDDED_CHILD_LENGTH_SIZE + 7 + value1.length
                , message.length);

        ByteBuffer messageBuffer = ByteBuffer.wrap(message);

        // check flags (version + lshared + left + leftEmbedded)
        assertEquals((byte) 0b10000000 | 0b00010000 | 0b00001000 | 0b00000010, messageBuffer.get());

        // check rent timestamp
        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        byte[] leftChildLengthBuffer = new byte[Uint8.BYTES];
        messageBuffer.get(leftChildLengthBuffer);
        Uint8 embeddedLeftChildLength = Uint8.decode(leftChildLengthBuffer, 0);

        // check left embedChild
        byte[] embeddedLeftChild = new byte[embeddedLeftChildLength.intValue()];
        messageBuffer.get(embeddedLeftChild);
        assertEquals(7, embeddedLeftChild.length);
        assertArrayEquals(new byte[]{7}, Trie.fromMessage(embeddedLeftChild, null)
                .getLeft().getNode().get().getValue()); // todo(fedejinich) is this ok ?

        // check value
        byte[] value = new byte[messageBuffer.remaining()];
        messageBuffer.get(value);
        assertArrayEquals(new byte[]{1}, value);

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithLeftChildToMessageHop() {
        Trie rootNode = new Trie()
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a01"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a0110"), TrieValueTest.makeValue(LONG_VALUE - 1));

//        rootNode = rootNode.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        assertTrue(!rootNode.getLeft().isEmpty());
        assertTrue(rootNode.getRight().isEmpty());
        assertTrue(!rootNode.getLeft().isEmbeddable());

        Trie intermediateNode = rootNode.getLeft().getNode().get();
        assertTrue(!intermediateNode.isTerminal());

        assertTrue(!intermediateNode.getLeft().isEmpty());
        assertTrue(intermediateNode.getRight().isEmpty());
        assertTrue(intermediateNode.getLeft().isEmbeddable());

        Trie leafNode = intermediateNode.getLeft().getNode().get();
        assertTrue(leafNode.isTerminal());

        assertEquals(TEST_TIMESTAMP, rootNode.getLastRentPaidTimestamp());

        byte[] message = rootNode.toMessage();

        assertNotNull(message);

        // flags + lastRentPaidTimestamp + leftChildHashLength + value
        assertEquals(FLAGS_SIZE + TIMESTAMP_SIZE + CHILD_HASH_SIZE
                , message.length);

        assertEquals(68, message.length); // todo(fedejinich) why?

        ByteBuffer messageBuffer = ByteBuffer.wrap(message);

        // check flags (version + lshared + left) => 0b10000000 | 0b00010000 | 0b00001000
        assertEquals(0b10011000, messageBuffer.get());

        // check rent timestamp
        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        // check left node hash

        // todo(fedejinich) here I should check the left intermediate node and also the left leaf node

        assertEquals(rootNode, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithEmbeddedLeftRightChildsToMessageHop() {
        Trie trie = new Trie()
                .put(decode("1a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("1a10"), TrieValueTest.makeValue(LONG_VALUE - 1));

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        assertTrue(trie.getLeft().isEmbeddable());
        assertTrue(trie.getLeft().getNode().get().isTerminal());
        assertFalse(trie.getRight().isEmbeddable());
        assertFalse(trie.getRight().getNode().get().isTerminal());
        assertEquals(TEST_TIMESTAMP, trie.getLastRentPaidTimestamp());

        byte[] message = trie.toMessage();

        assertNotNull(message);
        assertEquals(72, message.length); // todo(fedejinich) why?

        // check flags (version + lshared + left + leftEmbedded + right) => 0b10000000 | 0b00010000 | 0b00001000 | 0b00000010 | 0b00000100
        assertEquals(0b10011110, message[0]);

        // check rent timestamp
//        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithLeftEmbeddedRightChildsToMessageHop() {
        Trie trie = new Trie()
                .put(decode("1a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a"), TrieValueTest.makeValue(LONG_VALUE - 1))
                .put(decode("0a10"), TrieValueTest.makeValue(LONG_VALUE - 1));

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        assertFalse(trie.getLeft().isEmbeddable());
        assertFalse(trie.getLeft().getNode().get().isTerminal());
        assertTrue(trie.getRight().isEmbeddable());
        assertTrue(trie.getRight().getNode().get().isTerminal());

        byte[] message = trie.toMessage();

        assertNotNull(message);
        assertEquals(72, message.length); // todo(fedejinich) why?

        // check flags (version + lshared + left + right + rightEmbedded) => 0b10000000 | 0b00010000 | 0b00001000 | 0b00000100 | 0b00000001
        assertEquals(0b10011101, message[0]);

        // check rent timestamp
//        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithBothEmbeddedChildsToMessageHop() {
        Trie trie = new Trie()
                .put(decode("0a"), new byte[]{1})
                .put(decode("10"), new byte[]{9});

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        assertNotNull(message);
        assertEquals(14, message.length); // todo(fedejinich) why?

        // check flags (version + lshared + left + leftEmbedded + right + rightEmbedded) => 0b10000000 | 0b00010000 | 0b00001000 | 0b00000010
        assertEquals(0b10011111, message[0]);

        // check rent timestamp
//        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithValueToMessageHop() {
        Trie trie = new Trie().put(new byte[0], new byte[]{1, 2, 3, 4});

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        assertNotNull(message);

        // flags (1 byte) + value (4 bytes)
        assertEquals(5, message.length);

        // check flags
        assertEquals(0b10000000, message[0]);

        // check rent timestamp
//        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());

        // check value
        assertEquals(1, message[1]);
        assertEquals(2, message[2]);
        assertEquals(3, message[3]);
        assertEquals(4, message[4]);

        assertEquals(trie, Trie.fromMessage(message, null));
    }

    @Test @Ignore
    public void trieWithLongValueToMessageHop() {
        Trie trie = new Trie().put(new byte[0], TrieValueTest.makeValue(LONG_VALUE));

//        trie = trie.setLastRentPaidTimestamp(TEST_TIMESTAMP);

        byte[] message = trie.toMessage();

        assertNotNull(message);

        // flags (1 byte) + valueHash (32 bytes) + valueLength (3 bytes)
        assertEquals(36, message.length);

        // check flags => 0b10100000 | 0b00100000 todo(fedejinich) checkout this
        assertEquals(0b10100000, message[0]);

        // check rent timestamp
//        assertEquals(TEST_TIMESTAMP, new Date(messageBuffer.getLong()).getTime());
//
        // check encoded valueHash
        byte[] valueHash = trie.getValueHash().getBytes();
        for (int k = 0; k < valueHash.length; k++) {
            assertEquals(valueHash[k], message[k + 1]); // the first byte corresponds to flags
        }

        // check value length
//        assertEquals(new Uint24(LONG_VALUE), Uint24.decode(new byte[]{message[33], message[34], message[35]}, 0));

        assertEquals(trie, Trie.fromMessage(message, null));
    }
}
