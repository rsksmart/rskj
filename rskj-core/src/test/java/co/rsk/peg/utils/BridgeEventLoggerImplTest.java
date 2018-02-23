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

package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.Bridge;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.core.Block;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Test class for BridgeEventLoggerImpl.
 *
 * @author martin.medina
 */
public class BridgeEventLoggerImplTest {

    @Test
    public void logCommitFederation() {
        // Setup event logger
        BridgeConstants constantsMock = mock(BridgeConstants.class);
        when(constantsMock.getFederationActivationAge()).thenReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge());
        List<LogInfo> eventLogs = new LinkedList<>();
        BridgeEventLogger eventLogger = new BridgeEventLoggerImpl(constantsMock, eventLogs);

        // Setup parameters for test method call
        Block executionBlock = mock(Block.class);
        when(executionBlock.getTimestamp()).thenReturn(15005L);
        when(executionBlock.getNumber()).thenReturn(15L);

        List<BtcECKey> oldFederationKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")),
                BtcECKey.fromPublicOnly(Hex.decode("025eefeeeed5cdc40822880c7db1d0a88b7b986945ed3fc05a0b45fe166fe85e12")),
                BtcECKey.fromPublicOnly(Hex.decode("03c67ad63527012fd4776ae892b5dc8c56f80f1be002dc65cd520a2efb64e37b49"))
        );

        List<FederationMember> oldFederationMembers = oldFederationKeys.stream().map(pk ->
                new FederationMember(pk, ECKey.fromPublicOnly(pk.getPubKey()))
        ).collect(Collectors.toList());

        Federation oldFederation = new Federation(oldFederationMembers,
                Instant.ofEpochMilli(15005L), 15L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        List<BtcECKey> newFederationKeys = Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("0346cb6b905e4dee49a862eeb2288217d06afcd4ace4b5ca77ebedfbc6afc1c19d")),
                BtcECKey.fromPublicOnly(Hex.decode("0269a0dbe7b8f84d1b399103c466fb20531a56b1ad3a7b44fe419e74aad8c46db7")),
                BtcECKey.fromPublicOnly(Hex.decode("026192d8ab41bd402eb0431457f6756a3f3ce15c955c534d2b87f1e0372d8ba338"))
        );

        List<FederationMember> newFederationMembers = newFederationKeys.stream().map(pk ->
                new FederationMember(pk, ECKey.fromPublicOnly(pk.getPubKey()))
        ).collect(Collectors.toList());

        Federation newFederation = new Federation(newFederationMembers,
                Instant.ofEpochMilli(5005L), 0L, NetworkParameters.fromID(NetworkParameters.ID_REGTEST));

        // Do method call
        eventLogger.logCommitFederation(executionBlock, oldFederation, newFederation);

        // Assert
        Assert.assertEquals(1, eventLogs.size());

        // Assert address that made the log
        LogInfo result = eventLogs.get(0);
        Assert.assertArrayEquals(PrecompiledContracts.BRIDGE_ADDR.getBytes(), result.getAddress());

        // Assert log topics
        Assert.assertEquals(1, result.getTopics().size());
        Assert.assertEquals(Bridge.COMMIT_FEDERATION_TOPIC, result.getTopics().get(0));

        // Assert log data
        Assert.assertNotNull(result.getData());
        List<RLPElement> rlpData = RLP.decode2(result.getData());
        Assert.assertEquals(1 , rlpData.size());
        RLPList dataList = (RLPList)rlpData.get(0);
        Assert.assertEquals(3, dataList.size());

        // Assert old federation data
        RLPList oldFedData = (RLPList) dataList.get(0);
        Assert.assertEquals(2, oldFedData.size());
        Assert.assertArrayEquals(oldFederation.getAddress().getHash160(), oldFedData.get(0).getRLPData());

        RLPList oldFedPubKeys = (RLPList) oldFedData.get(1);
        Assert.assertEquals(4, oldFedPubKeys.size());
        for(int i = 0; i < 4; i++) {
            Assert.assertEquals(oldFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(oldFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation data
        RLPList newFedData = (RLPList) dataList.get(1);
        Assert.assertEquals(2, newFedData.size());
        Assert.assertArrayEquals(newFederation.getAddress().getHash160(), newFedData.get(0).getRLPData());

        RLPList newFedPubKeys = (RLPList) newFedData.get(1);
        Assert.assertEquals(3, newFedPubKeys.size());
        for(int i = 0; i < 3; i++) {
            Assert.assertEquals(newFederation.getBtcPublicKeys().get(i), BtcECKey.fromPublicOnly(newFedPubKeys.get(i).getRLPData()));
        }

        // Assert new federation activation block number
        Assert.assertEquals(15L + BridgeRegTestConstants.getInstance().getFederationActivationAge(), Long.valueOf(new String(dataList.get(2).getRLPData(), StandardCharsets.UTF_8)).longValue());
    }
}
