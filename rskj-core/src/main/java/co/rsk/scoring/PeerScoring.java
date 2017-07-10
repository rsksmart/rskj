package co.rsk.scoring;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ajlopez on 27/06/2017.
 */
public class PeerScoring {
    private Map<EventType, Integer> counters = new HashMap<>();
    private boolean goodReputation = true;
    private long timeLostGoodReputation;

    public void recordEvent(EventType evt) {
        if (!counters.containsKey(evt))
            counters.put(evt, new Integer(1));
        else
            counters.put(evt, new Integer(counters.get(evt).intValue() + 1));
    }

    public int getScore() {
        return 0;
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

    public boolean hasGoodReputation() { return this.goodReputation; }

    public void setGoodReputation(boolean value) {
        if (value == false && this.goodReputation == true)
            this.timeLostGoodReputation = System.currentTimeMillis();

        this.goodReputation = value;
    }

    public long getTimeLostGoodReputation() {
        return this.timeLostGoodReputation;
    }
}
