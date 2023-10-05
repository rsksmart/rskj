package co.rsk.util;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class BytesSerializerTest {
    @Test
    void serializeAndDeserializeZero() {
        byte[] serializedZero = RLP.encodeBigInteger(BigInteger.valueOf(0));
        RLPItem rlpItem = (RLPItem)RLP.decode2(serializedZero).get(0);
        byte[] deserializedSerializedZeroInBytes = rlpItem.getRLPData();
        Assertions.assertNull(deserializedSerializedZeroInBytes);

        /*
        THIS DOES NOT WORK SINCE deserializedSerializedZeroInBytes IS NULL
        int deserializedSerializedZero = ByteBuffer.wrap(deserializedSerializedZeroInBytes).get();

        Assertions.assertEquals(0, deserializedSerializedZero);
        */
    }

    @Test
    void serializeAndDeserializeOne() {
        byte[] serializedOne = RLP.encodeBigInteger(BigInteger.valueOf(1));
        RLPItem rlpItem = (RLPItem)RLP.decode2(serializedOne).get(0);
        byte[] deserializedSerializedOneInBytes = rlpItem.getRLPData();
        int deserializedSerializedOne = ByteBuffer.wrap(deserializedSerializedOneInBytes).get();

        Assertions.assertEquals(1, deserializedSerializedOne);
    }
}
