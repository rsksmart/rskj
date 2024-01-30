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

package org.ethereum.vm.program.invoke;

import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.DataWord;

/**
 * @author Roman Mandeleil
 * @since 03.06.2014
 */
public interface ProgramInvoke extends InvokeData {

    DataWord getBalance();

    DataWord getOriginAddress();

    DataWord getTxGasPrice();

    DataWord getPrevHash();

    DataWord getCoinbase();

    DataWord getTimestamp();

    DataWord getNumber();

    DataWord getTransactionIndex();

    DataWord getDifficulty();

    DataWord getGaslimit();

    DataWord getMinimumGasPrice();

    boolean byTransaction();

    boolean byTestingSuite();

    int getCallDeep();

    Repository getRepository();

    BlockStore getBlockStore();

    boolean isStaticCall();
}
