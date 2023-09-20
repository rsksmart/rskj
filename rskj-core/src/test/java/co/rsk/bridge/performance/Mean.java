/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.bridge.performance;

public class Mean {
    private long total = 0;
    private int samples = 0;
    private long min;
    private long max;

    public void add(long value) {
        if (samples == 0 || value < min)
            min = value;
        if (samples == 0 || value > max)
            max = value;
        total += value;
        samples++;
    }

    public void addFrom(Mean otherMean) {
        // Note that this would yield a weighted mean, as
        // opposed to a mean of means
        this.total += otherMean.total;
        this.samples += otherMean.samples;
        this.max = Math.max(this.max, otherMean.max);
        this.min = Math.max(this.min, otherMean.min);
    }

    public long getMean() {
        return total / samples;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }
}
