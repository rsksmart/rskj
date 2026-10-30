package co.rsk.cli.tools;

import co.rsk.util.NodeStopper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Times a replay of one block range against a chosen trie backend. Driven by system properties so
 * each scenario can be a separate process with a cold page cache.
 */
class ReplayBenchmarkTest {

    private static Path snapshot() {
        return Paths.get(env("BENCH_SNAPSHOT", "bench.snapshot", "/mnt/import/rskj-snapshot"));
    }

    private static Path segbuild() {
        return Paths.get(env("BENCH_SEGBUILD", "bench.segbuild", "/srv/segbuild-ro"));
    }

    private static String env(String envName, String propName, String fallback) {
        String v = System.getenv(envName);
        if (v != null && !v.isEmpty()) {
            return v;
        }
        return System.getProperty(propName, fallback);
    }

    @Test
    void replay() {
        Path SNAPSHOT = snapshot();
        Path SEGBUILD = segbuild();
        Assumptions.assumeTrue(Files.isDirectory(SNAPSHOT), "no snapshot");

        String backend = env("BENCH_BACKEND", "bench.backend", "UNITRIE");
        long from = Long.parseLong(env("BENCH_FROM", "bench.from", "9226944"));
        long to = Long.parseLong(env("BENCH_TO", "bench.to", "9229943"));

        List<String> args = new ArrayList<>(List.of(
                "--fromBlock", Long.toString(from), "--toBlock", Long.toString(to),
                "--trieStore", backend,
                "-Xdatabase.dir=" + SNAPSHOT, "--main"));
        if ("SEGBUILD".equals(backend)) {
            args.add("--segbuildDir");
            args.add(SEGBUILD.toString());
        }

        // Extra -X settings, e.g. to turn the BTC header cache's read-ahead off.
        String extra = env("BENCH_EXTRA", "bench.extra", "");
        for (String e : extra.split(",")) {
            if (!e.isBlank()) {
                args.add(e.trim());
            }
        }
        System.out.println("BENCH args: " + String.join(" ", args));

        int[] code = {-1};
        long t0 = System.nanoTime();
        new ExecuteBlocks().execute(args.toArray(new String[0]), (NodeStopper) s -> code[0] = s);
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        long blocks = to - from + 1;
        System.out.println(String.format(
                "BENCH backend=%s from=%d to=%d blocks=%d exit=%d wallMs=%d blocksPerSec=%.2f",
                backend, from, to, blocks, code[0], ms, blocks * 1000.0 / ms));
    }
}
