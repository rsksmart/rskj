package co.rsk.metrics.profilers;


import co.rsk.metrics.profilers.impl.DummyProfiler;


/**
 * ProfilerFactory is used to get the configured Profiler instance.
 * Only one profiler can be defined, once a profiler is set, it cannot be changed.
 * If a profiler isn't configured, the DummyProfiler will be set upon the first request for the instance.
 */
public final class ProfilerFactory {

    private static volatile Profiler instance = null;

    private ProfilerFactory(){
        super();
    }

    public static synchronized void configure(Profiler profiler){
        if(instance == null){
            instance = profiler;
        }
    }

    public static Profiler getInstance(){
        if(instance == null){
            configure(new DummyProfiler());
        }

        return instance;
    }
}
