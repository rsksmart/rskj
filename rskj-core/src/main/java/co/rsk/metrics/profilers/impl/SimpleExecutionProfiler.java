/*package co.rsk.metrics.profilers.impl;

package co.rsk.metrics.profilers.impl;

import co.rsk.metrics.profilers.Profiler;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SimpleExecutionProfiler implements Profiler {


    private static volatile ExecutionProfiler singleton = null;
    private static Object mutex = new Object();
    private Vector<Profile> profilingStack;


    @Override
    public synchronized int start(PROFILING_TYPE type) {
        profilingStack.add(new Profile(type, Thread.currentThread().getId()));
        return profilingStack.size()-1;//Index of added element

    }

    @Override
    public synchronized void stop(int id) {
        profilingStack.get(id).setDelta();
    }



    public void clean(){
        this.profilingStack = new Vector<>();
    }

    private SimpleExecutionProfiler(){
        super();
        this.profilingStack = new Vector<>();
    }


    //Thread-safe singleton
    public static final ExecutionProfiler singleton(){

        ExecutionProfiler instance = singleton;

        if(instance == null){
            synchronized (mutex){
                instance = singleton;
                if(instance == null)
                    singleton = instance = new ExecutionProfiler();
            }
        }
        return instance; //instance instead of singleton is used to reduce volatile attribute access, increasing performance
    }

    public void flushStack(String pathStr) {
        Path path = Paths.get(pathStr);
        if(Files.exists(path)){
            try{
                Files.delete(path);
            }
            catch (IOException e){
                e.printStackTrace();
                return;
            }
        }

        try(BufferedWriter writer = Files.newBufferedWriter(path)){
            writer.write("{\"calls\":[");

            Iterator<Profile> profileIterator = profilingStack.iterator();

            while(profileIterator.hasNext()){
                Profile profile = profileIterator.next();
                writer.write("{" + "\"type\": \"" + profile.getType() + "\", \"time\": \"" + (profile.getTime()+0.0f)/1000000.0f +
                        "\", \"thread\": \"" + profile.getThreadId()+"\"}"+(profileIterator.hasNext()?",":""));
            }

            writer.write("]}");
        }
        catch (IOException e){
            e.printStackTrace();
        }

    }



    public void flushAggregatedStack(String pathStr){
        Path path = Paths.get(pathStr);
        if(Files.exists(path)){
            try{
                Files.delete(path);
            }
            catch (IOException e){
                e.printStackTrace();
                return;
            }
        }

        float[] times = {0,0,0,0,0,0,0};


        try(BufferedWriter writer = Files.newBufferedWriter(path)){
            writer.write("{\"calls\":{");

            Iterator<Profile> profileIterator = profilingStack.iterator();

            while(profileIterator.hasNext()){
                Profile profile = profileIterator.next();
                times[profile.getType().ordinal()] += (profile.getTime()+0.0f)/1000000.0f;
            }

            PROFILING_TYPE[] values = PROFILING_TYPE.values();
            for( int i = 0; i< values.length; i++){
                PROFILING_TYPE type = values[i];
                writer.write( "\"" + type + "\":\"" + times[type.ordinal()] +"\""+(i+1<values.length?",":""));
            }
            writer.write("}}");

        }
        catch (IOException e){
            e.printStackTrace();
        }

    }

    public void flushDetailedStack(String pathStr, Map<Long, ProfilerResultBlockInfo> playedBlocks, String description) {
        Path path = Paths.get(pathStr);
        if(Files.exists(path)){
            try{
                Files.delete(path);
            }
            catch (IOException e){
                e.printStackTrace();
                return;
            }
        }

        //stephanielelaurin@lelaurin.com martes 30 a las 12hs

        ProfilerResult result = new ProfilerResult();
        Map<String, Float> stats = result.getOtherStats();
        result.setBlocksInfo(playedBlocks);
        float[] times = {0,0,0,0,0,0,0};
        result.setReportName(description);

        Iterator<Profile> profileIterator = profilingStack.iterator();
        Long blockNum = 1L;

        while(profileIterator.hasNext()){
            Profile profile = profileIterator.next();

            if(profile.getType() == PROFILING_TYPE.BLOCK_EXECUTE){
                ProfilerResultBlockInfo block = playedBlocks.get(blockNum);
                if(block == null){
                    System.out.println("NO EXISTE BLOCK "+ blockNum);
                }
                block.setExecutionTime((profile.getTime()+0.0f)/1000000.0f);
                blockNum++;
            }
            else{
                times[profile.getType().ordinal()] += (profile.getTime()+0.0f)/1000000.0f;
            }
        }

        PROFILING_TYPE[] values = PROFILING_TYPE.values();
        for(int i = 0; i< values.length; i++){
            PROFILING_TYPE type = values[i];
            if(type != PROFILING_TYPE.BLOCK_EXECUTE){
                stats.put(type.name(),times[type.ordinal()]);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new FileWriter(pathStr), result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    private class Profile {
        private PROFILING_TYPE type;
        private long time;
        private long threadId;

        public Profile(PROFILING_TYPE type, long id){
            this.type = type;
            threadId = id;
            time = System.nanoTime();
        }

        public void setDelta(){
            this.time = System.nanoTime() - this.time;
        }

        public PROFILING_TYPE getType(){
            return this.type;
        }

        public long getTime(){
            return this.time;
        }

        public long getThreadId(){
            return this.threadId;
        }

    }
}

*/