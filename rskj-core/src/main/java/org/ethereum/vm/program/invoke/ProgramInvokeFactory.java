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

import co.rsk.core.Coin;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.Program;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
public interface ProgramInvokeFactory {

    ProgramInvoke createProgramInvoke(Transaction tx, int txindex, Block block,
                                      Repository repository, BlockStore blockStore,
                                      SignatureCache signatureCache);

    ProgramInvoke createProgramInvoke(Program program, DataWord toAddress, DataWord callerAddress,
                                      DataWord inValue, long inGas,
                                      Coin balanceInt, byte[] dataIn,
                                      Repository repository, BlockStore blockStore,
                                      boolean isStaticCall, boolean byTestingSuite);
}
