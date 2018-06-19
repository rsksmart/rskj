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

package co.rsk.net.handler.txvalidator;

import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.TestSystemProperties;
import org.spongycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.regtest.RegTestConfig;
import org.ethereum.core.AccountState;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

public class TxValidatorIntrinsicGasLimitValidatorTest {

    private TestSystemProperties config;

    @Before
    public void setUp() {
        config = new TestSystemProperties();
        config.setBlockchainConfig(new RegTestConfig());
    }

    @Test
    public void validIntrinsicGasPrice() {
        Transaction tx1 = new Transaction(BigInteger.ZERO.toByteArray(),
                            BigInteger.ZERO.toByteArray(),
                            BigInteger.valueOf(21000).toByteArray(),
                            new ECKey().getAddress(),
                            BigInteger.ZERO.toByteArray(),
                            null,
                            config.getBlockchainConfig().getCommonConstants().getChainId());
        tx1.sign(new ECKey().getPrivKeyBytes());

        Transaction tx2 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.valueOf(30000).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                Hex.decode("0001"),
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx2.sign(new ECKey().getPrivKeyBytes());

        Transaction tx3 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.valueOf(21072).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                Hex.decode("0001"),
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx3.sign(new ECKey().getPrivKeyBytes());

        Transaction tx4 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                PrecompiledContracts.BRIDGE_ADDR.getBytes(),
                BigInteger.ZERO.toByteArray(),
                null,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        BridgeRegTestConstants bridgeRegTestConstants = BridgeRegTestConstants.getInstance();
        tx4.sign(bridgeRegTestConstants.getFederatorPrivateKeys().get(0).getPrivKeyBytes());

        TxValidatorIntrinsicGasLimitValidator tvigpv = new TxValidatorIntrinsicGasLimitValidator(config);

        Assert.assertTrue(tvigpv.validate(tx1, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertTrue(tvigpv.validate(tx2, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertTrue(tvigpv.validate(tx3, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertTrue(tvigpv.validate(tx4, new AccountState(), null, null, Long.MAX_VALUE, false));
    }



    @Test
    public void invalidIntrinsicGasPrice() {
        Transaction tx1 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.valueOf(21071).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                Hex.decode("0001"),
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx1.sign(new ECKey().getPrivKeyBytes());

        Transaction tx2 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.valueOf(20999).toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                null,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx2.sign(new ECKey().getPrivKeyBytes());

        Transaction tx3 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                Hex.decode("0001"),
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx3.sign(new ECKey().getPrivKeyBytes());

        Transaction tx4 = new Transaction(BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                BigInteger.ZERO.toByteArray(),
                new ECKey().getAddress(),
                BigInteger.ZERO.toByteArray(),
                null,
                config.getBlockchainConfig().getCommonConstants().getChainId());
        tx4.sign(new ECKey().getPrivKeyBytes());

        TxValidatorIntrinsicGasLimitValidator tvigpv = new TxValidatorIntrinsicGasLimitValidator(config);

        Assert.assertFalse(tvigpv.validate(tx1, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertFalse(tvigpv.validate(tx2, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertFalse(tvigpv.validate(tx3, new AccountState(), null, null, Long.MAX_VALUE, false));
        Assert.assertFalse(tvigpv.validate(tx4, new AccountState(), null, null, Long.MAX_VALUE, false));
    }
}
