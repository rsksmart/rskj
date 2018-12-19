package co.rsk.metrics.block.profiler.full;

import java.util.ArrayList;

public class BlockProfilingInfo {

    private long blockId;
    private ArrayList<MetricImpl> metrics;
    private BlockConnectionMetric blockConnectionInfo;
    private int trxs;

    public BlockProfilingInfo(){
        this(-1,-1);
    }

    public BlockProfilingInfo(long blockId, int trxs){
        this.blockId = blockId;
        this.metrics = new ArrayList<>();
        this.blockConnectionInfo = null;
        this.trxs = trxs;
    }

    public ArrayList<MetricImpl> getMetrics(){
        return  this.metrics;
    }
    public void setMetrics(ArrayList<MetricImpl> metrics) {
        this.metrics = metrics;
    }

    public BlockConnectionMetric getBlockConnectionInfo() {
        return blockConnectionInfo;
    }

    public void setBlockConnectionInfo(BlockConnectionMetric generalInfo) {
        this.blockConnectionInfo = generalInfo;
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
