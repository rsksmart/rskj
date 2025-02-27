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

package co.rsk.pcc.altBN128;

import co.rsk.pcc.altBN128.impls.AbstractAltBN128;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.vm.GasCost;

/**
 * Computes point addition on Barreto–Naehrig curve.
 * See {@link BN128Fp} for details<br/>
 * <br/>
 *
 * input data[]:<br/>
 * two points encoded as (x, y), where x and y are 32-byte left-padded integers,<br/>
 * if input is shorter than expected, it's assumed to be right-padded with zero bytes<br/>
 * <br/>
 *
 * output:<br/>
 * resulting point (x', y'), where x and y encoded as 32-byte left-padded integers<br/>
 *
 */

/**
 * @author Sebastian Sicardi
 * @since 10.09.2019
 */
public class BN128Addition extends BN128PrecompiledContract {

    public BN128Addition(ActivationConfig.ForBlock activations, AbstractAltBN128 altBN128) {
        super(activations, altBN128);
    }

    @Override
    public long getGasForData(byte[] data) {
        return GasCost.toGas(150);
    }

    @Override
    protected int concreteExecute(byte[] data) {
        return altBN128Lib.add(data, data.length);
    }
}
