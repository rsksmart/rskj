/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.vm.program;

import co.rsk.core.types.ints.Uint24;
import java.util.*;

/**
 * @author mish, May 2020
 * object to hold a node's value length and last rent paid time
 * to keeping track of nodes created, modified during transaction execution
 */
public class RentData {
    private Uint24 valueLength;
    private long lastRentPaidTime; 

    public RentData(Uint24 valueLength, long lastRentPaidTime){
        this.valueLength = valueLength;
        this.lastRentPaidTime = lastRentPaidTime;
    }

    public Uint24 getValueLength(){
        return this.valueLength;
    }

    public long getLRPTime(){
        return this.lastRentPaidTime;
    }
}
