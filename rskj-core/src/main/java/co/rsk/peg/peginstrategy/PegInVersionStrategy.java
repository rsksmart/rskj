package co.rsk.peg.peginstrategy;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.RegisterBtcTransactionException;
import org.ethereum.core.Transaction;

import java.io.IOException;

/**
 * Common interface for all Peg In strategies.
 *
 * Created by Kelvin Isievwore on 11/05/2021.
 */
public interface PegInVersionStrategy {

    void processPeginTransaction(BtcTransaction btcTx,
                                 Transaction rskTx,
                                 int height,
                                 PeginInformation peginInformation,
                                 Coin totalAmount) throws RegisterBtcTransactionException, IOException;
}
