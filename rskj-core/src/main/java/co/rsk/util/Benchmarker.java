package co.rsk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Benchmarker {
    private static final Map<String, Benchmarker> instances = new HashMap<>();
    private static final Benchmarker defaultBenchmarker = new Benchmarker(LoggerFactory.getLogger("benchmarker"));

    private class Item {
        public final int index;
        public String name;
        public String data;
        public long start;
        public long end;

        public Item(int index) {
            this.index = index;
            start = System.nanoTime();
        }

        public void finish() {
            end = System.nanoTime();
        }

        public long getDurationNano() {
            return end - start;
        }

        public long getDurationMs() {
            return Math.round(getDurationNano()/1_000_000);
        }
    }

    private Integer itemIndex = 0;
    private Logger logger;
    private Map<String, Item> items;

    public static Benchmarker get(String name) {
        if (!instances.containsKey(name)) {
            return defaultBenchmarker;
        }

        return instances.get(name);
    }

    public static Benchmarker create(String name, Logger logger) {
        Benchmarker benchmarker = new Benchmarker(logger);
        instances.put(name, benchmarker);
        return benchmarker;
    }

    public Benchmarker(Logger logger) {
        this.logger = logger;
        this.items = new HashMap<>();
    }

    public void start(String name) {
        start(name, "");
    }

    public void start(String name, String data) {
        Item item;
        synchronized (itemIndex) {
            item = new Item(++itemIndex);
        }
        item.name = name;
        item.data = data;
        items.put(name, item);

        logger.info("{}-START [{}]({})", item.index, name, data);
    }

    public boolean end(String name) {
        if (!items.containsKey(name)) {
            return false;
        }

        Item item = items.remove(name);
        item.finish();

        logger.info("{}-END [{}]({}). Duration: {}ms ({}ns)",
                item.index,
                item.name,
                item.data,
                item.getDurationMs(),
                item.getDurationNano()
        );

        return true;
    }
}
