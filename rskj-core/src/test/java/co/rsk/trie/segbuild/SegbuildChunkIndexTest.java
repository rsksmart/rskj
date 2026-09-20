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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegbuildChunkIndexTest {

    @TempDir
    Path root;

    private void chunk(String name) throws IOException {
        Files.createDirectories(root.resolve(name).resolve("sealed"));
    }

    @Test
    void findsTheChunkCoveringABlock() throws IOException {
        chunk("000-1-1000");
        chunk("001-1001-2000");
        chunk("002-2001-3000");

        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        assertEquals(3, index.size());
        assertEquals(0, index.requireChunkFor(1L).getIndex());
        assertEquals(0, index.requireChunkFor(1000L).getIndex());
        assertEquals(1, index.requireChunkFor(1001L).getIndex());
        assertEquals(2, index.requireChunkFor(3000L).getIndex());
    }

    @Test
    void reportsTheOverallRange() throws IOException {
        chunk("000-1-1000");
        chunk("001-1001-2000");

        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        assertEquals(1L, index.getFirstBlock());
        assertEquals(2000L, index.getLastBlock());
    }

    @Test
    void aBlockOutsideEveryChunkHasNoChunk() throws IOException {
        chunk("000-1-1000");

        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        assertFalse(index.chunkFor(1001L).isPresent());
        assertThrows(IllegalArgumentException.class, () -> index.requireChunkFor(1001L));
    }

    /**
     * A superseded chunk sits beside its replacement and covers the same range. Including it would
     * make the set look overlapping and could serve reads from an incomplete store.
     */
    @Test
    void asupersededChunkIsSkippedRatherThanColliding() throws IOException {
        chunk("000-1-1000");
        chunk("001-1001-2000");
        chunk("001-1001-2000.truncated-superseded-20260917");
        Files.createFile(root.resolve("WRITE_TEST"));

        SegbuildChunkIndex index = SegbuildChunkIndex.scan(root);

        assertEquals(2, index.size());
        assertEquals(1, index.requireChunkFor(1500L).getIndex());
    }

    @Test
    void overlappingChunksAreRejected() throws IOException {
        chunk("000-1-1500");
        chunk("001-1001-2000");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SegbuildChunkIndex.scan(root));
        assertTrue(e.getMessage().contains("overlap"));
    }

    @Test
    void anEmptyRootIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SegbuildChunkIndex.scan(root));
    }

    @Test
    void aMissingRootIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SegbuildChunkIndex.scan(root.resolve("does-not-exist")));
    }
}
