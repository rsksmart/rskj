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

package co.rsk.rpc.modules.eth;

/**
 * Created by ajlopez on 28/02/2021.
 */
public class GasFinderConfiguration {
    private final long difference;
    private final long topGas;
    private final long upwardStep;

    public GasFinderConfiguration(long difference, long topGas, long upwardStep) {
        this.difference = difference;
        this.topGas = topGas;
        this.upwardStep = upwardStep;
    }

    public long getDifference() {
        return this.difference;
    }

    public long getTopGas() {
        return this.topGas;
    }

    public long getUpwardStep() {
        return this.upwardStep;
    }
}
