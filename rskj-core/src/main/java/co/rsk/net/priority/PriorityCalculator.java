package co.rsk.net.priority;

import co.rsk.net.MessageTask;

public interface PriorityCalculator {
    Double calculate(MessageTask m);
}
