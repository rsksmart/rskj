/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.config;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.config.*;
import co.rsk.core.BlockDifficulty;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * Describes different constants specific for a blockchain
 */
public class Constants {
    public static final byte MAINNET_CHAIN_ID = (byte) 30;
    public static final byte TESTNET_CHAIN_ID = (byte) 31;
    public static final byte DEVNET_CHAIN_ID = (byte) 32;
    public static final byte REGTEST_CHAIN_ID = (byte) 33;

    private static final byte[] FALLBACKMINING_PUBKEY_0 = Hex.decode("041e2b148c024770e19c4f31db2233cac791583df95b4d14a5e9fd4b38dc8254b3048f937f169446b19d2eca40db1dd93fab34c0cd8a310afd6e6211f9a89e4bca");
    private static final byte[] FALLBACKMINING_PUBKEY_1 = Hex.decode("04b55031870df5de88bdb84f65bd1c6f8331c633e759caa5ac7cad3fa4f8a36791e995804bba1558ddcf330a67ff5bfa253fa1d8789735f97a97e849686527976e");
    private static final BigInteger SECP256K1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);
    private static final BigInteger TRANSACTION_GAS_CAP = BigDecimal.valueOf(Math.pow(2, 60)).toBigInteger();
    private static final BigInteger RSKIP156_DIF_BOUND_DIVISOR = BigInteger.valueOf(400);

    private final byte chainId;
    private final boolean seedCowAccounts;
    private final int durationLimit;
    private final BlockDifficulty minimumDifficulty;
    private final BlockDifficulty fallbackMiningDifficulty;
    private final BigInteger difficultyBoundDivisor;
    private final int newBlockMaxSecondsInTheFuture;
    public final BridgeConstants bridgeConstants;

    public Constants(
            byte chainId,
            boolean seedCowAccounts,
            int durationLimit,
            BlockDifficulty minimumDifficulty,
            BlockDifficulty fallbackMiningDifficulty,
            BigInteger difficultyBoundDivisor,
            int newBlockMaxSecondsInTheFuture,
            BridgeConstants bridgeConstants) {
        this.chainId = chainId;
        this.seedCowAccounts = seedCowAccounts;
        this.durationLimit = durationLimit;
        this.minimumDifficulty = minimumDifficulty;
        this.fallbackMiningDifficulty = fallbackMiningDifficulty;
        this.difficultyBoundDivisor = difficultyBoundDivisor;
        this.newBlockMaxSecondsInTheFuture = newBlockMaxSecondsInTheFuture;
        this.bridgeConstants = bridgeConstants;
    }

    public boolean seedCowAccounts() {
        return seedCowAccounts;
    }

    // Average Time between blocks
    public int getDurationLimit() {
        return durationLimit;
    }

    public BlockDifficulty getMinimumDifficulty() {
        return minimumDifficulty;
    }

    public BlockDifficulty getFallbackMiningDifficulty() {
        return fallbackMiningDifficulty;
    }

    public BigInteger getDifficultyBoundDivisor(ActivationConfig.ForBlock activationConfig) {
        // divisor used since inception until the RSKIP156
        if (activationConfig.isActive(ConsensusRule.RSKIP156)
                && getChainId() != Constants.REGTEST_CHAIN_ID) {
            // Unless we are in regtest, this RSKIP increments the difficulty divisor from 50 to 400
            return RSKIP156_DIF_BOUND_DIVISOR;
        }
        return difficultyBoundDivisor;
    }

    /**
     * EIP155: https://github.com/ethereum/EIPs/issues/155
     */
    public byte getChainId() {
        return chainId;
    }

    public int getNewBlockMaxSecondsInTheFuture() {
        return newBlockMaxSecondsInTheFuture;
    }

    public BridgeConstants getBridgeConstants() {
        return bridgeConstants;
    }

    public BigInteger getInitialNonce() {
        return BigInteger.ZERO;
    }

    public byte[] getFallbackMiningPubKey0() {
        return Arrays.copyOf(FALLBACKMINING_PUBKEY_0, FALLBACKMINING_PUBKEY_0.length);
    }

    public byte[] getFallbackMiningPubKey1() {
        return Arrays.copyOf(FALLBACKMINING_PUBKEY_1, FALLBACKMINING_PUBKEY_1.length);
    }

    public int getMaximumExtraDataSize() {
        return 32;
    }

    public int getMinGasLimit() {
        return 3000000;
    }

    public int getGasLimitBoundDivisor() {
        return 1024;
    }

    public int getExpDifficultyPeriod() {
        return 100000;
    }

    public int getUncleGenerationLimit() {
        return 7;
    }

    public int getUncleListLimit() {
        return 10;
    }

    public int getBestNumberDiffLimit() {
        return 100;
    }

    public BigInteger getMinimumPayableGas() {
        return BigInteger.valueOf(200000);
    }

    public BigInteger getFederatorMinimumPayableGas() {
        return BigInteger.valueOf(50000);
    }

    public static BigInteger getSECP256K1N() {
        return SECP256K1N;
    }

    public static BigInteger getTransactionGasCap() {
        return TRANSACTION_GAS_CAP;
    }

    public static int getMaxContractSize() {
        return 0x6000;
    }

    public static int getMaxAddressByteLength() {
        return 20;
    }

    public static Constants mainnet() {
        return new Constants(
                MAINNET_CHAIN_ID,
                false,
                14,
                new BlockDifficulty(BigInteger.valueOf((long) 14E15 / 2)),
                new BlockDifficulty(BigInteger.valueOf((long) 14E15)),
                BigInteger.valueOf(50),
                60,
                BridgeMainNetConstants.getInstance()
        );
    }

    public static Constants devnetWithFederation(List<BtcECKey> federationPublicKeys) {
        return new Constants(
                DEVNET_CHAIN_ID,
                false,
                14,
                new BlockDifficulty(BigInteger.valueOf(131072)),
                new BlockDifficulty(BigInteger.valueOf((long) 14E15)),
                BigInteger.valueOf(50),
                540,
                new BridgeDevNetConstants(federationPublicKeys)
        );
    }

    public static Constants testnet() {
        return new Constants(
                TESTNET_CHAIN_ID,
                false,
                14,
                new BlockDifficulty(BigInteger.valueOf(131072)),
                new BlockDifficulty(BigInteger.valueOf((long) 14E15)),
                BigInteger.valueOf(50),
                540,
                BridgeTestNetConstants.getInstance()
        );
    }

    public static Constants regtest() {
        return new Constants(
                REGTEST_CHAIN_ID,
                true,
                10,
                new BlockDifficulty(BigInteger.ONE),
                BlockDifficulty.ZERO,
                BigInteger.valueOf(2048),
                0,
                BridgeRegTestConstants.getInstance()
        );
    }

    public static Constants regtestWithFederation(List<BtcECKey> genesisFederationPublicKeys) {
        return new Constants(
                REGTEST_CHAIN_ID,
                true,
                10,
                new BlockDifficulty(BigInteger.ONE),
                BlockDifficulty.ZERO,
                BigInteger.valueOf(2048),
                0,
                new BridgeRegTestConstants(genesisFederationPublicKeys)
        );
    }
}
