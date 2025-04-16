/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

package co.rsk.metrics.jmx;

import co.rsk.metrics.profilers.MetricKind;

import java.math.BigInteger;

public class JmxMetric implements JmxMetricMBean {

    private final MetricKind kind;

    private final MetricAggregate aggregate;

    private long counter;
    private long duration;
    private BigInteger cumulativeDuration = BigInteger.ZERO;

    public JmxMetric(MetricKind kind, MetricAggregate aggregate) {
        this.kind = kind;
        this.aggregate = aggregate;
    }

    @Override
    public MetricKind getKind() {
        return this.kind;
    }

    @Override
    public synchronized long getDuration() {
        long counter = this.counter;
        if (counter > 0) {
            this.counter = 0;
            if (this.aggregate == MetricAggregate.AVG && counter > 1) {
                return this.duration / counter;
            }
        }
        return this.duration;
    }

    @Override
    public synchronized BigInteger getCumulativeDuration() {
        return this.cumulativeDuration;
    }

    public synchronized void updateDuration(long duration) {
        switch (this.aggregate) {
            case SUM:
                if (this.counter == 0) {
                    this.counter++;
                    this.duration = duration;
                } else {
                    this.duration = this.duration + duration;
                }
                break;
            case AVG:
                if (this.counter == 0) {
                    this.duration = duration;
                } else {
                    this.duration = this.duration + duration;
                }
                this.counter++;
                break;
            case MAX:
                if (this.counter == 0) {
                    this.counter++;
                    this.duration = duration;
                } else {
                    this.duration = Math.max(this.duration, duration);
                }
                break;
            case MIN:
                if (this.counter == 0) {
                    this.counter++;
                    this.duration = duration;
                } else {
                    this.duration = Math.min(this.duration, duration);
                }
                break;
            case LAST:
                this.duration = duration;
                break;
        }
        this.cumulativeDuration = this.cumulativeDuration.add(BigInteger.valueOf(duration));
    }
}
