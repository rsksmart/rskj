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

package co.rsk.peg.performance;

import java.util.Optional;

public class ExecutionStats {
    public String name;
    public Mean executionTimes;
    public Mean realExecutionTimes;
    public Mean slotsWritten;
    public Mean slotsCleared;
    public Mean getGasForData;
    public Mean dataCost;

    public static long nanosecondsPerGasUnit = 0;

    public ExecutionStats(String name) {
        this.name = name;
        this.executionTimes = new Mean();
        this.realExecutionTimes = new Mean();
        this.slotsWritten = new Mean();
        this.slotsCleared = new Mean();
        this.getGasForData = new Mean();
        this.dataCost = new Mean();
    }

    public Optional<Long> getEstimatedGas() {
        if (nanosecondsPerGasUnit == 0) {
            return Optional.empty();
        }

        return Optional.of(executionTimes.getMean() / nanosecondsPerGasUnit);
    }

    public String getPrintable() {
        return String.format(
                "%-45s\tEstimated Gas by Cpu: %s\tEstimated Gas by Data: %d\tEstimated Gas Total: %d\tCurrrent getGasForData(): %d\tcpu(us): %d\treal(us): %d\twrt(slots): %d\tclr(slots): %d",
                name,
                getEstimatedGas().map(Object::toString).orElse("N/A"),
                dataCost.getMean(),
                getEstimatedGas().orElse(0L) + dataCost.getMean(),
                getGasForData.getMean(),
                executionTimes.getMean() / 1000,
                realExecutionTimes.getMean() / 1000,
                slotsWritten.getMean(),
                slotsCleared.getMean()
        );
    }
}
