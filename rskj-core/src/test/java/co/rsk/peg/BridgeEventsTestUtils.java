package co.rsk.peg;

import org.ethereum.core.CallTransaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BridgeEventsTestUtils {

    public static List<DataWord> getEncodedTopics(CallTransaction.Function bridgeEvent, Object... args) {
        byte[][] encodedTopicsInBytes = bridgeEvent.encodeEventTopics(args);
        return LogInfo.byteArrayToList(encodedTopicsInBytes);
    }

    public static byte[] getEncodedData(CallTransaction.Function bridgeEvent, Object... args) {
        return bridgeEvent.encodeEventData(args);
    }

    public static Optional<LogInfo> getLogsTopics(List<LogInfo> logs, List<DataWord> expectedTopics) {
        return logs.stream()
            .filter(log -> log.getTopics().equals(expectedTopics))
            .findFirst();
    }

    public static Optional<LogInfo> getLogsData(List<LogInfo> logs, byte[] expectedData) {
        return logs.stream()
            .filter(log -> Arrays.equals(log.getData(), expectedData))
            .findFirst();
    }
}
