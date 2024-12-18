/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.helpers;

import co.rsk.jmh.Config;

import java.util.concurrent.TimeUnit;

public class BenchmarkHelper {

    private BenchmarkHelper() {
    }

    public static void waitForBlocks(Config config) throws InterruptedException {
        // wait for blocks to be mined so nonce is updated
        int blockTimeInSec = config.getInt("blockTimeInSec");
        int numOfBlocksToWait = config.getInt("blocksToWait");
        TimeUnit.SECONDS.sleep((long) blockTimeInSec * numOfBlocksToWait);
    }

}
