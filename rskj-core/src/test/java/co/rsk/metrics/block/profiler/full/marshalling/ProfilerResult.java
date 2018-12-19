package co.rsk.metrics.block.profiler.full.marshalling;

import co.rsk.metrics.block.profiler.ProfilingException;

import java.util.ArrayList;

public class ProfilerResult {

    ArrayList<PersistedBlock> blocksInfo;
    Float blockExecutionTime;
    String reportName;
    int firstBlockWithTransfers;




    public ProfilerResult(){
        this(new ArrayList<>());
    }

    public ProfilerResult(ArrayList<PersistedBlock> blocksInfo){
        this.blocksInfo = blocksInfo;
        blockExecutionTime = -1.0F;
        this.reportName = "";

    }

    public ArrayList<PersistedBlock> getBlocksInfo() {
        return blocksInfo;
    }

    public void setBlocksInfo(ArrayList<PersistedBlock> blocksInfo) {
        this.blocksInfo = blocksInfo;
    }


    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public int getFirstBlockWithTransfers() {
        return firstBlockWithTransfers;
    }

    public void setFirstBlockWithTransfers(int firstBlockWithTransfers) {
        this.firstBlockWithTransfers = firstBlockWithTransfers;
    }

    public void aggregate(ProfilerResult playerRun, int runs) throws ProfilingException {

        if(this.blocksInfo.size() == 0){
            this.blocksInfo = playerRun.blocksInfo;

            for(PersistedBlock currentBlockinfo : blocksInfo){

                for(PersistedMetric currentMetric : currentBlockinfo.getMetrics()){
                    long value = currentMetric.getSt()/runs;
                    currentMetric.setSt(value);

                    value = currentMetric.getgCt()/runs;
                    currentMetric.setgCt(value);

                    value = currentMetric.getThCPUt()/runs;
                    currentMetric.setThCPUt(value);

                }

                int memVal = currentBlockinfo.getBlockConnection().getrS()/runs;
                currentBlockinfo.getBlockConnection().setrS(memVal);

                memVal = currentBlockinfo.getBlockConnection().getrE()/runs;
                currentBlockinfo.getBlockConnection().setrE(memVal);

                long value = currentBlockinfo.getBlockConnection().getSt()/runs;
                currentBlockinfo.getBlockConnection().setSt(value);

                value = currentBlockinfo.getBlockConnection().getgCt()/runs;
                currentBlockinfo.getBlockConnection().setgCt(value);

                value = currentBlockinfo.getBlockConnection().getThCPUt()/runs;
                currentBlockinfo.getBlockConnection().setThCPUt(value);

            }
        }
        else{
            //Blocks are ordered in ascending block Number
            for(int i = 0; i < playerRun.blocksInfo.size(); i++){

                PersistedBlock currentBlockInfo = blocksInfo.get(i);
                PersistedBlock newBlockInfo = playerRun.blocksInfo.get(i);

                if(currentBlockInfo.getBlockId() != newBlockInfo.getBlockId()){
                    throw new ProfilingException("Blocks not in order");
                }

                ArrayList<PersistedMetric> currentMetrics = blocksInfo.get(i).getMetrics();
                ArrayList<PersistedMetric> newMetrics = playerRun.blocksInfo.get(i).getMetrics();

                for(int j = 0; j < newMetrics.size(); j++){
                    PersistedMetric currentMetric = currentMetrics.get(j);
                    PersistedMetric newMetric = newMetrics.get(j);

                    //Metrics must be in order
                    if(currentMetric.getType() != newMetric.getType()){
                        throw new ProfilingException("Metric types don't match");
                    }
                    long value = currentMetric.getSt() + newMetric.getSt()/runs;
                    currentMetric.setSt(value);

                    value = currentMetric.getgCt() + newMetric.getgCt()/runs;
                    currentMetric.setgCt(value);

                    value = currentMetric.getThCPUt() + newMetric.getThCPUt()/runs;
                    currentMetric.setThCPUt(value);

                }


                PersistedBlockMetric currentMemMetrics = blocksInfo.get(i).getBlockConnection();
                PersistedBlockMetric newMemMetrics = playerRun.blocksInfo.get(i).getBlockConnection();

                int memVal = currentMemMetrics.getrS() + newMemMetrics.getrS()/runs;
                currentMemMetrics.setrS(memVal);

                memVal = currentMemMetrics.getrE() + newMemMetrics.getrE()/runs;
                currentMemMetrics.setrE(memVal);

                long value = currentMemMetrics.getSt() + newMemMetrics.getSt()/runs;
                currentMemMetrics.setSt(value);

                value = currentMemMetrics.getgCt() + newMemMetrics.getgCt()/runs;
                currentMemMetrics.setgCt(value);

                value = currentMemMetrics.getThCPUt() + newMemMetrics.getThCPUt()/runs;
                currentMemMetrics.setThCPUt(value);

            }
        }

    }
}
