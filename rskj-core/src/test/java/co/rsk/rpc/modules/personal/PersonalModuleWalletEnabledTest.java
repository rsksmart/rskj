package co.rsk.rpc.modules.personal;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by Nazaret García on 15/01/2021
 */

public class PersonalModuleWalletEnabledTest {

    @Test(expected = DecoderException.class)
    public void importRawKey_KeyIsNull_ThrowsNullPointerException() {
        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(null);

        personalModuleWalletEnabled.importRawKey(null, "passphrase1");
    }

    @Test
    public void importRawKey_KeyContains0xPrefix_OK() {
        ECKey eckey = new ECKey();
        String rawKey = ByteUtil.toHexString(eckey.getPrivKeyBytes());
        String passphrase = "passphrase1";
        byte[] hexDecodedKey = Hex.decode(rawKey);

        RskAddress addressMock = mock(RskAddress.class);
        doReturn("{}").when(addressMock).toJsonString();

        Wallet walletMock = mock(Wallet.class);
        doReturn(addressMock).when(walletMock).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        doReturn(true).when(walletMock).unlockAccount(eq(addressMock), eq(passphrase), any(Long.class));

        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(walletMock);
        String result = personalModuleWalletEnabled.importRawKey(String.format("0x%s", rawKey), passphrase);

        verify(walletMock, times(1)).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        verify(walletMock, times(1)).unlockAccount(addressMock, passphrase, 1800000L);
        verify(addressMock, times(1)).toJsonString();

        assertEquals("{}", result);
    }

    @Test
    public void importRawKey_KeyDoesNotContains0xPrefix_OK() {
        ECKey eckey = new ECKey();
        String rawKey = ByteUtil.toHexString(eckey.getPrivKeyBytes());
        String passphrase = "passphrase1";
        byte[] hexDecodedKey = Hex.decode(rawKey);

        RskAddress addressMock = mock(RskAddress.class);
        doReturn("{}").when(addressMock).toJsonString();

        Wallet walletMock = mock(Wallet.class);
        doReturn(addressMock).when(walletMock).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        doReturn(true).when(walletMock).unlockAccount(eq(addressMock), eq(passphrase), any(Long.class));

        PersonalModuleWalletEnabled personalModuleWalletEnabled = createPersonalModuleWalletEnabled(walletMock);
        String result = personalModuleWalletEnabled.importRawKey(rawKey, passphrase);

        verify(walletMock, times(1)).addAccountWithPrivateKey(hexDecodedKey, passphrase);
        verify(walletMock, times(1)).unlockAccount(addressMock, passphrase, 1800000L);
        verify(addressMock, times(1)).toJsonString();

        assertEquals("{}", result);
    }

    private PersonalModuleWalletEnabled createPersonalModuleWalletEnabled(Wallet wallet) {
        return new PersonalModuleWalletEnabled(null, null, wallet, null);
    }

}
