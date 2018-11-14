package co.rsk.metrics.block.profiler.detailed;

import java.util.ArrayList;

public class BlockProfilingInfo {

    private long blockId;
    private ArrayList<Metric> metrics;
    private int trxs;

    public BlockProfilingInfo(){
        this(-1,-1);
    }

    public BlockProfilingInfo(long blockId, int trxs){
        this.blockId = blockId;
        this.metrics = new ArrayList<>();
        this.trxs = trxs;
    }

    public ArrayList<Metric> getMetrics(){
        return  this.metrics;
    }
    public void setMetrics(ArrayList<Metric> metrics) {
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
