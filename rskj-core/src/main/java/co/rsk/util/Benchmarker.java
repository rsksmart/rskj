package co.rsk.util;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class Benchmarker {
    private static final Map<String, Benchmarker> instances = new HashMap<>();

    private class Item {
        public long start;
        public long end;

        public Item() {
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

    private Logger logger;
    private Map<String, Item> items;

    public static Benchmarker get(String name) {
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
        Item item = new Item();
        items.put(name, item);

        logger.info("[{}] START @ {}", name, item.start);
    }

    public boolean end(String name) {
        if (!items.containsKey(name)) {
            return false;
        }

        Item item = items.remove(name);
        item.finish();

        logger.info("[{}] END @ {}. Duration: {}ms ({}ns)",
                name,
                item.end,
                item.getDurationMs(),
                item.getDurationNano()
        );

        return true;
    }
}
