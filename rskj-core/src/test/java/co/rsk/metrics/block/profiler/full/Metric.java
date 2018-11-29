package co.rsk.metrics.block.profiler.full;

import co.rsk.metrics.profilers.Profiler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

public class Metric implements co.rsk.metrics.profilers.Metric {

    private int type;
    private long st; //System time in nanoseconds
    private long gCt; //Garbage Collector time in milliseconds converted to nanoseconds
    private long thCPUt; //Thread CPU Time in nanoseconds
    private boolean stopped;
    private long ramAtStart;
    private long ramAtEnd;


    public Metric(){
        type = -1;
        st = -1;
        gCt = -1;
        thCPUt = -1;
        stopped = false;
    }


    //We don't unnecessarily store a reference to the ThreadMXBean instance on each metric object
    public Metric(Profiler.PROFILING_TYPE type, ThreadMXBean thread){
        this.type = type.ordinal();
        st = System.nanoTime();
        gCt = getGarbageCollectorTimeMillis();
        thCPUt = thread.getCurrentThreadCpuTime();
        this.stopped= false;
        ramAtStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
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
        ramAtEnd = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public int getType(){
        return this.type;
    }

    public void setType(Profiler.PROFILING_TYPE type) {
        this.type = type.ordinal();
    }

    public void setType(int type){
        this.type = type;
    }

    public long getSt() {
        return st;
    }

    public void setSt(long st) {
        this.st = st;
    }

    public long getgCt() {
        return gCt;
    }

    public void setgCt(long gCt) {
        this.gCt = gCt;
    }

    public long getRamAtStart() {
        return ramAtStart;
    }

    public void setRamAtStart(long ramAtStart) {
        this.ramAtStart = ramAtStart;
    }

    public long getRamAtEnd() {
        return ramAtEnd;
    }

    public void setRamAtEnd(long ramAtEnd) {
        this.ramAtEnd = ramAtEnd;
    }

    public long getThCPUt() {
        return thCPUt;
    }

    public void setThCPUt(long thCPUt) {
        this.thCPUt = thCPUt;
    }
}
