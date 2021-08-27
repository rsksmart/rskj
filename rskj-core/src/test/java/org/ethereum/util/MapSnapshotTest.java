/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.util;

import org.ethereum.db.ByteArrayWrapper;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MapSnapshotTest {

    @Test
    public void readSnapshot_WhenRead_PopulateMap() throws IOException {
        byte[] key = new byte[] {0, 1, 2, 3, 4};
        byte[] value = new byte[] {5, 6, 7, 8, 9};

        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(key.length);
            out.write(key);
            out.writeInt(value.length);
            out.write(value);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        Map<ByteArrayWrapper, byte[]> map = new HashMap<>();

        inSnapshot.read(map);

        assertFalse(map.isEmpty());
        assertArrayEquals(key, map.keySet().stream().findFirst().orElseThrow(IllegalStateException::new).getData());
        assertArrayEquals(value, map.values().stream().findFirst().orElseThrow(IllegalStateException::new));
    }

    @Test
    public void readSnapshot_WhenNull_ThrowError() throws IOException {
        InputStream inputStream = makeInputStream(out -> {});
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            //noinspection ConstantConditions
            inSnapshot.read(null);
            fail();
        } catch (NullPointerException ignored) { }
    }

    @Test
    public void readSnapshot_WhenEmptyStream_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {});
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException ignored) { }
    }

    @Test
    public void readSnapshot_WhenInvalidCount_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(0);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException e) {
            assertEquals("Invalid data: number of entries", e.getMessage());
        }
    }

    @Test
    public void readSnapshot_WhenInvalidKeySize_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(0);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException e) {
            assertEquals("Invalid data: key size", e.getMessage());
        }
    }

    @Test
    public void readSnapshot_WhenInvalidKeyLength_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException ignored) { }
    }

    @Test
    public void readSnapshot_WhenInvalidValueSize_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
            out.writeByte(100);
            out.writeInt(-2);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException e) {
            assertEquals("Invalid data: value size", e.getMessage());
        }
    }

    @Test
    public void readSnapshot_WhenInvalidValueLength_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
            out.writeByte(100);
            out.writeInt(1);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            fail();
        } catch (IOException ignored) { }
    }

    @Test
    public void closeInputSnapshot_WhenClose_ShouldTriggerCloseOfNestedStream() throws IOException {
        InputStream inputStream = mock(InputStream.class);
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);

        inSnapshot.close();

        verify(inputStream, atLeastOnce()).close();
    }

    @Test
    public void writeSnapshot_WhenWrite_ShouldPopulateOutputStream() throws IOException {
        byte[] key = new byte[] {0, 1, 2, 3, 4};
        byte[] value = new byte[] {5, 6, 7, 8, 9};
        Map<ByteArrayWrapper, byte[]> outMap = new HashMap<>();
        outMap.put(ByteUtil.wrap(key), value);

        ByteArrayOutputStream expectedOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(expectedOutputStream);
        dataOutputStream.writeInt(1);
        dataOutputStream.writeInt(key.length);
        dataOutputStream.write(key);
        dataOutputStream.writeInt(value.length);
        dataOutputStream.write(value);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MapSnapshot.Out outSnapshot = new MapSnapshot.Out(outputStream);

        outSnapshot.write(outMap);

        assertArrayEquals(expectedOutputStream.toByteArray(), outputStream.toByteArray());
    }

    @Test
    public void closeOutputSnapshot_WhenClose_ShouldTriggerCloseOfNestedStream() throws IOException {
        OutputStream outputStream = mock(OutputStream.class);
        MapSnapshot.Out outSnapshot = new MapSnapshot.Out(outputStream);

        outSnapshot.close();

        verify(outputStream, atLeastOnce()).close();
    }

    @Test
    public void writeAndReadSnapshot_WhenReadWritten_ShouldMatch() throws IOException {
        Map<ByteArrayWrapper, byte[]> outMap = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            byte[] key = new byte[] {(byte) i, (byte) (i + 1), (byte) (i + 2), (byte) (i + 3), (byte) (i + 4)};
            byte[] value = new byte[] {(byte) (i + 5), (byte) (i + 6), (byte) (i + 7), (byte) (i + 8), (byte) (i + 9)};
            outMap.put(ByteUtil.wrap(key), value);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (MapSnapshot.Out outSnapshot = new MapSnapshot.Out(outputStream)) {
            outSnapshot.write(outMap);
        }

        Map<ByteArrayWrapper, byte[]> inMap = new HashMap<>();
        try (MapSnapshot.In inSnapshot = new MapSnapshot.In(new ByteArrayInputStream(outputStream.toByteArray()))) {
            inSnapshot.read(inMap);
        }

        assertEquals(10, inMap.size());
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : inMap.entrySet()) {
            assertArrayEquals(entry.getValue(), outMap.get(entry.getKey()));
        }
    }

    private static InputStream makeInputStream(OutputStreamConsumer consumer) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            consumer.accept(new DataOutputStream(outputStream));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    @FunctionalInterface
    public interface OutputStreamConsumer {
        void accept(DataOutputStream out) throws IOException;
    }
}
