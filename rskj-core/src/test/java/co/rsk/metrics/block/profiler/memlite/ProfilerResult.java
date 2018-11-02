package co.rsk.metrics.block.profiler.memlite;

import co.rsk.metrics.block.profiler.ProfilingException;
import co.rsk.metrics.profilers.Profiler;

import java.util.Vector;

public class ProfilerResult {

    Vector<BlockProfilingInfo> blocksInfo;
    Float blockExecutionTime;
    Float averageBlockExecutionTime;
    String reportName;




    public ProfilerResult(){
        this(new Vector<>());
    }

    public ProfilerResult(Vector<BlockProfilingInfo> blocksInfo){
        this.blocksInfo = blocksInfo;
        blockExecutionTime = -1.0F;
        this.reportName = "";

    }

    public Vector<BlockProfilingInfo> getBlocksInfo() {
        return blocksInfo;
    }

    public void setBlocksInfo(Vector<BlockProfilingInfo> blocksInfo) {
        this.blocksInfo = blocksInfo;
    }


    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public void aggregate(ProfilerResult playerRun, int runs) throws ProfilingException {

        if(this.blocksInfo.size() == 0){
            this.blocksInfo = playerRun.blocksInfo;
        }
        else{
            //Blocks are ordered in ascending block Number
            for(int i = 0; i < playerRun.blocksInfo.size(); i++){

                BlockProfilingInfo currentBlockInfo = blocksInfo.get(i);
                BlockProfilingInfo newBlockInfo = playerRun.blocksInfo.get(i);

                if(currentBlockInfo.getBlockId() != newBlockInfo.getBlockId()){
                    throw new ProfilingException("Blocks not in order");
                }

              /*  Vector<Metric> currentMetrics = blocksInfo.get(i).getMetrics();
                Vector<Metric> newMetrics = playerRun.blocksInfo.get(i).getMetrics();

                for(int j = 0; j < newMetrics.size(); j++){
                    Metric currentMetric = currentMetrics.get(j);
                    Metric newMetric = newMetrics.get(j);

                    //Metrics must be in order
                    if(!currentMetric.getType().equals(newMetric.getType())){
                        throw new ProfilingException("Metric types don't match");
                    }
                    long time = currentMetric.getTime() + newMetric.getTime()/runs;
                    currentMetric.setTime(time);
                }*/
            }
        }

    }

    private void updateBlockExecutionTime() throws ProfilingException {
        blockExecutionTime = 0.0F;

        for(BlockProfilingInfo blockInfo : this.blocksInfo){
            /*for(Metric metric : blockInfo.getMetrics()){
                if(metric.getType().equals(Profiler.PROFILING_TYPE.BLOCK_EXECUTE)){
                    blockExecutionTime+= metric.getTime();
                    break;
                }
            }*/
        }

        averageBlockExecutionTime = blockExecutionTime/this.blocksInfo.size();
    }

    public Float getBlockExecutionTime() throws ProfilingException {

        if(blockExecutionTime == -1.0F){
            updateBlockExecutionTime();
        }

        return blockExecutionTime;
    }


    public void setBlockExecutionTime(Float blockExecutionTime) {
        this.blockExecutionTime = blockExecutionTime;
    }

    public Float getAverageBlockExecutionTime() throws ProfilingException {
        if(blockExecutionTime == -1.0F){
            updateBlockExecutionTime();
        }
        return averageBlockExecutionTime;
    }

    public void setAverageBlockExecutionTime(Float averageBlockExecutionTime) {
        this.averageBlockExecutionTime = averageBlockExecutionTime;
    }
}
