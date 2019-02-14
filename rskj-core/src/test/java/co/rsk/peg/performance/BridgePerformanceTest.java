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

import co.rsk.vm.VMPerformanceTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.ArrayList;
import java.util.List;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        ReleaseBtcTest.class,
        UpdateCollectionsTest.class,
        ReceiveHeadersTest.class,
        RegisterBtcTransactionTest.class,
        AddSignatureTest.class,
        BtcBlockchainTest.class,
        LockTest.class,
        ActiveFederationTest.class,
        RetiringFederationTest.class,
        PendingFederationTest.class,
        FederationChangeTest.class,
        VoteFeePerKbChangeTest.class,
        GetFeePerKbTest.class,
        LockWhitelistTest.class,
        StateForBtcReleaseClientTest.class,
        GetBtcTransactionConfirmations.class
})
@Ignore
public class BridgePerformanceTest {
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
            long nanosecondsPerGasUnit = result.deltaTime_nS / result.gas;
            averageNanosecondsPerGasUnit.add(nanosecondsPerGasUnit);
        };
        VMPerformanceTest.runWithLogging(resultLogger);
        // Set reference cost on stats (getMax(), getMean() or getMin() can be used depending on the desired
        // reference value).
        ExecutionStats.nanosecondsPerGasUnit = averageNanosecondsPerGasUnit.getMax();
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
