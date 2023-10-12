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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CombinedExecutionStats extends ExecutionStats {
    private List<ExecutionStats> statsList;

    public CombinedExecutionStats(String name, List<ExecutionStats> statsList) {
        super(name);
        this.statsList = new ArrayList<>();
        statsList.stream().forEach(stats -> this.add(stats));
    }

    public CombinedExecutionStats(String name) {
        this(name, Collections.emptyList());
    }

    public void add(ExecutionStats stats) {
        // Note that this yields weighted stats, as opposed
        // to the mean of the stats
        this.statsList.add(stats);
        this.executionTimes.addFrom(stats.executionTimes);
        this.realExecutionTimes.addFrom(stats.realExecutionTimes);
        this.slotsWritten.addFrom(stats.slotsWritten);
        this.slotsCleared.addFrom(stats.executionTimes);
    }

    public String getPrintable() {
        StringBuilder result = new StringBuilder();

        result.append(String.format("%s\n", super.getPrintable()));
        statsList.stream().forEach(stats ->
                result.append(String.format("\t\t%s\n", stats.getPrintable()))
        );

        // Get rid of the last return of carriage
        String printable = result.toString();
        return printable.substring(0, printable.length()-1);
    }
}
