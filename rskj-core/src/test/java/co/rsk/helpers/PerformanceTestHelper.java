package co.rsk.helpers;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Created by SerAdmin on 10/22/2018.
 */
public class PerformanceTestHelper {


    long deltaTime; // in nanoseconds
    long deltaRealTimeMillis;
    long deltaGCTime;

    long startTime;
    long startRealTime;
    long startGCTime;

    ThreadMXBean thread;

    public long getDeltaTime() {
        // in nanoseconds
        return deltaTime;
    }

    public long getDeltaRealTimeMillis() {
        return deltaRealTimeMillis;
    }

    public long getDeltaGCTime() {
        return deltaGCTime;
    }

    public void setup() {
        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) return;

        Boolean old = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
    }

    public void startMeasure() {

        Boolean oldMode;
        startTime = 0;
        thread = ManagementFactory.getThreadMXBean();
        if (thread.isThreadCpuTimeSupported())

        {
            oldMode = thread.isThreadCpuTimeEnabled();
            thread.setThreadCpuTimeEnabled(true);
            startTime = thread.getCurrentThreadCpuTime(); // in nanoseconds.
        }
        startRealTime = System.currentTimeMillis();
        startGCTime = getGarbageCollectorTimeMillis();
    }

    public long getGarbageCollectorTimeMillis() {
        long t = 0;
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc : gcs) {
            t += gc.getCollectionTime();
        }
        return t;
    }

    public static String padLeft(long v) {
        return String.format("%1$8s", Long.toString(v));
    }

    public static final int nsToUs = 1000 ;
    public static final int nsToMs = 1000 * 1000;
    public static final int nsToS = 1000 * 1000 * 1000;

    public void endMeasure(String midstate) {
        System.out.println("End: " + midstate);
        endMeasure();
    }

    public void line() {
        System.out.println("---------------------------------------------------------------");
    }

    public void endMeasure() {
        line();
        if (startTime != 0) {
            long endTime = thread.getCurrentThreadCpuTime();
            deltaTime = (endTime - startTime); // nano
            System.out.println("Time elapsed [ms]:     " + padLeft(deltaTime/nsToMs)+" [s]:"+ padLeft(deltaTime/nsToS));
        }

        if (startRealTime!=0) {
            long endRealTime =System.currentTimeMillis();
            deltaRealTimeMillis = (endRealTime - startRealTime);
            System.out.println("RealTime elapsed [ms]: " + padLeft(deltaRealTimeMillis)+" [s]:"+ padLeft(deltaRealTimeMillis /1000));
        }
        long endGCTime = getGarbageCollectorTimeMillis();
        deltaGCTime = (endGCTime - startGCTime);
        System.out.println("GCTime elapsed [ms]:   " + padLeft(deltaGCTime)+" [s]:"+ padLeft(deltaGCTime/1000));
        line();
    }
}
