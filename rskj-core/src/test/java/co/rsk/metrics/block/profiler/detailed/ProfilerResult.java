package co.rsk.metrics.block.profiler.detailed;

import co.rsk.metrics.block.profiler.ProfilingException;
import co.rsk.metrics.profilers.Profiler;

import java.util.ArrayList;
import java.util.Vector;

public class ProfilerResult {

    ArrayList<BlockProfilingInfo> blocksInfo;
    Float blockExecutionTime;
    Float averageBlockExecutionTime;
    String reportName;




    public ProfilerResult(){
        this(new ArrayList<>());
    }

    public ProfilerResult(ArrayList<BlockProfilingInfo> blocksInfo){
        this.blocksInfo = blocksInfo;
        blockExecutionTime = -1.0F;
        this.reportName = "";

    }

    public ArrayList<BlockProfilingInfo> getBlocksInfo() {
        return blocksInfo;
    }

    public void setBlocksInfo(ArrayList<BlockProfilingInfo> blocksInfo) {
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

            for(BlockProfilingInfo currentBlockinfo : blocksInfo){
                for(Metric currentMetric : currentBlockinfo.getMetrics()){
                    long time = currentMetric.getSt()/runs;
                    currentMetric.setSt(time);

                    time = currentMetric.getgCt()/runs;
                    currentMetric.setgCt(time);

                    time = currentMetric.getThCPUt()/runs;
                    currentMetric.setThCPUt(time);
                }
            }
        }
        else{
            //Blocks are ordered in ascending block Number
            for(int i = 0; i < playerRun.blocksInfo.size(); i++){

                BlockProfilingInfo currentBlockInfo = blocksInfo.get(i);
                BlockProfilingInfo newBlockInfo = playerRun.blocksInfo.get(i);

                if(currentBlockInfo.getBlockId() != newBlockInfo.getBlockId()){
                    throw new ProfilingException("Blocks not in order");
                }

                ArrayList<Metric> currentMetrics = blocksInfo.get(i).getMetrics();
                ArrayList<Metric> newMetrics = playerRun.blocksInfo.get(i).getMetrics();

                for(int j = 0; j < newMetrics.size(); j++){
                    Metric currentMetric = currentMetrics.get(j);
                    Metric newMetric = newMetrics.get(j);

                    //Metrics must be in order
                    if(currentMetric.getType() != newMetric.getType()){
                        throw new ProfilingException("Metric types don't match");
                    }
                    long time = currentMetric.getSt() + newMetric.getSt()/runs;
                    currentMetric.setSt(time);

                    time = currentMetric.getgCt() + newMetric.getgCt()/runs;
                    currentMetric.setgCt(time);

                    time = currentMetric.getThCPUt() + newMetric.getThCPUt()/runs;
                    currentMetric.setThCPUt(time);


                }
            }
        }

    }

    private void updateBlockExecutionTime() throws ProfilingException {
        blockExecutionTime = 0.0F;

        for(BlockProfilingInfo blockInfo : this.blocksInfo){
            for(Metric metric : blockInfo.getMetrics()){
                if(metric.getType() == Profiler.PROFILING_TYPE.BLOCK_EXECUTE.ordinal()){
                    blockExecutionTime+= metric.getSt();
                    break;
                }
            }
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
