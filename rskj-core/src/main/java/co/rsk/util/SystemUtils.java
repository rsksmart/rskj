/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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
package co.rsk.util;

import co.rsk.altbn128.cloudflare.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.NetworkUpgrade;
import org.ethereum.core.Blockchain;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SystemUtils {

    private static final List<String> SYSTEM_PROPERTIES = Arrays.asList(
            "java.version",
            "java.runtime.name", "java.runtime.version",
            "java.vm.name", "java.vm.version", "java.vm.vendor",
            "os.name", "os.version", "os.arch"
    );

    private SystemUtils() { /* hidden */ }

    /**
    * Wrapper for {@link Utils#isArm()} from external library
    */
    public static boolean isArm() {
        return Utils.isArm();
    }

    /**
     * Helper method that prints some system and runtime properties available to JVM.
     */
    public static void printSystemInfo(Logger logger) {
        String sysInfo = Stream.concat(
                systemPropsStream(),
                runtimePropsStream()
        ).map(p -> p.getKey() + ": " + p.getValue()).collect(Collectors.joining("\r  "));

        logger.info("System info:\r  {}", sysInfo);
    }

    private static Stream<Pair<String, String>> systemPropsStream() {
        return SYSTEM_PROPERTIES.stream().map(sp -> Pair.of(sp, System.getProperty(sp)));
    }

    private static Stream<Pair<String, String>> runtimePropsStream() {
        Runtime runtime = Runtime.getRuntime();
        return Stream.of(
                Pair.of("processors", Integer.toString(runtime.availableProcessors())),
                Pair.of("memory.free", Long.toString(runtime.freeMemory())),
                Pair.of("memory.max", Long.toString(runtime.maxMemory())),
                Pair.of("memory.total", Long.toString(runtime.totalMemory()))
        );
    }

    private static List<NetworkUpgrade> getDisabledNetworkUpgrades(Blockchain blockchain, ActivationConfig activationConfig) {
        final var latestBlock = blockchain.getBestBlock();

        return Arrays.stream(NetworkUpgrade.values())
                .filter(networkUpgrade -> !activationConfig.containsNetworkUpgrade(networkUpgrade) || !activationConfig.isActive(networkUpgrade, latestBlock.getNumber()))
                .toList();
    }

    public static void printDisabledNetworkUpgrades(@Nonnull Logger logger, @Nonnull Blockchain blockchain, @Nonnull ActivationConfig activationConfig) {
        final var disabledNetworkUpgrades = getDisabledNetworkUpgrades(blockchain, activationConfig);
        final var latestBlock = blockchain.getBestBlock();

        if (disabledNetworkUpgrades.isEmpty()) {
            logger.info("All network upgrades are active.");
        } else {
            logger.warn("Total number of disabled network upgrades: {}", disabledNetworkUpgrades.size());
            disabledNetworkUpgrades.forEach(disabledNetworkUpgrade -> logger.warn("WARNING: Network upgrade {} is DISABLED. Best block number is: {}.", disabledNetworkUpgrade.name(), latestBlock.getNumber()));
        }
    }
}
