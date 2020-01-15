package co.rsk.net.priority;

import co.rsk.net.MessageTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

public class MessagePriority implements Comparator<MessageTask> {

    private static final Logger logger = LoggerFactory.getLogger("message_priority");
    private final PriorityCalculator typeCalc;
    private final PriorityCalculator avgTimeBetweenMessagesCalc;
    private final PriorityCalculator avgProcessingTimeCalc;

    public MessagePriority(PriorityCalculator typeCalc,
                           PriorityCalculator avgTimeBetweenMessagesCalc,
                           PriorityCalculator avgProcessingTimeCalc) {

        this.typeCalc = typeCalc;
        this.avgTimeBetweenMessagesCalc = avgTimeBetweenMessagesCalc;
        this.avgProcessingTimeCalc = avgProcessingTimeCalc;
    }

    @Override
    public int compare(MessageTask o1, MessageTask o2) {
        return Double.compare(evaluate(o1), evaluate(o2));
    }

    private double evaluate(MessageTask m) {
        Double type = typeCalc.calculate(m);
        Double dAvg = avgTimeBetweenMessagesCalc.calculate(m);
        Double cAvg = avgProcessingTimeCalc.calculate(m);

        double total = type * dAvg / cAvg;
        return total;
    }

    public double evaluate2(MessageTask m) {
        Double type = typeCalc.calculate(m);
        Double dAvg = avgTimeBetweenMessagesCalc.calculate(m);
        Double cAvg = avgProcessingTimeCalc.calculate(m);

        double total = type * dAvg / cAvg;
        logger.trace("[priority][message_type: {}][peer: {}][type: {}][dAvg: {}][cAvg: {}][total: {}]",
                m.getMessage().getMessageType(), m.getSender().getPeerNodeID(), type, dAvg, cAvg, total);

        return total;
    }
}
