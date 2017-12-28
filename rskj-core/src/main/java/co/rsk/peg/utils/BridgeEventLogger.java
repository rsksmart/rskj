package co.rsk.peg.utils;

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
}
