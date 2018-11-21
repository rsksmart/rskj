package co.rsk.metrics.block.profiler.simple;

import co.rsk.metrics.block.profiler.ProfilingException;
import co.rsk.metrics.profilers.Profiler;

import java.util.*;

public class ProfilerResult {

    Vector<BlockProfilingInfo> blocksInfo;
    String reportName;




    public ProfilerResult(){
        this(new Vector<>());
    }

    public  ProfilerResult(Vector<BlockProfilingInfo> blocksInfo){
        this.blocksInfo = blocksInfo;
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

    public void compress(){
        //TODO:  Offer a data compression mechanism for this profiler to do offline so no time is wasted during profiling
        for(BlockProfilingInfo blockInfo: blocksInfo){
            Map<Profiler.PROFILING_TYPE, Metric> compressedMetrics = new HashMap<>();

            for(Metric metric: blockInfo.getMetrics()){
                if(!compressedMetrics.containsKey(metric.getType())){
                    compressedMetrics.put(metric.getType(), metric);
                }
                else{
                    compressedMetrics.get(metric.getType()).aggregate(metric);
                }
            }
        }
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

                Vector<Metric> currentMetrics = blocksInfo.get(i).getMetrics();
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
                }
            }
        }

    }

}
