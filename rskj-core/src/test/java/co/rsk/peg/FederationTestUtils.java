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

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import org.ethereum.crypto.ECKey;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FederationTestUtils {

    public static Federation getFederation(Integer... federationMemberPks) {
        return new Federation(
            getFederationMembersFromPks(federationMemberPks),
            ZonedDateTime.parse("2017-06-10T02:30:01Z").toInstant(),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );
    }

    public static List<FederationMember> getFederationMembers(int memberCount) {
        List<FederationMember> result = new ArrayList<>();
        for (int i = 1; i <= memberCount; i++) {
            result.add(new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf((i) * 100)),
                ECKey.fromPrivate(BigInteger.valueOf((i) * 101)),
                ECKey.fromPrivate(BigInteger.valueOf((i) * 102))
            ));
        }
        result.sort(FederationMember.BTC_RSK_MST_PUBKEYS_COMPARATOR);
        return result;
    }

    public static List<FederationMember> getFederationMembersFromPks(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
            BtcECKey.fromPrivate(BigInteger.valueOf(n)),
            ECKey.fromPrivate(BigInteger.valueOf(n+1)),
            ECKey.fromPrivate(BigInteger.valueOf(n+2))
        )).collect(Collectors.toList());
    }

    public static List<FederationMember> getFederationMembersWithBtcKeys(List<BtcECKey> keys) {
        return keys.stream().map(btcKey ->
                new FederationMember(btcKey, new ECKey(), new ECKey())
        ).collect(Collectors.toList());
    }

    public static List<FederationMember> getFederationMembersWithKeys(List<BtcECKey> pks) {
        return pks.stream().map(pk -> getFederationMemberWithKey(pk)).collect(Collectors.toList());
    }

    public static FederationMember getFederationMemberWithKey(BtcECKey pk) {
        ECKey ethKey = ECKey.fromPublicOnly(pk.getPubKey());
        return new FederationMember(pk, ethKey, ethKey);
    }
}
