package co.rsk.net.messages;

import org.ethereum.crypto.HashUtil;
import org.junit.Test;

import java.math.BigInteger;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class AccountResponseMessageTest {

    @Test
    public void createMessage() {
        long id = 0;
        byte[] merkleProof = new byte[]{0x0F};
        byte[] nonce = new byte[]{0x0F};
        byte[] balance = new byte[]{0x0F, (byte) 0xFF};
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = new byte[]{0x0F, 0x0A, 0x0F};

        AccountResponseMessage message = new AccountResponseMessage(id, merkleProof, nonce, balance, codeHash, storageRoot);

        assertThat(message.getId(), is(id));
        assertThat(message.getMerkleProof(), is(merkleProof));
        assertThat(message.getNonce(), is(nonce));
        assertThat(message.getBalance(), is(balance));
        assertThat(message.getCodeHash(), is(codeHash));
        assertThat(message.getStorageRoot(), is(storageRoot));
        assertThat(message.getMessageType(), is(MessageType.ACCOUNT_RESPONSE_MESSAGE));
    }

    @Test
    public void accept() {
        long id = 0;
        byte[] merkleProof = new byte[]{0x0F};
        byte[] nonce = new byte[]{0x42};
        byte[] balance = new byte[]{0x04, 0x02};
        byte[] codeHash = HashUtil.randomHash();
        byte[] storageRoot = new byte[]{0x0F, 0x0A, 0x0F};

        AccountResponseMessage message = new AccountResponseMessage(id, merkleProof, nonce, balance, codeHash, storageRoot);

        MessageVisitor visitor = mock(MessageVisitor.class);

        message.accept(visitor);

        verify(visitor, times(1)).apply(message);
    }

}