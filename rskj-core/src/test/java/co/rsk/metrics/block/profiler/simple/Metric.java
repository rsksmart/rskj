package co.rsk.metrics.block.profiler.simple;

import co.rsk.metrics.profilers.Profiler;

import java.lang.management.ThreadMXBean;

public class Metric implements co.rsk.metrics.profilers.Metric {

    private Profiler.PROFILING_TYPE type;
    private long time; //Value in nanoseconds

    public Metric(){
        type = null;
        time = -1;
    }

    public Metric(Profiler.PROFILING_TYPE type){
        this.type = type;

        time = System.nanoTime();
    }


    public void setDelta(ThreadMXBean thread){
        this.time = System.nanoTime() - this.time;
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

    public void aggregate(Metric newMetric){
        this.time+=newMetric.getTime();
    }

}
