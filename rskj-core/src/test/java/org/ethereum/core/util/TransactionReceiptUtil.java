package org.ethereum.core.util;

import com.sun.istack.Nullable;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.LogInfo;

import java.util.List;
import java.util.stream.Collectors;

public class TransactionReceiptUtil extends TransactionReceipt {

    /**
     * Lists all events
     * */
    public static List<String> getEvents(TransactionReceipt transactionReceipt) {
        return transactionReceipt.getLogInfoList().stream().map(logInfo -> eventSignature(logInfo)).collect(Collectors.toList());
    }

    /**
     * Given a signature, counts how many times an event is contained
     *
     * @param eventSignature an event signature
     *
     * @return count
     * */
    public static int getEventCount(TransactionReceipt transactionReceipt, String eventSignature, @Nullable String[] eventTypeParams) {
        List<String> events = getEvents(transactionReceipt);

        return events.stream().filter(e -> isExpectedEventSignature(e, eventSignature, eventTypeParams)).collect(Collectors.toList()).size();
    }

    private static String eventSignature(LogInfo logInfo) {
        // The first topic usually consists on the signatureHash of the name of the event
        return logInfo.getTopics().get(0).toString();
    }

    private static boolean isExpectedEventSignature(String encodedEvent, String expectedEventSignature, String[] eventTypeParams) {
        CallTransaction.Function fun = eventTypeParams == null ?
                CallTransaction.Function.fromSignature(expectedEventSignature):
                CallTransaction.Function.fromSignature(expectedEventSignature, eventTypeParams);
        String encodedExpectedEvent = HashUtil.toPrintableHash(fun.encodeSignatureLong());

        return encodedEvent.equals(encodedExpectedEvent);
    }
}
