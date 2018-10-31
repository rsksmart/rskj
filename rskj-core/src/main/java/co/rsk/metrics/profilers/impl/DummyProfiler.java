package co.rsk.metrics.profilers.impl;


import co.rsk.metrics.profilers.Profiler;

public class DummyProfiler implements Profiler {

    @Override
    public int start(PROFILING_TYPE type) {
        return 0;
    }

    @Override
    public void stop(int id) {

    }

    @Override
    public void newBlock(long blockId, int trxQty){
    }
}
