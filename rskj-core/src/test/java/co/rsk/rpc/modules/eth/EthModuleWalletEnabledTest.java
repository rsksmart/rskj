package co.rsk.rpc.modules.eth;

import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.ethereum.core.Account;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EthModuleWalletEnabledTest {

    @Mock
    private Wallet wallet;

    @Test
    public void testSignToReturnAlways32BytesLength() {
        ECKey ecKey = mock(ECKey.class);

        Account account = mock(Account.class);
        when(account.getEcKey()).thenReturn(ecKey);

        when(wallet.getAccount(any())).thenReturn(account);

        EthModuleWallet ethModuleWallet = new EthModuleWalletEnabled(wallet);

        when(ecKey.sign(any())).thenReturn(new ECKey.ECDSASignature(
                new BigInteger("90799205472826917840242505107457993089603477280876640922171931138596850540969"),
                new BigInteger("12449423892652054473462673837036123325448979032544381124854758290795038162079"))
        );
        String signature1 = ethModuleWallet.sign(new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7").toHexString(), ByteUtil.toHexString("whatever".getBytes()));
        Assert.assertEquals("0xc8be87722c6452172a02a62fdea70c8b25cfc9613d28647bf2aeb3c7d1faa1a91b861fccc05bb61e25ff4300502812750706ca8df189a0b8163540b9bccabc9f00", signature1
        );

        when(ecKey.sign(any())).thenReturn(new ECKey.ECDSASignature(
                new BigInteger("1"),
                new BigInteger("1"))
        );
        String signature2 = ethModuleWallet.sign(new RskAddress("b86ca7db8c7ae687ac8d098789987eee12333fc7").toHexString(), ByteUtil.toHexString("whatever".getBytes()));
        Assert.assertEquals("0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000100", signature2);

    }

}
