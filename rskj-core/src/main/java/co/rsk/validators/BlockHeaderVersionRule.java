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

package co.rsk.validators;

import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Validates that a block header carries the version its height requires.
 * <p>
 * RSKIP-351 makes the header version part of consensus: blocks before its activation are
 * version 0, blocks from the activation on must be exactly version 1, until a later upgrade
 * raises the number (RSKIP-535 raises it to 2). The required version comes from
 * {@link ActivationConfig#getHeaderVersion(long)}, the same source the miner uses in
 * {@code BlockHeaderBuilder}, so the two cannot drift apart.
 * <p>
 * The genesis header is defined by configuration and is not validated.
 */
public class BlockHeaderVersionRule implements BlockValidationRule, BlockHeaderValidationRule {

    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");

    private final ActivationConfig activationConfig;

    public BlockHeaderVersionRule(ActivationConfig activationConfig) {
        this.activationConfig = Objects.requireNonNull(activationConfig);
    }

    @Override
    public boolean isValid(Block block) {
        return isValid(block.getHeader());
    }

    @Override
    public boolean isValid(BlockHeader header) {
        if (header.isGenesis()) {
            return true;
        }

        byte requiredVersion = activationConfig.getHeaderVersion(header.getNumber());
        byte version = header.getVersion();
        if (version != requiredVersion) {
            logger.warn("Invalid block: header version {} does not match the version {} required at block {}",
                    version, requiredVersion, header.getNumber());
            return false;
        }

        return true;
    }
}
