package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class CodeResponseTest {

//    private static byte[] codeHash = HashUtil.randomHash();
//    private static CodeResponseMessage message;
//
//    @Before
//    public void setUp() {
//        message = new CodeResponseMessage(100, codeHash);
//    }
//
//    @Test
//    public void createMessage() {
//        assertEquals(100, message.getId());
//        assertArrayEquals(codeHash, message.getCodeHash());
//        assertEquals(MessageType.CODE_RESPONSE_MESSAGE, message.getMessageType());
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