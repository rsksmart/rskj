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

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * UTXO provider that provides no UTXOs
 * Usable for wallets for which we're only interested in
 * monitoring incoming transactions.
 * @author Ariel Mendelzon
 */
public class EmptyUTXOProvider implements UTXOProvider {
    private static final Logger logger = LoggerFactory.getLogger("EmptyUTXOProvider");

    private final NetworkParameters params;

    public EmptyUTXOProvider(NetworkParameters params) {
        this.params = params;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
        return Collections.emptyList();
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
