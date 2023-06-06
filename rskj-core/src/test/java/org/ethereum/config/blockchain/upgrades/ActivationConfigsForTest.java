/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

package org.ethereum.config.blockchain.upgrades;

import com.typesafe.config.ConfigFactory;
import org.ethereum.config.SystemProperties;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"squid:S2187"}) // used from another class
public class ActivationConfigsForTest {

    private static final ActivationConfig REGTEST = read("config/regtest");

    private static List<ConsensusRule> getPaidBridgeTxsRskip() {
        return Arrays.asList(ConsensusRule.ARE_BRIDGE_TXS_PAID);
    }

    private static List<ConsensusRule> getOrchidRskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP85,
            ConsensusRule.RSKIP87,
            ConsensusRule.RSKIP88,
            ConsensusRule.RSKIP89,
            ConsensusRule.RSKIP90,
            ConsensusRule.RSKIP91,
            ConsensusRule.RSKIP92,
            ConsensusRule.RSKIP97,
            ConsensusRule.RSKIP98
        ));

        return rskips;
    }

    private static List<ConsensusRule> getOrchid060Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.add(ConsensusRule.RSKIP103);

        return rskips;
    }

    private static List<ConsensusRule> getWasabi100Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP103,
            ConsensusRule.RSKIP106,
            ConsensusRule.RSKIP110,
            ConsensusRule.RSKIP119,
            ConsensusRule.RSKIP120,
            ConsensusRule.RSKIP122,
            ConsensusRule.RSKIP123,
            ConsensusRule.RSKIP124,
            ConsensusRule.RSKIP125,
            ConsensusRule.RSKIP126,
            ConsensusRule.RSKIP132
        ));

        return rskips;
    }

    private static List<ConsensusRule> getBahamasRskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.add(ConsensusRule.RSKIP136);

        return rskips;
    }

    private static List<ConsensusRule> getTwoToThreeRskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.add(ConsensusRule.RSKIP150);

        return rskips;
    }

    private static List<ConsensusRule> getPapyrus200Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP137,
            ConsensusRule.RSKIP140,
            ConsensusRule.RSKIP143,
            ConsensusRule.RSKIP146,
            ConsensusRule.RSKIP150,
            ConsensusRule.RSKIP151,
            ConsensusRule.RSKIP152,
            ConsensusRule.RSKIP156,
            ConsensusRule.RSKIPUMM
        ));

        return rskips;
    }

    private static List<ConsensusRule> getIris300Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP153,
            ConsensusRule.RSKIP169,
            ConsensusRule.RSKIP170,
            ConsensusRule.RSKIP171,
            ConsensusRule.RSKIP174,
            ConsensusRule.RSKIP176,
            ConsensusRule.RSKIP179,
            ConsensusRule.RSKIP180,
            ConsensusRule.RSKIP181,
            ConsensusRule.RSKIP185,
            ConsensusRule.RSKIP186,
            ConsensusRule.RSKIP191,
            ConsensusRule.RSKIP197,
            ConsensusRule.RSKIP199,
            ConsensusRule.RSKIP200,
            ConsensusRule.RSKIP201,
            ConsensusRule.RSKIP218,
            ConsensusRule.RSKIP219,
            ConsensusRule.RSKIP220
        ));

        return rskips;
    }

    private static List<ConsensusRule> getHop400Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP271,
            ConsensusRule.RSKIP284,
            ConsensusRule.RSKIP290,
            ConsensusRule.RSKIP293,
            ConsensusRule.RSKIP294,
            ConsensusRule.RSKIP297
        ));

        return rskips;
    }

    private static List<ConsensusRule> getHop401Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP353,
            ConsensusRule.RSKIP357
        ));

        return rskips;
    }

    private static List<ConsensusRule> getFingerroot500Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP252,
            ConsensusRule.RSKIP326,
            ConsensusRule.RSKIP374,
            ConsensusRule.RSKIP375,
            ConsensusRule.RSKIP377,
            ConsensusRule.RSKIP383
        ));

        return rskips;
    }

    private static List<ConsensusRule> getTbd600Rskips() {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(Arrays.asList(
            ConsensusRule.RSKIP376
        ));

        return rskips;
    }

    public static ActivationConfig genesis() {
        return only();
    }

    public static ActivationConfig orchid() {
        return orchid(Collections.emptyList());
    }

    public static ActivationConfig orchid(List<ConsensusRule> except) {
        return enableTheseDisableThose(getOrchidRskips(), except);
    }

    public static ActivationConfig wasabi100() {
        return wasabi100(Collections.emptyList());
    }

    public static ActivationConfig wasabi100(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig papyrus200() {
        return papyrus200(Collections.emptyList());
    }

    public static ActivationConfig papyrus200(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig iris300() {
        return iris300(Collections.emptyList());
    }

    public static ActivationConfig iris300(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());
        rskips.addAll(getIris300Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig hop400() {
        return hop400(Collections.emptyList());
    }

    public static ActivationConfig hop400(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());
        rskips.addAll(getIris300Rskips());
        rskips.addAll(getHop400Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig hop401() {
        return hop401(Collections.emptyList());
    }

    public static ActivationConfig hop401(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());
        rskips.addAll(getIris300Rskips());
        rskips.addAll(getHop400Rskips());
        rskips.addAll(getHop401Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig fingerroot500() {
        return fingerroot500(Collections.emptyList());
    }

    public static ActivationConfig fingerroot500(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());
        rskips.addAll(getIris300Rskips());
        rskips.addAll(getHop400Rskips());
        rskips.addAll(getHop401Rskips());
        rskips.addAll(getFingerroot500Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig tbd600() {
        return tbd600(Collections.emptyList());
    }

    public static ActivationConfig tbd600(List<ConsensusRule> except) {
        List<ConsensusRule> rskips = new ArrayList<>();
        rskips.addAll(getPaidBridgeTxsRskip());
        rskips.addAll(getOrchidRskips());
        rskips.addAll(getOrchid060Rskips());
        rskips.addAll(getWasabi100Rskips());
        rskips.addAll(getBahamasRskips());
        rskips.addAll(getTwoToThreeRskips());
        rskips.addAll(getPapyrus200Rskips());
        rskips.addAll(getIris300Rskips());
        rskips.addAll(getHop400Rskips());
        rskips.addAll(getHop401Rskips());
        rskips.addAll(getTbd600Rskips());

        return enableTheseDisableThose(rskips, except);
    }

    public static ActivationConfig regtest() {
        return REGTEST;
    }

    public static ActivationConfig all() {
        return allBut();
    }

    /***
     * This method will disable all ConsensusRule by default. Accepts a list of ConsensusRule to enable and then and additional optional list to disable a few of those.
     * This is useful to test a specific network upgrade (e.g. Hop400) but disabling some specific ConsensusRules from it.
     * e.g. enableTheseDisableThose(getHop401Rskips(), getPaidBridgeTxsRskip())
     * This will enable all ConsensusRule up to hop401 but remain the ARE_BRIDGE_TXS_PAID consensus rule disabled
     * @param enableThese
     * @param disableThose nullable argument
     * @return
     */
    public static ActivationConfig enableTheseDisableThose(
        List<ConsensusRule> enableThese,
        @Nullable List<ConsensusRule> disableThose) {

        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class)
            .stream()
            .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        for (ConsensusRule consensusRule : enableThese) {
            consensusRules.put(consensusRule, 0L);
        }
        if (disableThose != null) {
            for (ConsensusRule consensusRule : disableThose) {
                consensusRules.put(consensusRule, -1L);
            }
        }

        return new ActivationConfig(consensusRules);
    }

    public static ActivationConfig allBut(ConsensusRule... upgradesToDisable) {
        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class)
            .stream()
            .collect(Collectors.toMap(Function.identity(), ignored -> 0L));
        for (ConsensusRule consensusRule : upgradesToDisable) {
            consensusRules.put(consensusRule, -1L);
        }

        return new ActivationConfig(consensusRules);
    }

    public static ActivationConfig only(ConsensusRule... upgradesToEnable) {
        Map<ConsensusRule, Long> consensusRules = EnumSet.allOf(ConsensusRule.class)
            .stream()
            .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        for (ConsensusRule consensusRule : upgradesToEnable) {
            consensusRules.put(consensusRule, 0L);
        }

        return new ActivationConfig(consensusRules);
    }

    public static ActivationConfig bridgeUnitTest() {
        Map<ConsensusRule, Long> allDisabled = EnumSet.allOf(ConsensusRule.class)
            .stream()
            .collect(Collectors.toMap(Function.identity(), ignored -> -1L));
        allDisabled.put(ConsensusRule.ARE_BRIDGE_TXS_PAID, 10L);

        return new ActivationConfig(allDisabled);
    }

    private static ActivationConfig read(String resourceBasename) {
        return ActivationConfig.read(ConfigFactory.load(resourceBasename).getConfig(SystemProperties.PROPERTY_BLOCKCHAIN_CONFIG));
    }
}
