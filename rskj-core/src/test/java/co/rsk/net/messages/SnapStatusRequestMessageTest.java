package co.rsk.net.messages;

import org.ethereum.util.RLP;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

class SnapStatusRequestMessageTest {
    @Test
    void getMessageType_returnCorrectMessageType() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();

        //when
        MessageType messageType = message.getMessageType();

        //then
        assertThat(messageType, equalTo(MessageType.SNAP_STATUS_REQUEST_MESSAGE));
    }

    @Test
    void getEncodedMessage_returnExpectedByteArray() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        byte[] expectedEncodedMessage = RLP.encodedEmptyList();
        //when
        byte[] encodedMessage = message.getEncodedMessage();

        //then
        assertThat(encodedMessage, equalTo(expectedEncodedMessage));
    }

    @Test
    void givenAcceptIsCalled_messageVisitorIsAppliedForMessage() {
        //given
        SnapStatusRequestMessage message = new SnapStatusRequestMessage();
        MessageVisitor visitor = mock(MessageVisitor.class);

        //when
        message.accept(visitor);

        //then
        verify(visitor, times(1)).apply(message);
    }
}