package co.rsk.net.rlpx;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.light.message.TestMessage;
import org.ethereum.net.message.Message;

public class LCMessageFactory {
    public Message create(byte code, byte[] encoded) {

        LightClientMessageCodes receivedCommand = LightClientMessageCodes.fromByte(code);
        switch (receivedCommand) {
            case TEST:
                return new TestMessage(encoded);
            default:
                throw new IllegalArgumentException("No such message");
        }
    }
}
