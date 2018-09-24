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

import co.rsk.config.BridgeConstants;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
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
        Federation genesisFederation = mock(Federation.class);
        when(provider.getNewFederation())
                .thenReturn(null);
        when(blockchainNetConfig.getGenesisFederation())
                .thenReturn(genesisFederation);

        assertThat(federationSupport.getActiveFederation(), is(genesisFederation));
    }

    @Test
    public void whenOldFederationIsNullThenActiveFederationIsNewFederation() {
        Federation newFederation = mock(Federation.class);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(null);

        assertThat(federationSupport.getActiveFederation(), is(newFederation));
    }

    @Test
    public void whenOldAndNewFederationArePresentReturnOldFederationByActivationAge() {
        Federation newFederation = mock(Federation.class);
        Federation oldFederation = mock(Federation.class);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(executionBlock.getNumber())
                .thenReturn(80L);
        when(bridgeConstants.getFederationActivationAge())
                .thenReturn(10L);
        when(newFederation.getCreationBlockNumber())
                .thenReturn(75L);

        assertThat(federationSupport.getActiveFederation(), is(oldFederation));
    }

    @Test
    public void whenOldAndNewFederationArePresentReturnNewFederationByActivationAge() {
        Federation newFederation = mock(Federation.class);
        Federation oldFederation = mock(Federation.class);
        when(provider.getNewFederation())
                .thenReturn(newFederation);
        when(provider.getOldFederation())
                .thenReturn(oldFederation);
        when(executionBlock.getNumber())
                .thenReturn(80L);
        when(bridgeConstants.getFederationActivationAge())
                .thenReturn(10L);
        when(newFederation.getCreationBlockNumber())
                .thenReturn(65L);

        assertThat(federationSupport.getActiveFederation(), is(newFederation));
    }
}