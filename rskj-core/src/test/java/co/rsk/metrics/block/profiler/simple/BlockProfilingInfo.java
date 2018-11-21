package co.rsk.metrics.block.profiler.simple;

import java.util.Vector;

public class BlockProfilingInfo {

    private long blockId;
    private Vector<Metric> metrics;
    private int trxs;

    public BlockProfilingInfo(){
        this(-1,-1);
    }

    public BlockProfilingInfo(long blockId, int trxs){
        this.blockId = blockId;
        this.metrics = new Vector<>();
        this.trxs = trxs;
    }

    public Vector<Metric> getMetrics(){
        return  this.metrics;
    }
    public void setMetrics(Vector<Metric> metrics) {
        this.metrics = metrics;
    }


    public long getBlockId() {
        return blockId;
    }

    public void setBlockId(long blockId) {
        this.blockId = blockId;
    }

    public int getTrxs() {
        return trxs;
    }

    public void setTrxs(int trxs) {
        this.trxs = trxs;
    }

}
