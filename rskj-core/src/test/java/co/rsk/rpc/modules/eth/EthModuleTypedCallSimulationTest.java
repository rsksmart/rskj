/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.test.World;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.util.HexUtils;
import org.ethereum.core.Account;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.util.EthModuleTestUtils;
import org.ethereum.util.TransactionFactoryHelper;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class EthModuleTypedCallSimulationTest {

    private Account from;
    private RskAddress to;
    private EthModuleTestUtils.EthModuleGasEstimation eth;

    @BeforeEach
    void setup() {
        World world = new World(new TestSystemProperties());
        from = new AccountBuilder(world).name("from").build();
        to = new AccountBuilder(world).name("to").build().getAddress();
        eth = EthModuleTestUtils.buildBasicEthModuleForGasEstimation(world);
    }

    @Test
    void accessListAlonePresent_resolvesToType1_addsAccessListIntrinsicGas() {
        List<CallArguments.AccessListEntry> accessList = List.of(accessListEntry(to, "0x01"));
        byte[] encoded = AccessListCodec.encodeAccessList(accessList);

        long baseline = estimate(eth, null, null, null, null);
        long withAccessList = estimate(eth, accessList, null, null, null);

        assertEquals(encoded.length * GasCost.ACCESS_LIST_GAS_PER_BYTE, withAccessList - baseline, "an access list with no fee-cap fields must resolve to Type 1 and be charged RSKIP-546's 80 gas/byte");
    }

    @Test
    void feeCapsPresent_resolvesToType2_addsAccessListIntrinsicGasWhenGiven() {
        List<CallArguments.AccessListEntry> accessList = List.of(accessListEntry(to, "0x01"));
        byte[] encoded = AccessListCodec.encodeAccessList(accessList);

        long baseline = estimate(eth, null, "0x1", "0x2", null);
        long withAccessList = estimate(eth, accessList, "0x1", "0x2", null);

        assertEquals(encoded.length * GasCost.ACCESS_LIST_GAS_PER_BYTE, withAccessList - baseline, "fee-cap fields must resolve to Type 2 and its access list charged the same 80 gas/byte");
    }

    @Test
    void feeCapsPresentWithoutAccessList_estimatesSuccessfully() {
        long estimated = estimate(eth, null, "0x1", "0x2", null);
        assertTrue(estimated > 0, "expected a positive gas estimate for a fee-cap-only call");
    }

    @Test
    void explicitTypeField_isIgnoredWhenNoMatchingAttributesPresent() {
        long withoutType = estimate(eth, null, null, null, null);
        long withUnbackedType2 = estimate(eth, null, null, null, "0x2");

        assertEquals(withoutType, withUnbackedType2, "an unbacked `type` field must not change the simulated shape");
    }

    @Test
    void explicitTypeField_isIgnoredWhenAttributesImplyADifferentType() {
        List<CallArguments.AccessListEntry> accessList = List.of(accessListEntry(to, "0x01"));

        long asType1 = estimate(eth, accessList, null, null, null);
        long claimingType2 = estimate(eth, accessList, null, null, "0x2");

        assertEquals(asType1, claimingType2, "an access-list-only call must resolve to Type 1 regardless of a mismatched `type` field");
    }

    private long estimate(EthModuleTestUtils.EthModuleGasEstimation eth, List<CallArguments.AccessListEntry> accessList, String maxPriorityFeePerGas, String maxFeePerGas, String type) {
        CallArguments args = new CallArguments();
        args.setFrom(from.getAddress().toJsonString());
        args.setTo(to.toJsonString());
        if (accessList != null) {
            args.setAccessList(accessList);
        }
        if (maxPriorityFeePerGas != null) {
            args.setMaxPriorityFeePerGas(maxPriorityFeePerGas);
        }
        if (maxFeePerGas != null) {
            args.setMaxFeePerGas(maxFeePerGas);
        }
        if (type != null) {
            args.setType(type);
        }
        CallArgumentsParam params = TransactionFactoryHelper.toCallArgumentsParam(args);
        String hex = eth.estimateGas(params, new BlockIdentifierParam("latest"));
        return HexUtils.jsonHexToLong(hex);
    }

    private static CallArguments.AccessListEntry accessListEntry(RskAddress address, String storageKeySuffix) {
        CallArguments.AccessListEntry entry = new CallArguments.AccessListEntry();
        entry.setAddress(address.toJsonString());
        entry.setStorageKeys(List.of("0x" + "0".repeat(62) + storageKeySuffix.substring(2)));
        return entry;
    }
}
