/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package org.ethereum.core;

import java.math.BigInteger;

import java.util.Arrays;


public class TransactionArguments {

    public String from;
    public byte[] to;
    public BigInteger gas;
    public BigInteger gasLimit;
    public BigInteger gasPrice;
    public BigInteger value;
    public String data; // compiledCode
    public BigInteger nonce;
    public byte chainId;  //NOSONAR

    @Override
    public String toString() {
        return "TransactionArguments{" +
                "from='" + from + '\'' +
                ", to='" + Arrays.toString(to) + '\'' +
                ", gasLimit='" + ((gas != null)?gas:gasLimit) + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", value='" + value + '\'' +
                ", data='" + data + '\'' +
                ", nonce='" + nonce + '\'' +
                ", chainId='" + chainId + '\'' +
                '}';
    }
}