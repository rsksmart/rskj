package co.rsk.net.messages;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import org.ethereum.TestUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CodeRequestMessageTest {
    private static CodeRequestMessage message;
    private byte[] blockHashBytes = TestUtils.randomBytes(32);
    private byte[] addressHashBytes = TestUtils.randomBytes(20);

//    @Before
//    public void setUp() {
//        byte[] keccak256 = new Keccak256(blockHashBytes).getBytes();
//        byte[] rskAddress = new RskAddress(addressHashBytes).getBytes();
//        message = new CodeRequestMessage(100, keccak256, rskAddress);
//    }
//    @Test
//    public void createMessage() {
//        assertEquals(100, message.getId());
//        assertArrayEquals(blockHashBytes, message.getBlockHash());
//        assertArrayEquals(addressHashBytes, message.getAddress());
//        assertEquals(MessageType.CODE_REQUEST_MESSAGE, message.getMessageType());
//    }
//
//    @Test
//    public void accept() {
//        MessageVisitor visitor = mock(MessageVisitor.class);
//
//        message.accept(visitor);
//
//        verify(visitor, times(1)).apply(message);
//    }
}
