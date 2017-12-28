package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.vm.LogInfo;

import java.util.List;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public interface BridgeEventLogger {

    List<LogInfo> getLogs();

    void logUpdateCollections(Transaction rskTx);

    void losAddSignature(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash);

    void logReleaseBtc(BtcTransaction btcTx);
}
