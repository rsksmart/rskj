/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * @author Patricio Gallardo
 * @since 07.01.2021
 */
public abstract class BN128PrecompiledContract extends PrecompiledContracts.PrecompiledContract {

    private final ActivationConfig.ForBlock activations;

    protected BN128PrecompiledContract(ActivationConfig.ForBlock activations) {
        this.activations = activations;
    }

    /**
     * This is a Template Method, subclasses should override 'concreteExecute()' method.
     */
    @Override
    public byte[] execute(byte[] data) throws VMException {
        if (activations.isActive(ConsensusRule.RSKIP197)) {
            return unsafeExecute(data);
        } else {
            return safeExecute(data);
        }
    }

    /**
     * After RSKIP197 we can throw exception of {@link VMException} and they will properly handled as an error.
     */
    private byte[] unsafeExecute(byte[] data) throws VMException {
        if (data == null) {
            data = EMPTY_BYTE_ARRAY;
        }
        AltBN128 altBN128 = new AltBN128();
        int rs = concreteExecute(data, altBN128);
        if (rs < 0) {
            throw new VMException("Invalid result.");
        }
        return altBN128.getOutput();
    }

    /**
     * Before RSKIP197 there were no way of throwing an error, so it returned an empty byte array.
     */
    private byte[] safeExecute(byte[] data) {
        if (data == null) {
            data = EMPTY_BYTE_ARRAY;
        }
        AltBN128 altBN128 = new AltBN128();
        int rs = concreteExecute(data, altBN128);
        if (rs < 0) {
            return EMPTY_BYTE_ARRAY;
        }
        return altBN128.getOutput();
    }

    protected abstract int concreteExecute(byte[] data, AltBN128 altBN128);
}
