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
    long deltaRealTime;
    long startTime;
    long startRealTime;
    long startGCTime;
    ThreadMXBean thread;
    public boolean shortFormat = false;

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
        System.out.println(msg);
        endMeasure();
    }

    public void endMeasure() {
        String  s="";
        System.out.println("---------------------------------------------------------------");
        if (startTime != 0) {
            long endTime = thread.getCurrentThreadCpuTime();
            deltaTime = (endTime - startTime); // nano
            if (shortFormat)
                s = s+ " CPUTime[s]: "+padLeft(deltaTime/nsToS);
            else
                System.out.println("Time elapsed [ms]:     " + padLeft(deltaTime/nsToMs)+" [s]:"+ padLeft(deltaTime/nsToS));
        }

        if (startRealTime!=0) {
            long endRealTime =System.currentTimeMillis();
            deltaRealTime = (endRealTime - startRealTime);
            if (shortFormat)
                s = s+ " RealTime[s]: "+padLeft(deltaRealTime/1000);
            else
                System.out.println("RealTime elapsed [ms]: " + padLeft(deltaRealTime)+" [s]:"+ padLeft(deltaRealTime/1000));
        }
        long endGCTime = getGarbageCollectorTimeMillis();
        long deltaGCTime = (endGCTime - startGCTime);
        if (shortFormat) {
            s = s + " GCTime[s]: " + padLeft(deltaGCTime / 1000);
            System.out.println(s);
        }
        else
            System.out.println("GCTime elapsed [ms]:   " + padLeft(deltaGCTime)+" [s]:"+ padLeft(deltaGCTime/1000));

    }
}
