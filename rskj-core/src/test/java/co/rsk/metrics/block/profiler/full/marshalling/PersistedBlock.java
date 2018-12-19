package co.rsk.metrics.block.profiler.full.marshalling;

import java.util.ArrayList;

public class PersistedBlock {

    private long blockId;
    private ArrayList<PersistedMetric> metrics;
    private PersistedBlockMetric blockConnection;
    private int trxs;

    public PersistedBlock(){
        this(-1,-1);
    }

    public PersistedBlock(long blockId, int trxs){
        this.blockId = blockId;
        this.metrics = new ArrayList<>();
        this.blockConnection = null;
        this.trxs = trxs;
    }

    public ArrayList<PersistedMetric> getMetrics(){
        return  this.metrics;
    }
    public void setMetrics(ArrayList<PersistedMetric> metrics) {
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

    public PersistedBlockMetric getBlockConnection() {
        return blockConnection;
    }

    public void setBlockConnection(PersistedBlockMetric blockConnection) {
        this.blockConnection = blockConnection;
    }
}
