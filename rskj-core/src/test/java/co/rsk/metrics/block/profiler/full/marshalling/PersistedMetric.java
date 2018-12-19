package co.rsk.metrics.block.profiler.full.marshalling;

public class PersistedMetric {

    private int type;
    private long st; //System time in nanoseconds
    private long gCt; //Garbage Collector time in milliseconds converted to nanoseconds
    private long thCPUt; //Thread CPU Time in nanoseconds
    private long numOfEvents; //Times this metric has been called

    public PersistedMetric(){

    }


    //We don't unnecessarily store a reference to the ThreadMXBean instance on each metric object
    public PersistedMetric(int type, long st, long gCt, long thCPUt){
        this.type = type;
        this.st = st;
        this.gCt = gCt;
        this.thCPUt = thCPUt;
        this.numOfEvents = 0;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
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

    public long getNumOfEvents() {
        return numOfEvents;
    }

    public void setNumOfEvents(long numOfEvents) {
        this.numOfEvents = numOfEvents;
    }

    public long getThCPUt() {
        return thCPUt;
    }

    public void setThCPUt(long thCPUt) {
        this.thCPUt = thCPUt;
    }
}
