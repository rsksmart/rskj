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

package org.ethereum.core.genesis;

import co.rsk.db.ContractDetailsImpl;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.db.ContractDetails;
import org.ethereum.vm.DataWord;

/**
 * Created by mario on 13/01/17.
 */
public class ContractDetailsMapper {

    public ContractDetailsMapper() {
    }

    public ContractDetails mapFromContract(Contract contract) {
        ContractDetails contractDetails;

        contractDetails = new ContractDetailsImpl(
                null,
                ContractDetailsImpl.newStorage(),
                null
        );

        if (contract.getCode()!=null) {
            contractDetails.setCode(Hex.decode(contract.getCode()));
        }
        for (String key : contract.getData().keySet()) {
            String value = contract.getData().get(key);
            contractDetails.putBytes(new DataWord(Hex.decode(key)), Hex.decode(value));
        }
        return contractDetails;
    }
}
