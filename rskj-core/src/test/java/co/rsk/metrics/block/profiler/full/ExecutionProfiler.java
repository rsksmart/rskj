package co.rsk.metrics.block.profiler.full;

import co.rsk.metrics.block.profiler.full.marshalling.PersistedBlock;
import co.rsk.metrics.block.profiler.full.marshalling.PersistedBlockMetric;
import co.rsk.metrics.block.profiler.full.marshalling.PersistedMetric;
import co.rsk.metrics.block.profiler.full.marshalling.ProfilerResult;
import co.rsk.metrics.profilers.Metric;
import co.rsk.metrics.profilers.Profiler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecutionProfiler implements Profiler {


    private static volatile ExecutionProfiler singleton = null;
    private static Object mutex = new Object();

    //Metrics
    private ArrayList<BlockProfilingInfo> profilePerBlock;

    //Current block info
    private BlockProfilingInfo currentBlock;
    private ArrayList<MetricImpl> currentDetailedMetrics;


    private ThreadMXBean thread;
    int transferStartingBlock;

    @Override
    public synchronized Metric start(PROFILING_TYPE type) {

        if(PROFILING_TYPE.BLOCK_CONNECTION.equals(type)){
            BlockConnectionMetric blockConnectionMetrics = new BlockConnectionMetric(type, thread);
            this.currentBlock.setBlockConnectionInfo(blockConnectionMetrics);
            return blockConnectionMetrics;
        }

        MetricImpl metric = new MetricImpl(type, thread);
        currentDetailedMetrics.add(metric);
        return metric;
    }

    /*@Override
    public Metric startBlockConnection() {
        BlockConnectionMetric blockConnectionMetrics = new BlockConnectionMetric(PROFILING_TYPE.BLOCK_CONNECTION, thread);
        this.currentBlock.setBlockConnectionInfo(blockConnectionMetrics);
        return blockConnectionMetrics;
    }*/


    @Override
    public synchronized void stop(Metric metric) {
        ((MetricImpl)metric).setDelta(thread);
    }


    @Override
    public synchronized void newBlock(long blockId, int trxQty)
    {
        if (this.currentBlock != null && this.currentBlock.getBlockId() > 0) {
            this.profilePerBlock.add(currentBlock);
        }

        this.currentBlock = new BlockProfilingInfo(blockId, trxQty);
        this.currentDetailedMetrics = this.currentBlock.getMetrics();
    }


    public synchronized void clean() {
        this.currentBlock = null;
        this.profilePerBlock = new ArrayList<>();
        this.currentDetailedMetrics = null;
    }

    private ExecutionProfiler() {
        super();
        this.currentBlock = null;
        this.currentDetailedMetrics = null;
        this.profilePerBlock = new ArrayList<>();

        thread = ManagementFactory.getThreadMXBean();
        if(thread.isThreadCpuTimeSupported()){
            thread.setThreadCpuTimeEnabled(true);
        }
        //TODO: Add flag to avoid CPU time calculation when not supported
    }

    public void setTransferStartingBlock(int transferStartingBlock) {
        this.transferStartingBlock = transferStartingBlock;
    }

    //Thread-safe singleton
    public static final ExecutionProfiler singleton()  {

        ExecutionProfiler instance = singleton;

        if (instance == null) {
            synchronized (mutex) {
                instance = singleton;
                if (instance == null)
                    singleton = instance = new ExecutionProfiler();
            }
        }
        return instance; //instance instead of singleton is used to reduce volatile attribute access, increasing performance
    }

    //Bill pug singleton
    /*private static class ExecutionProfilerSingleton{
        private static final ExecutionProfiler INSTANCE = new ExecutionProfiler();
    }

    public static ExecutionProfiler getInstance(){
        return ExecutionProfilerSingleton.INSTANCE;
    }
*/

  public List<Metric> isAllStopped(){

        List<co.rsk.metrics.profilers.Metric> nonStopped = new ArrayList<>();

        for(BlockProfilingInfo info : this.profilePerBlock){
            for(MetricImpl metric : info.getMetrics()){
                if(!metric.isStopped()){
                    nonStopped.add(metric);
                }
            }
            if(!info.getBlockConnectionInfo().isStopped()){
                nonStopped.add(info.getBlockConnectionInfo());
            }

        }

        return  nonStopped;
    }


    /*Categories can repeat in a block, this methods aggregates all the values to avoid multiple entries of the same category in the file*/
    public void flushAggregated(String pathStr){
        if(this.currentBlock != null){
            this.profilePerBlock.add(this.currentBlock);
        }

        ArrayList<PersistedBlock> aggregatedList = new ArrayList<>();

        for(BlockProfilingInfo info: this.profilePerBlock){

            if(info.getBlockId() > 0 ){
                PersistedBlock aggregatedBlock = new PersistedBlock();
                aggregatedBlock.setBlockId(info.getBlockId());
                aggregatedBlock.setTrxs(info.getTrxs());

                Map<Integer, PersistedMetric> metricsMap = new HashMap<>();
                aggregatedList.add(aggregatedBlock);

                for(MetricImpl metric : info.getMetrics()){

                    if(metricsMap.containsKey(metric.getType())){
                        PersistedMetric currentMetric = metricsMap.get(metric.getType());
                        currentMetric.setThCPUt(currentMetric.getThCPUt()+ metric.getThCPUt());
                        currentMetric.setgCt(currentMetric.getgCt() + metric.getgCt());
                        currentMetric.setSt(currentMetric.getSt() + metric.getSt());
                        currentMetric.setNumOfEvents(currentMetric.getNumOfEvents() + 1);
                    }
                    else{
                        PersistedMetric newMetric = new PersistedMetric();
                        newMetric.setThCPUt(metric.getThCPUt());
                        newMetric.setgCt(metric.getgCt());
                        newMetric.setSt(metric.getSt());
                        newMetric.setType(metric.getType());
                        newMetric.setNumOfEvents(1);
                        metricsMap.put(metric.getType(), newMetric);
                    }
                }

                ArrayList<PersistedMetric> newMetrics = new ArrayList<>(metricsMap.size());
                newMetrics.addAll(metricsMap.values());
                aggregatedBlock.setMetrics(newMetrics);

                //Shortening value to store in JSON, a rounded-up integer in MB
                int startRam = Math.round(info.getBlockConnectionInfo().getRamAtStart()/(1048576));
                int endRam = Math.round(info.getBlockConnectionInfo().getRamAtEnd()/(1048576)); // 1048576=1024*1024

                PersistedBlockMetric blockConnection = new PersistedBlockMetric(startRam, endRam);
                blockConnection.setThCPUt(info.getBlockConnectionInfo().getThCPUt());
                blockConnection.setgCt(info.getBlockConnectionInfo().getgCt());
                blockConnection.setSt(info.getBlockConnectionInfo().getSt());
                blockConnection.setNumOfEvents(1);
                aggregatedBlock.setBlockConnection(blockConnection);

            }


        }

        Path path = Paths.get(pathStr);
        if (Files.exists(path)) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }

        ProfilerResult result = new ProfilerResult(aggregatedList);

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new FileWriter(pathStr), result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

