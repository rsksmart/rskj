package co.rsk.metrics.block.profiler.detailed;

import co.rsk.metrics.profilers.Profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class Metric {

    private Profiler.PROFILING_TYPE type;
    private long time; //System time in nanoseconds
    private long gcTime; //Garbage Collector time in milliseconds converted to nanoseconds
    private long threadCpuTime; //Thread CPU Time in nanoseconds


    public Metric(){
        type = null;
        time = -1;
    }


    //We don't unnecessarily store a reference to the ThreadMXBean instance on each metric object
    public Metric(Profiler.PROFILING_TYPE type, ThreadMXBean thread){
        this.type = type;
        time = System.nanoTime();
        gcTime = getGarbageCollectorTimeMillis();
        threadCpuTime = thread.getCurrentThreadCpuTime();
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
        time = System.nanoTime() - time;
        gcTime = (getGarbageCollectorTimeMillis() - gcTime) * 1000000;
        threadCpuTime = thread.getCurrentThreadCpuTime() - threadCpuTime;
    }

    public Profiler.PROFILING_TYPE getType(){
        return this.type;
    }

    public void setType(Profiler.PROFILING_TYPE type) {
        this.type = type;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTime(){
        return this.time;
    }

    public long getGcTime() {
        return gcTime;
    }

    public void setGcTime(long gcTime) {
        this.gcTime = gcTime;
    }

    public long getThreadCpuTime() {
        return threadCpuTime;
    }

    public void setThreadCpuTime(long threadCpuTime) {
        this.threadCpuTime = threadCpuTime;
    }
}
