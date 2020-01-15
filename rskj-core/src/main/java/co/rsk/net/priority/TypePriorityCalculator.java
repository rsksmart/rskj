package co.rsk.net.priority;

import co.rsk.net.MessageTask;

public class TypePriorityCalculator implements PriorityCalculator {

    @Override
    public Double calculate(MessageTask m) {
        return m.getMessage().accept(new TypePriorityVisitor());
    }
}
