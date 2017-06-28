package co.rsk.scoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class NodeStatus {
    private Map<EventType, Integer> counters = new HashMap<>();

    public void recordEvent(EventType evt) {
        if (!counters.containsKey(evt))
            counters.put(evt, new Integer(1));
        else
            counters.put(evt, new Integer(counters.get(evt).intValue() + 1));
    }

    public int getEventCounter(EventType evt) {
        if (!counters.containsKey(evt))
            return 0;

        return counters.get(evt).intValue();
    }

    public int getTotalEventCounter() {
        int counter = 0;

        for (Map.Entry<EventType, Integer> entry : counters.entrySet())
            counter += entry.getValue().intValue();

        return counter;
    }

    public boolean isEmpty() {
        return counters.isEmpty();
    }
}
