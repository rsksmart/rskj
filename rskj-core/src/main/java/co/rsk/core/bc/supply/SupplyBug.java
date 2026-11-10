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
 */
package co.rsk.core.bc.supply;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A deliberate supply bug, used to prove that {@link SupplyConservationCheck} actually fires.
 *
 * <p>Real blocks are not expected to create rBTC, so the check would otherwise never be exercised
 * end to end. Enabling this makes block execution credit an account with currency that came from
 * nowhere, which is exactly the condition the check exists to detect.
 *
 * <p><b>This must never run outside a test or a deliberately misconfigured private node.</b> It is
 * off unless {@code --add-supply-bug} is passed, {@link #DISABLED} is a shared instance that can do
 * nothing at all, and {@link #mintFromNowhere} is the only method that touches state. It is
 * additionally refused on mainnet, where enabling it could only ever do harm.
 */
public final class SupplyBug {

    private static final Logger logger = LoggerFactory.getLogger("supplyconservation");

    /** The only instance that can be obtained without explicitly asking for the bug. */
    public static final SupplyBug DISABLED = new SupplyBug(false);

    /** The account credited out of thin air. Chosen to be recognisable in logs and test output. */
    public static final RskAddress BENEFICIARY =
            new RskAddress("00000000000000000000000000000000baadf00d");

    /** How much is conjured, in weis. Small enough to be harmless, large enough to be unmistakable. */
    public static final Coin MINTED_AMOUNT = Coin.valueOf(1_000_000_000L);

    private final boolean enabled;

    private SupplyBug(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns an enabled bug, or {@link #DISABLED} if the flag was not given.
     *
     * @param enabled    whether {@code --add-supply-bug} was passed
     * @param mainnet    whether this node is on mainnet, where the bug is refused outright
     * @throws IllegalStateException if the bug was requested on mainnet
     */
    public static SupplyBug create(boolean enabled, boolean mainnet) {
        if (!enabled) {
            return DISABLED;
        }

        if (mainnet) {
            throw new IllegalStateException(
                    "--add-supply-bug deliberately creates rBTC out of nothing and cannot be enabled on mainnet");
        }

        logger.error("################################################################");
        logger.error("# --add-supply-bug IS ENABLED.                                 #");
        logger.error("# Block execution will create rBTC from nothing, so that the   #");
        logger.error("# supply conservation check can be seen to reject a block.     #");
        logger.error("# This node produces INVALID blocks. Never use it in           #");
        logger.error("# production.                                                  #");
        logger.error("################################################################");

        return new SupplyBug(true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Credits {@link #BENEFICIARY} with {@link #MINTED_AMOUNT} out of nothing, if and only if the
     * bug is enabled. Does nothing otherwise.
     */
    public void mintFromNowhere(Repository track) {
        if (!enabled) {
            return;
        }

        logger.error("Injecting supply bug: crediting {} with {} weis from nowhere",
                BENEFICIARY, MINTED_AMOUNT.asBigInteger());
        track.addBalance(BENEFICIARY, MINTED_AMOUNT);
    }
}
