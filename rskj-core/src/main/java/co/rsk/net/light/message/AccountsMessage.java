package co.rsk.net.light.message;

public class AccountsMessage extends LightClientMessage {


    public AccountsMessage(byte[] encoded) {
    }

    @Override
    public byte[] getEncoded() {
        return new byte[0];
    }

    @Override
    public Class<?> getAnswerMessage() {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }
}
