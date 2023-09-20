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

package co.rsk.bridge;

import co.rsk.bitcoinj.core.*;

import java.util.List;

/**
 * Support class for the bridge btc wallet
 * @author Oscar Guindzberg
 */
public class RskUTXOProvider implements UTXOProvider {

    private final NetworkParameters params;
    private final List<UTXO> utxos;

    public RskUTXOProvider(NetworkParameters params, List<UTXO> utxos) {
        this.params = params;
        this.utxos = utxos;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        return utxos;
    }

    @Override
    public int getChainHeadHeight() throws UTXOProviderException {
        return Integer.MAX_VALUE;
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }

}
