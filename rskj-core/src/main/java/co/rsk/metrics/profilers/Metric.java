package co.rsk.metrics.profilers;

import java.lang.management.ThreadMXBean;

public interface Metric {


    void setDelta(ThreadMXBean thread);
}
