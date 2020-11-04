package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.wallet.Wallet;
import java.io.IOException;

public interface WalletProvider {
    Wallet provide(BtcTransaction btcTx, Address address) throws IOException;
}
