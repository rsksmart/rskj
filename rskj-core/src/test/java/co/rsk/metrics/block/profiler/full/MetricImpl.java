package co.rsk.metrics.block.profiler.full;

import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class MetricImpl implements Metric {

    private int type;
    private long st; //System time in nanoseconds
    private long gCt; //Garbage Collector time in milliseconds converted to nanoseconds
    private long thCPUt; //Thread CPU Time in nanoseconds
    private boolean stopped;


    public MetricImpl(){
        type = -1;
        st = -1;
        gCt = -1;
        thCPUt = -1;
        stopped = false;
    }


    //We don't unnecessarily store a reference to the ThreadMXBean instance on each metric object
    public MetricImpl(Profiler.PROFILING_TYPE type, ThreadMXBean thread){
        this.type = type.ordinal();
        st = System.nanoTime();
        gCt = getGarbageCollectorTimeMillis();
        thCPUt = thread.getCurrentThreadCpuTime();
        this.stopped= false;
    }


    public boolean isStopped(){
        return this.stopped;
    }

    private long getGarbageCollectorTimeMillis() {
        long t = 0;
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();

        for (GarbageCollectorMXBean gc : gcs) {
            t += gc.getCollectionTime();
        }
        return t;
    }

    public void setDelta(ThreadMXBean thread){
        stopped = true;

        st = System.nanoTime() - st;
        gCt = (getGarbageCollectorTimeMillis() - gCt) * 1000000;
        thCPUt = thread.getCurrentThreadCpuTime() - thCPUt;
    }

    public int getType(){
        return this.type;
    }

    public long getSt() {
        return st;
    }

    public long getgCt() {
        return gCt;
    }

    public long getThCPUt() {
        return thCPUt;
    }
}
