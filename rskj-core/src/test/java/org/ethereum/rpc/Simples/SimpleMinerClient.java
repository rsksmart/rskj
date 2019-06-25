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

package org.ethereum.rpc.Simples;

import co.rsk.mine.MinerClient;

/** Created by Ruben on 14/06/2016. */
public class SimpleMinerClient implements MinerClient {

    public boolean isMining = false;

    public void start() {
        isMining = true;
    }

    public boolean mineBlock() {
        // Unused
        return false;
    }

    public void stop() {
        isMining = false;
    }

    public boolean isMining() {
        return isMining;
    }
}
