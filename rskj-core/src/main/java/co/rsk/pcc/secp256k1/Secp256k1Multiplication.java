/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.pcc.secp256k1;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.crypto.signature.Secp256k1Service;
import org.ethereum.vm.GasCost;

public class Secp256k1Multiplication extends Secp256k1PrecompiledContract {

    private static final int EC_256_MULTIPLICATION_GAS_COST = 3000;

    public Secp256k1Multiplication(ActivationConfig.ForBlock activations, Secp256k1Service secp256k1Service) {
        super(activations, secp256k1Service);
    }

    @Override
    public long getGasForData(byte[] data) {
        return GasCost.toGas(EC_256_MULTIPLICATION_GAS_COST);
    }

    @Override
    protected byte[] executeOperation(byte[] data) {
        return secp256k1Service.mul(data);
    }
}
