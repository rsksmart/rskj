package co.rsk.metrics.profilers;


import co.rsk.metrics.profilers.impl.DummyProfiler;

public class ProfilerFactory {


    private static Profiler instance = null;


    public static void configure(Profiler profiler){
        if(instance == null){
            instance = profiler;
        }
    }

    public static Profiler getInstance(){
        if(instance == null){
            instance = new DummyProfiler();
        }

        return instance;
    }
}
