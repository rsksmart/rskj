package co.rsk.metrics.block.profiler.full;

import co.rsk.metrics.profilers.Profiler;

import java.lang.management.ThreadMXBean;

public class BlockConnectionMetric extends MetricImpl {

    private long ramAtStart;
    private long ramAtEnd;

    //We don't unnecessarily store a reference to the ThreadMXBean instance on each metric object
    public BlockConnectionMetric(Profiler.PROFILING_TYPE type, ThreadMXBean thread){
        super(type, thread);
        ramAtStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }


    public void setDelta(ThreadMXBean thread){
        super.setDelta(thread);
        ramAtEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public long getRamAtStart() {
        return ramAtStart;
    }

    public long getRamAtEnd() {
        return ramAtEnd;
    }

}
