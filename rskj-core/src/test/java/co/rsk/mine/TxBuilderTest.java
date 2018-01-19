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

package co.rsk.mine;

import co.rsk.config.RskSystemProperties;
import co.rsk.net.BlockProcessor;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.Simples.SimpleEthereum;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;

public class TxBuilderTest {

    private final RskSystemProperties config = new RskSystemProperties();

    @Test
    public void createBasicTransaction() {
        SimpleEthereum rsk = new SimpleEthereum();
        TxBuilder builder = new TxBuilder(config, rsk, null, (Repository) rsk.repository);

        BigInteger gasPrice = BigInteger.ONE;
        BigInteger gasLimit = BigInteger.valueOf(21000);
        BigInteger nonce = BigInteger.TEN;

        Transaction tx = builder.createNewTransaction(gasPrice, gasLimit, nonce);

        Assert.assertEquals(gasLimit, new BigInteger(1, tx.getGasLimit()));
        Assert.assertEquals(gasPrice, tx.getGasPrice().asBigInteger());
        Assert.assertEquals(nonce, new BigInteger(1, tx.getNonce()));
    }

    @Test
    public void createAndBroadcastTransaction() {
        SimpleEthereum rsk = new SimpleEthereum();
        BlockProcessor blockProcessor = Mockito.mock(BlockProcessor.class);
        TxBuilder builder = new TxBuilder(config, rsk, blockProcessor, (Repository) rsk.repository);

        BigInteger nonce = BigInteger.TEN;

        try {
            builder.createNewTx(nonce);
        }
        catch (InterruptedException ex){
            Assert.fail();
        }
    }
}
