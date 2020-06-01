package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.light.LightClientMessageVisitor;
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

    public abstract void accept(LightClientMessageVisitor v);
}
