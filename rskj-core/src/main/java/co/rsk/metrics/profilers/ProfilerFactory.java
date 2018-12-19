package co.rsk.metrics.profilers;


import co.rsk.metrics.profilers.impl.DummyProfiler;

public class ProfilerFactory {


    private static volatile Profiler instance = null;



    public static synchronized void configure(Profiler profiler){
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
