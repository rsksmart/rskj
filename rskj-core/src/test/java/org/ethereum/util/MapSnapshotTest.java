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

import org.ethereum.crypto.HashUtil;
import org.ethereum.db.ByteArrayWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.security.DigestOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

class MapSnapshotTest {

    @Test
    void readSnapshot_WhenRead_PopulateMap() throws IOException {
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

        Assertions.assertFalse(map.isEmpty());
        Assertions.assertArrayEquals(key, map.keySet().stream().findFirst().orElseThrow(IllegalStateException::new).getData());
        Assertions.assertArrayEquals(value, map.values().stream().findFirst().orElseThrow(IllegalStateException::new));
    }

    @Test
    void readSnapshot_WhenMismatchedChecksums_ThrowError() {
        byte[] key = new byte[] {0, 1, 2, 3, 4};
        byte[] value = new byte[] {5, 6, 7, 8, 9};

        byte[] bytes = composeByteArray(out -> {
            out.writeInt(1);
            out.writeInt(key.length);
            out.write(key);
            out.writeInt(value.length);
            out.write(value);
        });
        bytes[9]++; // corrupt data
        InputStream inputStream = new ByteArrayInputStream(bytes);
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        Map<ByteArrayWrapper, byte[]> map = new HashMap<>();

        Assertions.assertThrows(IOException.class, () -> inSnapshot.read(map));

//        Assertions.assertFalse(map.isEmpty());
//        Assertions.assertArrayEquals(key, map.keySet().stream().findFirst().orElseThrow(IllegalStateException::new).getData());
//        Assertions.assertArrayEquals(value, map.values().stream().findFirst().orElseThrow(IllegalStateException::new));
    }

    @Test
    void readSnapshot_WhenNull_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {});
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);

        Assertions.assertThrows(NullPointerException.class, () -> {
            //noinspection ConstantConditions
            inSnapshot.read(null);
        });
    }

    @Test
    void readSnapshot_WhenEmptyStream_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {});
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);

        Assertions.assertThrows(IOException.class, () -> inSnapshot.read(new HashMap<>()));
    }

    @Test
    void readSnapshot_WhenInvalidCount_ThrowError() {
        InputStream inputStream = makeInputStream(out -> out.writeInt(0));
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            Assertions.fail();
        } catch (IOException e) {
            Assertions.assertEquals("Invalid data: number of entries", e.getMessage());
        }
    }

    @Test
    void readSnapshot_WhenInvalidKeySize_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(-1);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            Assertions.fail();
        } catch (IOException e) {
            Assertions.assertEquals("Invalid data: key size", e.getMessage());
        }
    }

    @Test
    void readSnapshot_WhenInvalidKeyLength_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);

        Assertions.assertThrows(EOFException.class, () -> inSnapshot.read(new HashMap<>()));
    }

    @Test
    void readSnapshot_WhenInvalidValueSize_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
            out.writeByte(100);
            out.writeInt(-2);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        try {
            inSnapshot.read(new HashMap<>());
            Assertions.fail();
        } catch (IOException e) {
            Assertions.assertEquals("Invalid data: value size", e.getMessage());
        }
    }

    @Test
    void readSnapshot_WhenInvalidValueLength_ThrowError() {
        InputStream inputStream = makeInputStream(out -> {
            out.writeInt(1);
            out.writeInt(1);
            out.writeByte(100);
            out.writeInt(1);
        });
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);
        Assertions.assertThrows(EOFException.class, () -> inSnapshot.read(new HashMap<>()));
    }

    @Test
    void closeInputSnapshot_WhenClose_ShouldTriggerCloseOfNestedStream() throws IOException {
        InputStream inputStream = mock(InputStream.class);
        MapSnapshot.In inSnapshot = new MapSnapshot.In(inputStream);

        inSnapshot.close();

        verify(inputStream, atLeastOnce()).close();
    }

    @Test
    void writeSnapshot_WhenWrite_ShouldPopulateOutputStream() throws IOException {
        byte[] key = new byte[] {0, 1, 2, 3, 4};
        byte[] value = new byte[] {5, 6, 7, 8, 9};
        Map<ByteArrayWrapper, byte[]> outMap = new HashMap<>();
        outMap.put(ByteUtil.wrap(key), value);

        ByteArrayOutputStream expectedOutputStream = new ByteArrayOutputStream();
        DigestOutputStream digestOutput = new DigestOutputStream(expectedOutputStream, HashUtil.makeMessageDigest());
        DataOutputStream dataOutputStream = new DataOutputStream(digestOutput);
        dataOutputStream.writeInt(1);
        dataOutputStream.writeInt(key.length);
        dataOutputStream.write(key);
        dataOutputStream.writeInt(value.length);
        dataOutputStream.write(value);
        byte[] digest = digestOutput.getMessageDigest().digest();
        dataOutputStream.writeInt(digest.length);
        dataOutputStream.write(digest);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MapSnapshot.Out outSnapshot = new MapSnapshot.Out(outputStream);

        outSnapshot.write(outMap);

        Assertions.assertArrayEquals(expectedOutputStream.toByteArray(), outputStream.toByteArray());
    }

    @Test
    void closeOutputSnapshot_WhenClose_ShouldTriggerCloseOfNestedStream() throws IOException {
        OutputStream outputStream = mock(OutputStream.class);
        MapSnapshot.Out outSnapshot = new MapSnapshot.Out(outputStream);

        outSnapshot.close();

        verify(outputStream, atLeastOnce()).close();
    }

    @Test
    void writeAndReadSnapshot_WhenReadWritten_ShouldMatch() throws IOException {
        Map<ByteArrayWrapper, byte[]> outMap = new HashMap<>();
        outMap.put(ByteUtil.wrap(new byte[0]), new byte[0]); // empty-byte keys and values are allowed
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

        Assertions.assertEquals(11, inMap.size());
        for (Map.Entry<ByteArrayWrapper, byte[]> entry : inMap.entrySet()) {
            Assertions.assertArrayEquals(entry.getValue(), outMap.get(entry.getKey()));
        }
    }

    private static byte[] composeByteArray(OutputStreamConsumer consumer) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            DigestOutputStream digestOutput = new DigestOutputStream(outputStream, HashUtil.makeMessageDigest());
            DataOutputStream dataOutput = new DataOutputStream(digestOutput);
            consumer.accept(dataOutput);
            byte[] digest = digestOutput.getMessageDigest().digest();
            dataOutput.writeInt(digest.length);
            dataOutput.write(digest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }

    private static InputStream makeInputStream(OutputStreamConsumer consumer) {
        return new ByteArrayInputStream(composeByteArray(consumer));
    }

    @FunctionalInterface
    public interface OutputStreamConsumer {
        void accept(DataOutputStream out) throws IOException;
    }
}
