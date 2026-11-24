/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.cli.tools;

import co.rsk.util.NodeStopper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

/**
 * Replays a small range of real blocks from a node snapshot, against each trie backend.
 *
 * <p>Skipped unless the databases are present. Point them elsewhere with {@code -Dreplay.snapshot}
 * and {@code -Dreplay.segbuild}.
 */
class ExecuteBlocksReplayTest {

    private static final Path SNAPSHOT =
            Paths.get(System.getProperty("replay.snapshot", "/mnt/import/rskj-snapshot"));
    private static final Path SEGBUILD =
            Paths.get(System.getProperty("replay.segbuild", "/srv/segbuild-ro"));

    /** A short range inside the last segbuild chunk, so one chunk serves the whole run. */
    private static final String FROM = "9229950";
    private static final String TO = "9229953";

    private int replay(String... extra) {
        String[] base = {"--fromBlock", FROM, "--toBlock", TO, "-Xdatabase.dir=" + SNAPSHOT, "--main"};
        List<String> args = new ArrayList<>(List.of(base));
        args.addAll(List.of(extra));

        int[] code = {-1};
        NodeStopper stopper = status -> code[0] = status;
        new ExecuteBlocks().execute(args.toArray(new String[0]), stopper);
        return code[0];
    }

    private static void assumeSnapshot() {
        Assumptions.assumeTrue(Files.isDirectory(SNAPSHOT), "no snapshot at " + SNAPSHOT);
    }

    private static void assumeSegbuild() {
        assumeSnapshot();
        Assumptions.assumeTrue(Files.isDirectory(SEGBUILD), "no segbuild at " + SEGBUILD);
    }

    @Test
    void replaysAgainstTheNodesOwnUnitrie() {
        assumeSnapshot();

        assertEquals(0, replay("--trieStore", "UNITRIE"),
                "every block should have matched its header's state root");
    }

    @Test
    void replaysAgainstSegbuild() {
        assumeSegbuild();

        assertEquals(0, replay("--trieStore", "SEGBUILD", "--segbuildDir", SEGBUILD.toString(),
                        "--segbuildFallback"),
                "every block should have matched its header's state root");
    }

    /** The backend must not change the answer: both agree with the headers, so both agree. */
    @Test
    void bothBackendsAgreeWithTheHeaders() {
        assumeSegbuild();

        assertEquals(0, replay("--trieStore", "UNITRIE"));
        assertEquals(0, replay("--trieStore", "SEGBUILD", "--segbuildDir", SEGBUILD.toString(),
                "--segbuildFallback"));
    }

    @Test
    void segbuildNeedsItsDirectory() {
        assumeSnapshot();

        assertEquals(1, replay("--trieStore", "SEGBUILD"), "should refuse without --segbuildDir");
    }

    @Test
    void savingStateNeedsWritesToBeAllowed() {
        assumeSnapshot();

        assertEquals(1, replay("--trieStore", "UNITRIE", "--saveState=true"),
                "should refuse to save state without --allowWrites");
    }

    /**
     * The whole point: a replay reads history, it does not alter it. Both databases must be
     * untouched afterwards -- same entries, same sizes, same modification times.
     */
    @Test
    void replayingWritesNothingToEitherDatabase() throws IOException {
        assumeSegbuild();

        List<String> snapshotBefore = manifest(SNAPSHOT);
        List<String> segbuildBefore = manifest(SEGBUILD);

        assertEquals(0, replay("--trieStore", "SEGBUILD", "--segbuildDir", SEGBUILD.toString(),
                "--segbuildFallback"));

        assertLinesMatch(snapshotBefore, manifest(SNAPSHOT), "the snapshot was modified by a replay");
        assertLinesMatch(segbuildBefore, manifest(SEGBUILD), "segbuild was modified by a replay");
    }

    private static List<String> manifest(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.sorted().map(p -> {
                try {
                    return p + "|" + (Files.isDirectory(p) ? "dir" : Files.size(p))
                            + "|" + Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return p + "|unreadable";
                }
            }).collect(java.util.stream.Collectors.toList());
        }
    }
}
