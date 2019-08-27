package co.rsk.rpc.modules.personal;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.core.Wallet;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.TransactionPool;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.facade.Ethereum;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class PersonalModuleWalletEnabledTest {

    Wallet wallet;
    PersonalModuleWalletEnabled module;

    @Before
    public void initialize() {
        this.wallet = new Wallet(new HashMapDB());
        this.module = new PersonalModuleWalletEnabled(
                mock(RskSystemProperties.class),
                mock(Ethereum.class),
                wallet,
                mock(TransactionPool.class)
        );
    }

    @Test
    public void newAccount_unlocks_account_immediately() {
        String address = this.module.newAccount("test");
        RskAddress rskAddress = new RskAddress(Hex.decode(address.substring(2)));
        Assert.assertNotNull(this.wallet.getAccount(rskAddress));
    }
}
