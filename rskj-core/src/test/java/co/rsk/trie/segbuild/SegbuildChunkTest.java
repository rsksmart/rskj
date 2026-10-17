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
package co.rsk.trie.segbuild;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The directory name is the authoritative statement of a chunk's range, so parsing it strictly is
 * what keeps things that are not chunks out of the set.
 */
class SegbuildChunkTest {

    private Optional<SegbuildChunk> parse(String name) {
        return SegbuildChunk.parse(Paths.get("/segbuild", name));
    }

    @Test
    void aChunkNameGivesTheIndexAndRange() {
        SegbuildChunk chunk = parse("001-1591001-1852706").orElseThrow();

        assertEquals(1, chunk.getIndex());
        assertEquals(1591001L, chunk.getFirstBlock());
        assertEquals(1852706L, chunk.getLastBlock());
    }

    @Test
    void theRangeIsInclusiveAtBothEnds() {
        SegbuildChunk chunk = parse("000-1-1591000").orElseThrow();

        assertTrue(chunk.covers(1L));
        assertTrue(chunk.covers(1591000L));
        assertTrue(chunk.covers(795000L));
        assertFalse(chunk.covers(0L));
        assertFalse(chunk.covers(1591001L));
    }

    @Test
    void theSealedStoreIsInsideTheChunk() {
        SegbuildChunk chunk = parse("096-9229944-9230008").orElseThrow();

        assertEquals(Paths.get("/segbuild", "096-9229944-9230008", "sealed"), chunk.getSealedDirectory());
    }

    /**
     * A truncated chunk is retained beside the replacement that supersedes it. It must not be
     * mistaken for part of the set: reading it would answer with an incomplete range.
     */
    @Test
    void aSupersededChunkIsNotAChunk() {
        assertFalse(parse("095-9133348-9229943.truncated-superseded-20260917").isPresent());
    }

    @Test
    void thingsThatAreNotChunkNamesAreRejected() {
        assertFalse(parse("WRITE_TEST").isPresent());
        assertFalse(parse("sealed").isPresent());
        assertFalse(parse("1-2-3").isPresent(), "the index is zero-padded to three digits");
        assertFalse(parse("000-1").isPresent());
        assertFalse(parse("000-1-2-3").isPresent());
        assertFalse(parse("abc-1-2").isPresent());
    }

    @Test
    void aBackwardsRangeIsRejected() {
        assertFalse(parse("000-500-100").isPresent());
    }

    @Test
    void chunksSortByTheBlockTheyStartAt() {
        SegbuildChunk first = parse("000-1-1591000").orElseThrow();
        SegbuildChunk second = parse("001-1591001-1852706").orElseThrow();

        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
    }
}
