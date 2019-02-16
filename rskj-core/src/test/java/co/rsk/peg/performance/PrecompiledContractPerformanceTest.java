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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.ArrayList;
import java.util.List;

public class PrecompiledContractPerformanceTest {
    private static List<ExecutionStats> statsList;
    private static boolean running = false;
    private static Mean averageNanosecondsPerGasUnit;

    @BeforeClass
    public static void setRunning() {
        running = true;
    }

    @BeforeClass
    public static void estimateReferenceCost() {
        // Run VM tests and average
        averageNanosecondsPerGasUnit = new Mean();
        VMPerformanceTest.ResultLogger resultLogger = (String name, VMPerformanceTest.PerfRes result) -> {
            // deltaTime is measured in nanoseconds and, differently from the gas value, it is
            // already divided by the programCloneCount, which means it represents the average execution
            // time of a single VM *instruction* (each *instruction* is actually a n*PUSH + OP + m*POP program
            // that measures the cost of pushing n operands to the stack, executing the operation and then
            // popping m results from the stack).
            long nanosecondsPerGasUnit = result.deltaTime / (result.gas / result.programCloneCount);
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

    @AfterClass
    public static void printStats() {
        for (ExecutionStats stats : statsList) {
            System.out.println(stats.getPrintable());
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static void addStats(ExecutionStats stats) {
        ensureStatsCreated();
        statsList.add(stats);
    }

    private static void ensureStatsCreated() {
        if (statsList == null) {
            statsList = new ArrayList<>();
        }
    }
}
