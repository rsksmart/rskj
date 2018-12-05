/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FederationSupportTest {

    private FederationSupport federationSupport;
    private BlockchainNetConfig blockchainNetConfig;
    private Constants commonConstants;
    private BridgeConstants bridgeConstants;
    private BridgeStorageProvider provider;
    private Block executionBlock;


    @Before
    public void setUp() {
        provider = mock(BridgeStorageProvider.class);
        blockchainNetConfig = mock(BlockchainNetConfig.class);
        commonConstants = mock(Constants.class);
        when(blockchainNetConfig.getCommonConstants()).thenReturn(commonConstants);
        bridgeConstants = mock(BridgeConstants.class);
        when(commonConstants.getBridgeConstants()).thenReturn(bridgeConstants);
        executionBlock = mock(Block.class);
        federationSupport = new FederationSupport(provider, blockchainNetConfig, executionBlock);
    }

    @Test
    public void whenNewFederationIsNullThenActiveFederationIsGenesisFederation() {
        Federation genesisFederation = getNewFakeFederation(0);
        when(provider.getNewFederation())
                .thenReturn(null);
        when(blockchainNetConfig.getGenesisFederation())
                .thenReturn(genesisFederation);

        assertThat(federationSupport.getActiveFederation(), is(genesisFederation));
    }

    @Test
    public void whenOldFederationIsNullThenActiveFederationIsNewFederation() {
        Federation newFederation = getNewFakeFederation(100);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(null);

        assertThat(federationSupport.getActiveFederation(), is(newFederation));
    }

    @Test
    public void whenOldAndNewFederationArePresentReturnOldFederationByActivationAge() {
        Federation newFederation = getNewFakeFederation(75);
        Federation oldFederation = getNewFakeFederation(0);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(executionBlock.getNumber())
                .thenReturn(80L);
        when(bridgeConstants.getFederationActivationAge())
                .thenReturn(10L);

        assertThat(federationSupport.getActiveFederation(), is(oldFederation));
    }

    @Test
    public void whenOldAndNewFederationArePresentReturnNewFederationByActivationAge() {
        Federation newFederation = getNewFakeFederation(65);
        Federation oldFederation = getNewFakeFederation(0);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(executionBlock.getNumber())
                .thenReturn(80L);
        when(bridgeConstants.getFederationActivationAge())
                .thenReturn(10L);

        assertThat(federationSupport.getActiveFederation(), is(newFederation));
    }

    @Test
    public void getFederatorPublicKeys() {
        BtcECKey btcKey0 = BtcECKey.fromPublicOnly(Hex.decode("020000000000000000001111111111111111111122222222222222222222333333"));
        ECKey rskKey0 = new ECKey();
        ECKey mstKey0 = new ECKey();

        BtcECKey btcKey1 = BtcECKey.fromPublicOnly(Hex.decode("020000000000000000001111111111111111111122222222222222222222444444"));
        ECKey rskKey1 = new ECKey();
        ECKey mstKey1 = new ECKey();

        Federation theFederation = new Federation(
                Arrays.asList(
                        new FederationMember(btcKey0, rskKey0, mstKey0),
                        new FederationMember(btcKey1, rskKey1, mstKey1)
                ), Instant.ofEpochMilli(123), 456,
                NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
        when(provider.getNewFederation()).thenReturn(theFederation);

        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC), btcKey0.getPubKey()));
        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.BTC), btcKey1.getPubKey()));

        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorBtcPublicKey(0), btcKey0.getPubKey()));
        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorBtcPublicKey(1), btcKey1.getPubKey()));

        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK), rskKey0.getPubKey(true)));
        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.RSK), rskKey1.getPubKey(true)));

        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(0, FederationMember.KeyType.MST), mstKey0.getPubKey(true)));
        Assert.assertTrue(Arrays.equals(federationSupport.getFederatorPublicKeyOfType(1, FederationMember.KeyType.MST), mstKey1.getPubKey(true)));
    }

    private Federation getNewFakeFederation(long creationBlockNumber) {
        return new Federation(
                Collections.emptyList(), Instant.ofEpochMilli(123),
                creationBlockNumber, NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
    }
}