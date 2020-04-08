package co.rsk.net.light.message;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.rlpx.LCMessageFactory;
import org.ethereum.core.BlockFactory;
import org.ethereum.crypto.HashUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class AccountsMessageTest {

    private long id;
    private byte[] merkleInclusionProof;
    private long nonce;
    private long balance;
    private byte[] codeHash;
    private byte[] storageRoot;

    @Before
    public void setUp() {
        id = 1;
        merkleInclusionProof = HashUtil.randomHash();
        nonce = 123;
        balance = 100;
        codeHash = HashUtil.randomHash();
        storageRoot = HashUtil.randomHash();
    }

    @Test
    public void messageCreationShouldBeCorrect() {
        AccountsMessage testMessage = new AccountsMessage(id, merkleInclusionProof,
                nonce, balance,
                codeHash, storageRoot);
        assertEquals(LightClientMessageCodes.ACCOUNTS, testMessage.getCommand());
        assertEquals(testMessage.getId(), id);
        assertArrayEquals(testMessage.getMerkleInclusionProof(), merkleInclusionProof);
        assertEquals(testMessage.getNonce(), nonce);
        assertEquals(testMessage.getBalance(), balance);
        assertArrayEquals(testMessage.getCodeHash(), codeHash);
        assertArrayEquals(testMessage.getStorageRoot(), storageRoot);
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {

        AccountsMessage testMessage = new AccountsMessage(id, merkleInclusionProof,
                nonce, balance,
                codeHash, storageRoot);
        byte[] encoded = testMessage.getEncoded();
        LCMessageFactory lcMessageFactory = new LCMessageFactory(mock(BlockFactory.class));
        byte code = LightClientMessageCodes.ACCOUNTS.asByte();
        AccountsMessage message = (AccountsMessage) lcMessageFactory.create(code, encoded);

        assertEquals(testMessage.getId(), message.getId());
        assertArrayEquals(testMessage.getMerkleInclusionProof(), message.getMerkleInclusionProof());
        assertEquals(testMessage.getNonce(), message.getNonce());
        assertEquals(testMessage.getBalance(), message.getBalance());
        assertArrayEquals(testMessage.getCodeHash(), message.getCodeHash());
        assertArrayEquals(testMessage.getStorageRoot(), message.getStorageRoot());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }
}
