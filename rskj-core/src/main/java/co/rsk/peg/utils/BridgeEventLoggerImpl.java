package co.rsk.peg.utils;

import co.rsk.peg.Bridge;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
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

    private List<LogInfo> logs;

    public BridgeEventLoggerImpl(List<LogInfo> logs) {
        this.logs = logs;
    }

    public List<LogInfo> getLogs() {
        return logs;
    }
}
