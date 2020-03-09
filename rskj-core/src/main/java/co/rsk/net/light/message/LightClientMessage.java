package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import org.ethereum.net.message.Message;

public abstract class LightClientMessage extends Message {

    public LightClientMessage() {
    }

    public LightClientMessage(byte[] encoded) {
        super(encoded);
    }

    public LightClientMessageCodes getCommand() {
        return LightClientMessageCodes.fromByte(code);
    }
}
