package co.rsk.metrics.block.profiler.simple;

import co.rsk.metrics.profilers.Profiler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ExecutionProfiler implements Profiler {


    private static volatile ExecutionProfiler singleton = null;
    private static Object mutex = new Object();

    private Vector<BlockProfilingInfo> profilePerBlock;
    private BlockProfilingInfo currentBlock;


    @Override
    public synchronized int start(PROFILING_TYPE type) {
        currentBlock.getMetrics().add(new Metric(type));
        return currentBlock.getMetrics().size() - 1;//Index of added element

    }

    @Override
    public synchronized void stop(int id) {
        currentBlock.getMetrics().get(id).setDelta();
    }


    @Override
    public synchronized void newBlock(long blockId, int trxQty)
    {
        if (this.currentBlock != null) {
            this.profilePerBlock.add(currentBlock);
        }

        this.currentBlock = new BlockProfilingInfo(blockId, trxQty);
    }


    public synchronized void clean() {
        this.currentBlock = null;
        this.profilePerBlock = new Vector<>();
    }

    private ExecutionProfiler() {
        super();
        this.currentBlock = null;
        this.profilePerBlock = new Vector<>();
    }


    //Thread-safe singleton
    public static final ExecutionProfiler singleton() {

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

