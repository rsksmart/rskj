package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.LegacyAddress;
import co.rsk.bitcoinj.wallet.Wallet;
import java.io.IOException;
import java.util.List;

public interface WalletProvider {
    Wallet provide(BtcTransaction btcTx, List<LegacyAddress> addresses)  throws IOException;
}
