package co.rsk.helpers;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Created by Sergio Demian Lerner on 10/22/2018.
 */
public class Stopwatch {



    protected long deltaTime_ns; // in nanoseconds
    protected long deltaRealTimeMillis; // in milliseconds
    protected long deltaGCTimeMillis;

    long startTime;
    long startRealTime;
    long startGCTime;
    ThreadMXBean thread;
    boolean printResults = true;

    public void setup() {
        thread = ManagementFactory.getThreadMXBean();
        if (!thread.isThreadCpuTimeSupported()) return;

        Boolean old = thread.isThreadCpuTimeEnabled();
        thread.setThreadCpuTimeEnabled(true);
    }

    public long getDeltaTime_ns() {
        return deltaTime_ns;
    }

    public long getDeltaRealTimeMillis() {
        return deltaRealTimeMillis;
    }

    public long getDeltaGCTimeMillis() {
        return deltaGCTimeMillis;
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

    public long getGarbageCollectorTimeMillis()
    {
        long t=0;
        List<GarbageCollectorMXBean> gcs = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gc :gcs) {
            t +=gc.getCollectionTime();
        }
        return t;
    }

    public static String padLeft(long v) {
        return String.format("%1$8s", Long.toString(v));
    }
    final int nsToMs = 1000*1000;
    final int nsToS  = 1000*1000*1000;

    public void endMeasure(String msg) {
        if (printResults)
            System.out.println(msg);
        endMeasure();
    }

    public void endMeasure() {
        if (printResults)
            System.out.println("---------------------------------------------------------------");

        if (startTime != 0) {
            long endTime = thread.getCurrentThreadCpuTime();
            deltaTime_ns = (endTime - startTime); // nano
            if (printResults)
                System.out.println("Time elapsed [ms]:     " + padLeft(deltaTime_ns /nsToMs)+" [s]:"+ padLeft(deltaTime_ns /nsToS));
        }

        if (startRealTime!=0) {
            long endRealTime =System.currentTimeMillis();
            deltaRealTimeMillis = (endRealTime - startRealTime);
            if (printResults)
                System.out.println("RealTime elapsed [ms]: " + padLeft(deltaRealTimeMillis)+" [s]:"+ padLeft(deltaRealTimeMillis /1000));
        }
        long endGCTime = getGarbageCollectorTimeMillis();
        deltaGCTimeMillis = (endGCTime - startGCTime);
        if (printResults)
            System.out.println("GCTime elapsed [ms]:   " + padLeft(deltaGCTimeMillis)+" [s]:"+ padLeft(deltaGCTimeMillis /1000));

    }
}
