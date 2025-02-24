/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.jmh.utils.plan;

import co.rsk.core.types.bytes.Bytes;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Random;

@State(Scope.Benchmark)
public class DataPlan {

    private final byte[] data = new byte[1024];

    private Bytes bytes;

    private Random random;

    @Setup(Level.Trial)
    public void doSetup() {
        random = new Random(111);
        random.nextBytes(data);
        bytes = Bytes.of(data);
    }

    public byte[] getData() {
        return data;
    }

    public Bytes getBytes() {
        return bytes;
    }

    public int getNextRand(int bound) {
        return random.nextInt(bound);
    }
}
