package co.rsk.metrics.block.profiler.detailed;

import co.rsk.metrics.block.profiler.ProfilingException;
import co.rsk.metrics.profilers.Profiler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ExecutionProfiler implements Profiler {


    private static volatile ExecutionProfiler singleton = null;
    private static Object mutex = new Object();

    private ArrayList<BlockProfilingInfo> profilePerBlock;
    private BlockProfilingInfo currentBlock;
    private ArrayList<Metric> currentMetrics;
    private ThreadMXBean thread;
    int startCount;
    int stopCount;


    @Override
    public synchronized co.rsk.metrics.profilers.Metric start(PROFILING_TYPE type) {
        startCount++;
        Metric metric = new Metric(type, thread);
        currentMetrics.add(metric);
        return metric;

    }

    @Override
    public synchronized void stop(co.rsk.metrics.profilers.Metric metric) {
        stopCount++;

        metric.setDelta(thread);
    }


    @Override
    public synchronized void newBlock(long blockId, int trxQty)
    {
        if (this.currentBlock != null && this.currentBlock.getBlockId() > 0) {
            this.profilePerBlock.add(currentBlock);
        }

        this.currentBlock = new BlockProfilingInfo(blockId, trxQty);
        this.currentMetrics = this.currentBlock.getMetrics();
    }


    public synchronized void clean() {
        this.currentBlock = null;
        this.profilePerBlock = new ArrayList<>();
        this.currentMetrics = null;
        startCount = stopCount = 0;
    }

    private ExecutionProfiler() throws ProfilingException {
        super();
        startCount = stopCount = 0;
        this.currentBlock = null;
        this.currentMetrics = null;
        this.profilePerBlock = new ArrayList<>();
        thread = ManagementFactory.getThreadMXBean();
        if(!thread.isThreadCpuTimeSupported()){
            throw new ProfilingException("Thread CPU Time is not supported");
        }
        thread.setThreadCpuTimeEnabled(true);
    }


    //Thread-safe singleton
    public static final ExecutionProfiler singleton() throws ProfilingException {

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


   public List<Metric> isAllStopped(){

        System.out.println("START COUNT "+startCount);
        System.out.println("STOP COUNT" + stopCount);
        List<Metric> nonStopped = new ArrayList<>();
        for(BlockProfilingInfo info : this.profilePerBlock){
            int idx = 0;
            for(Metric metric : info.getMetrics()){
                if(!metric.isStopped()){
                    nonStopped.add(metric);
                    //System.out.println(idx);
                }
                idx++;
            }
        }

        return  nonStopped;
    }


    public void flushAggregated(String pathStr){
        if(this.currentBlock != null){
            this.profilePerBlock.add(this.currentBlock);
        }

        ArrayList<BlockProfilingInfo> aggregatedList = new ArrayList<>();

        for(BlockProfilingInfo info: this.profilePerBlock){

            BlockProfilingInfo aggregatedBlock = new BlockProfilingInfo();
            aggregatedBlock.setBlockId(info.getBlockId());
            aggregatedBlock.setTrxs(info.getTrxs());
            Map<Integer, Metric> metricsMap = new HashMap<>();
            ArrayList<Metric> newMetrics = new ArrayList<>();
            aggregatedBlock.setMetrics(newMetrics);
            aggregatedList.add(aggregatedBlock);


            for(Metric metric : info.getMetrics()){
                if(metricsMap.containsKey(metric.getType())){
                    Metric currentMetric = metricsMap.get(metric.getType());
                    currentMetric.setThCPUt(currentMetric.getThCPUt()+ metric.getThCPUt());
                    currentMetric.setgCt(currentMetric.getgCt() + metric.getgCt());
                    currentMetric.setSt(currentMetric.getSt() + metric.getSt());
                }
                else{
                    Metric newMetric = new Metric();
                    newMetric.setThCPUt(metric.getThCPUt());
                    newMetric.setgCt(metric.getgCt());
                    newMetric.setSt(metric.getSt());
                    newMetric.setType(metric.getType());
                    metricsMap.put(metric.getType(), newMetric);
                }
            }

            newMetrics.addAll(metricsMap.values());
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
    public void flush(String pathStr) {
        if(this.currentBlock != null){
            this.profilePerBlock.add(this.currentBlock);
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

        ProfilerResult result = new ProfilerResult(profilePerBlock);

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new FileWriter(pathStr), result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

