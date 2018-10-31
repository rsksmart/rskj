package co.rsk.metrics.block.tests;

import co.rsk.metrics.profilers.impl.ProfilerResult;
import co.rsk.metrics.profilers.impl.ProfilingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;


public class RunsAggregation {

    @Test
    public void aggregateValues() throws IOException, ProfilingException {
        generateAggregatedFile("playRun", "aggregatedRun.json", "No Remasc", 10);
        generateAggregatedFile("playRunRemasc", "aggregatedRunRemasc.json", "With Remasc", 10);
    }

    private void generateAggregatedFile(String sourceName, String destFile, String description,int runs) throws IOException, ProfilingException {
        String path = "/home/raul/RSKWorkspace/rskj/rskj-core/src/test/resources/performance/player-runs";
        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructType(ProfilerResult.class);


        ProfilerResult aggregatedRun = new ProfilerResult();
        for(int i = 0; i < runs; i++){
            String json = new String(ByteStreams.toByteArray(new FileInputStream(path+"/"+sourceName+"_"+i+".json")));
            ProfilerResult playerRun  = mapper.readValue(json, type);
            aggregatedRun.aggregate(playerRun, runs);
        }

        aggregatedRun.setReportName(description);
        mapper.writeValue(new FileWriter(path+"/"+destFile), aggregatedRun);

    }
}
