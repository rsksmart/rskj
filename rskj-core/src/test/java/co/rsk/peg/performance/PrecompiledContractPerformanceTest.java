/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.vm.VMPerformanceTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"squid:S2187"}) // extended by subclasses
public class PrecompiledContractPerformanceTest {
    private static List<ExecutionStats> statsList;
    private static boolean running = false;
    private static Mean averageNanosecondsPerGasUnit;

    @BeforeAll
     static void setRunning() {
        running = true;
    }

    @BeforeAll
     static void estimateReferenceCost() {
        // Run VM tests and average
        averageNanosecondsPerGasUnit = new Mean();
        VMPerformanceTest.ResultLogger resultLogger = (String name, VMPerformanceTest.PerfRes result) -> {
            long nanosecondsPerGasUnit = result.deltaTime_nS / result.gas;
            averageNanosecondsPerGasUnit.add(nanosecondsPerGasUnit);
        };
        VMPerformanceTest.runWithLogging(resultLogger);
        // Set reference cost on stats (getMax(), getMean() or getMin() can be used depending on the desired
        // reference value).
        ExecutionStats.nanosecondsPerGasUnit = averageNanosecondsPerGasUnit.getMean();
        System.out.println(String.format(
                "Reference cost: %d ns/gas (min: %d ns/gas, max: %d ns/gas)",
                ExecutionStats.nanosecondsPerGasUnit,
                averageNanosecondsPerGasUnit.getMin(),
                averageNanosecondsPerGasUnit.getMax()
        ));
    }

    @AfterAll
    static void printStats() {
        for (ExecutionStats stats : statsList) {
            System.out.println(stats.getPrintable());
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean addStats(ExecutionStats stats) {
        ensureStatsCreated();

        return statsList.add(stats);
    }

    private static void ensureStatsCreated() {
        if (statsList == null) {
            statsList = new ArrayList<>();
        }
    }
}
