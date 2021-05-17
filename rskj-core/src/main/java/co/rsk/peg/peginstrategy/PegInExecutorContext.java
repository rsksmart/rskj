package co.rsk.peg.peginstrategy;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.peg.PeginInformation;
import co.rsk.peg.RegisterBtcTransactionException;
import org.ethereum.core.Transaction;

import java.io.IOException;

/**
 * Created by Kelvin Isievwore on 11/05/2021.
 */
public class PegInExecutorContext {

    private PegInVersionStrategy peginVersionStrategy;

    public void setPeginVersion(PegInVersionStrategy peginVersionStrategy) {
        this.peginVersionStrategy = peginVersionStrategy;
    }

    public void executePeginTransaction(
            BtcTransaction btcTx,
            Transaction rskTx,
            int height,
            PeginInformation peginInformation,
            Coin totalAmount) throws IOException, RegisterBtcTransactionException {
        this.peginVersionStrategy.processPeginTransaction(btcTx, rskTx, height, peginInformation, totalAmount);
    }
}
