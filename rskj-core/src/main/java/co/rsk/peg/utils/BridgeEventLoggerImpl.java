package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.peg.Bridge;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import java.util.Collections;
import java.util.List;

/**
 * Responsible for logging events triggered by BridgeContract.
 *
 * @author martin.medina
 */
public class BridgeEventLoggerImpl implements BridgeEventLogger {

    private static final byte[] BRIDGE_CONTRACT_ADDRESS = TypeConverter.stringToByteArray(PrecompiledContracts.BRIDGE_ADDR);

    private List<LogInfo> logs;

    public BridgeEventLoggerImpl(List<LogInfo> logs) {
        this.logs = logs;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }

    public void logUpdateCollections(Transaction rskTx) {
        this.logs.add(
                new LogInfo(BRIDGE_CONTRACT_ADDRESS,
                            Collections.singletonList(Bridge.UPDATE_COLLECTIONS_TOPIC),
                            RLP.encodeElement(rskTx.getSender())
                )
        );
    }

    public void losAddSignature(BtcECKey federatorPublicKey, BtcTransaction btcTx, byte[] rskTxHash) {
        List<DataWord> topics = Collections.singletonList(Bridge.ADD_SIGNATURE_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()),
                RLP.encodeElement(federatorPublicKey.getPubKeyHash()),
                RLP.encodeElement(rskTxHash));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }

    public void logReleaseBtc(BtcTransaction btcTx) {
        List<DataWord> topics = Collections.singletonList(Bridge.RELEASE_BTC_TOPIC);
        byte[] data = RLP.encodeList(RLP.encodeString(btcTx.getHashAsString()),
                RLP.encodeElement(btcTx.bitcoinSerialize()));

        this.logs.add(new LogInfo(BRIDGE_CONTRACT_ADDRESS, topics, data));
    }
}
