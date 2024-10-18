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

package co.rsk.jmh.utils;

import co.rsk.core.types.bytes.Bytes;
import co.rsk.jmh.utils.plan.DataPlan;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 1, time = 5 /* secs */)
@Measurement(iterations = 3, time = 5 /* secs */)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class BenchmarkByteUtil {

    @Benchmark
    public void toHexString_Bouncycastle(DataPlan plan) {
        byte[] data = plan.getData();
        int off = plan.getNextRand(data.length);
        int len = plan.getNextRand(data.length - off);
        org.bouncycastle.util.encoders.Hex.toHexString(data, off, len);
    }

    @Benchmark
    public void toHexString_V2(DataPlan plan) {
        Bytes bytes = plan.getBytes();
        int bytesLen = bytes.length();
        int off = plan.getNextRand(bytesLen);
        int len = plan.getNextRand(bytesLen - off);
        bytes.toHexString(off, len);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BenchmarkByteUtil.class.getName())
                .forks(2)
                .build();

        new Runner(opt).run();
    }
}
