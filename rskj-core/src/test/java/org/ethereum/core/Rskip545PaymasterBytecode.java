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
package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;

/**
 * Minimal paymaster-style delegate targets for RSKIP-545 integration tests.
 * Jump destinations are fixed hex literals verified by hand against opcode layout.
 */
final class Rskip545PaymasterBytecode {

    /** Writes {@code 1} to storage slot 0 and stops. */
    static final byte[] SUCCESS_MARKER = Hex.decode("600160005500");

    /** Unconditional revert (disabled / insolvent paymaster). */
    static final byte[] ALWAYS_REVERT = Hex.decode("600060fd");

    /** SimpleStorage-style setValue(42) calldata. */
    static final byte[] SET_VALUE_42 =
            Hex.decode("60fe47b1000000000000000000000000000000000000000000000000000000000000002a");

    /** SimpleStorage init code from Postman Section 11. */
    static final byte[] SIMPLE_STORAGE_INIT =
            Hex.decode("603980600b6000396000f36004361060205760003560e01c806360fe47b114602557632a1afcd914602d575b600080fd5b600435600055005b60005460005260206000f3");

    private Rskip545PaymasterBytecode() {
    }

    /**
     * Only the whitelisted authority EOA may invoke (checks {@code ADDRESS}); on success sets slot 0 to 1.
     * Runs in the delegated authority's storage context (RSKIP-545 set-code).
     */
    static byte[] userWhitelistGate(RskAddress allowedUser) {
        return Hex.decode(
                "30"
                + "73" + Hex.toHexString(allowedUser.getBytes())
                + "14601e57"
                + "600060fd"
                + "5b600160005500"
        );
    }

    /**
     * Only {@code msg.sender == allowedSponsor} may invoke; on success sets slot 0 to 1.
     */
    static byte[] sponsorGate(RskAddress allowedSponsor) {
        return Hex.decode(
                sponsorCheckPrefix(allowedSponsor, 0x1e)
                + "600060fd"
                + "5b600160005500"
        );
    }

    /**
     * Sponsor gate plus per-authority quota in slot 1 ({@code max > counter} required).
     */
    static byte[] quotaGate(RskAddress allowedSponsor, int maxQuota) {
        if (maxQuota < 1 || maxQuota > 255) {
            throw new IllegalArgumentException("maxQuota must fit in one byte for test bytecode");
        }
        return Hex.decode(
                sponsorCheckPrefix(allowedSponsor, 0x1e)
                + "600060fd"
                + "5b"
                + "600154"
                + "60" + String.format("%02x", maxQuota)
                + "10"
                + "602c57"
                + "600060fd"
                + "600154600101600155"
                + "600160005500"
        );
    }

    /**
     * Simulates a failing postOp: writes slot 0 then reverts (execution state rolls back).
     */
    static byte[] postOpFailure() {
        return Hex.decode("600160005560006000fd");
    }

    /**
     * Decrements prepaid gas credits in slot 0; sets slot 1 when a credit is consumed.
     */
    static byte[] prepaidCreditGate(RskAddress allowedSponsor) {
        return Hex.decode(
                sponsorCheckPrefix(allowedSponsor, 0x1e)
                + "600060fd"
                + "5b"
                + "60005415"
                + "601a57"
                + "600060fd"
                + "60005460019003600055"
                + "600160015500"
        );
    }

    /**
     * Sponsor-only helper that seeds three prepaid gas credits into slot 0 on the authority.
     */
    static byte[] prepaidCreditMintGate(RskAddress allowedSponsor) {
        return prepaidCreditMintGate(allowedSponsor, 3);
    }

    static byte[] prepaidCreditMintGate(RskAddress allowedSponsor, int credits) {
        if (credits < 1 || credits > 255) {
            throw new IllegalArgumentException("credits must fit in one byte for test bytecode");
        }
        return Hex.decode(
                sponsorCheckPrefix(allowedSponsor, 0x1e)
                + "600060fd"
                + "5b60" + String.format("%02x", credits) + "60005500"
        );
    }

    private static String sponsorCheckPrefix(RskAddress allowedSponsor, int okJumpDest) {
        return "33"
                + "73" + Hex.toHexString(allowedSponsor.getBytes())
                + "14"
                + String.format("60%02x57", okJumpDest);
    }

}
