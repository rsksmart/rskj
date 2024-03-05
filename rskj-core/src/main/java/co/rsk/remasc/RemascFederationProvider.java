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
package co.rsk.remasc;

import co.rsk.core.RskAddress;
import co.rsk.peg.FederationSupport;
import co.rsk.peg.federation.FederationMember;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;

/**
 * Created by ajlopez on 14/11/2017.
 */
public class RemascFederationProvider {

    private final FederationSupport federationSupport;
    private final ActivationConfig.ForBlock activations;

    public RemascFederationProvider(
            ActivationConfig.ForBlock activations,
            FederationSupport federationSupport) {
        this.federationSupport = federationSupport;
        this.activations = activations;
    }

    public int getFederationSize() {
        return this.federationSupport.getFederationSize();
    }

    public RskAddress getFederatorAddress(int n) {
        if(!activations.isActive(ConsensusRule.RSKIP415)) {
            return getRskAddressFromBtcKey(n);
        }
        return getRskAddressFromRskKey(n);
    }

    private RskAddress getRskAddressFromBtcKey(int n) {
        byte[] btcPublicKey = this.federationSupport.getFederatorBtcPublicKey(n);
        return getRskAddressFromKey(btcPublicKey);
    }

    private RskAddress getRskAddressFromRskKey(int n) {
        byte[] rskPublicKey = this.federationSupport.getFederatorPublicKeyOfType(n, FederationMember.KeyType.RSK);
        return getRskAddressFromKey(rskPublicKey);
    }

    private RskAddress getRskAddressFromKey(byte[] publicKey) {
        return new RskAddress(ECKey.fromPublicOnly(publicKey).getAddress());
    }

}
