/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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

import co.rsk.core.bc.SuperBridgeEvent;
import co.rsk.crypto.Keccak256;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.BlockHeader;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.RLP;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class SuperChainUtils {

    private SuperChainUtils() { /* hidden */ }

    public static Optional<Boolean> isSuperBlock(Constants constants, ActivationConfig activationConfig, BlockHeader header) {
        if (!activationConfig.isActive(ConsensusRule.RSKIP481, header.getNumber())) {
            return Optional.empty();
        }

        BigInteger minSuperBlockPoWFactor = constants.getMinSuperBlockPoWFactor();
        Optional<BigInteger> powFactorBIOpt = DifficultyUtils.difficultyToPoWFactor(constants, header);

        return powFactorBIOpt.map(factor -> factor.compareTo(minSuperBlockPoWFactor) >= 0);
    }

    @Nonnull
    public static Keccak256 makeSuperChainDataHash(@Nullable Keccak256 superParentHash, long superBlockNumber,
                                                   @Nonnull List<BlockHeader> superUncles, int superUncleCount,
                                                   @Nullable SuperBridgeEvent event) {
        byte[] unclesListHash = HashUtil.keccak256(BlockHeader.getUnclesEncodedEx(superUncles));
        return makeSuperChainDataHash(superParentHash, superBlockNumber,
                new Keccak256(unclesListHash), superUncleCount, event);
    }

    @Nonnull
    public static Keccak256 makeSuperChainDataHash(@Nullable Keccak256 superParentHash, long superBlockNumber,
                                                   @Nonnull Keccak256 superUnclesHash, int superUncleCount,
                                                   @Nullable SuperBridgeEvent event) {
        byte[] encoded = RLP.encodeList(
                RLP.encodeElement(Optional.ofNullable(superParentHash).map(Keccak256::getBytes).orElse(new byte[0])),
                RLP.encodeBigInteger(BigInteger.valueOf(superBlockNumber)),
                RLP.encodeElement(superUnclesHash.getBytes()),
                RLP.encodeBigInteger(BigInteger.valueOf(superUncleCount)),
                RLP.encodeElement(Optional.ofNullable(event).map(SuperBridgeEvent::getEncoded).orElse(new byte[0]))
        );

        return new Keccak256(HashUtil.keccak256(encoded));
    }
}
