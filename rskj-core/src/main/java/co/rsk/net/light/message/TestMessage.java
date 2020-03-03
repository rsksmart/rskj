package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.net.message.Message;
import org.ethereum.util.RLP;

public class TestMessage extends Message {

    public TestMessage() {
    }

    public TestMessage(byte[] encoded) {
        super(encoded);
    }

    @Override
    public byte[] getEncoded() {
        byte[] rlpInt = RLP.encodeInt(0);
        return RLP.encodeList(rlpInt);
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public LightClientMessageCodes getCommand() {
        return LightClientMessageCodes.TEST;
    }
}
