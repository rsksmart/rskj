package co.rsk.peg.utils;

import org.ethereum.vm.LogInfo;

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
