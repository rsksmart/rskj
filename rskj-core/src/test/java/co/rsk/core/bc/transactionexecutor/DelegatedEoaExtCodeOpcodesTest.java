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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.executeLegacyCall;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.rskip545Active;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.saveCode;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DelegatedEoaExtCodeOpcodesTest {

    private static final DataWord EXTCODESIZE_SLOT = DataWord.valueOf(0);
    private static final DataWord EXTCODEHASH_SLOT = DataWord.valueOf(1);
    private static final DataWord EXTCODECOPY_SLOT = DataWord.valueOf(2);

    @ParameterizedTest(name = "{0}")
    @MethodSource("precompiles")
    void extcodeOpcodes_onEoaDelegatedToPrecompile_readTheIndicator(String name, RskAddress precompile) {
        Execution execution = readCodeOfEoaDelegatedTo(precompile);

        byte[] indicator = DelegationCodeResolver.createDelegatedCode(precompile);
        assertAll(
                () -> assertEquals(DataWord.valueOf(indicator.length), execution.callerStorage(EXTCODESIZE_SLOT), "EXTCODESIZE"),
                () -> assertEquals(DataWord.valueOf(Keccak256Helper.keccak256(indicator)), execution.callerStorage(EXTCODEHASH_SLOT), "EXTCODEHASH"),
                () -> assertEquals(DataWord.valueOf(Arrays.copyOf(indicator, 32)), execution.callerStorage(EXTCODECOPY_SLOT), "EXTCODECOPY"));
    }

    static Stream<Arguments> precompiles() {
        return Stream.of(
                Arguments.of("ECRECOVER", new RskAddress(PrecompiledContracts.ECRECOVER_ADDR_STR)),
                Arguments.of("IDENTITY", new RskAddress(PrecompiledContracts.IDENTITY_ADDR_STR)),
                Arguments.of("BRIDGE", new RskAddress(PrecompiledContracts.BRIDGE_ADDR_STR)));
    }

    private static Execution readCodeOfEoaDelegatedTo(RskAddress precompile) {
        TestSystemProperties config = rskip545Active();
        Repository track = createRepository().startTracking();

        Account alice = createAccount("extCodeOfDelegatedEoaAlice", track, Coin.valueOf(6_000_000));
        Account caller = createAccount("extCodeOfDelegatedEoaCaller", track, Coin.ZERO);
        Account authority = createAccount("extCodeOfDelegatedEoaAuthority", track, Coin.ZERO);

        // alice ──tx legacy──▶ Caller ──EXTCODE*──▶ Authority (EOA delegated) ┄┄code┄┄▶ precompile
        saveCode(track, caller, extCodeReaderCode(authority));
        saveCode(track, authority, DelegationCodeResolver.createDelegatedCode(precompile));
        track.commit();

        executeLegacyCall(config, track, alice, caller);

        return new Execution(track, caller.getAddress());
    }

    /**
     * Caller's code:
     * <pre>{@code
     * assembly {
     *     sstore(0, extcodesize(target))
     *     sstore(1, extcodehash(target))
     *     extcodecopy(target, 0, 0, 23)   // target's code goes to memory[0..23]
     *     sstore(2, mload(0))             // slot 2: those 23 bytes, left-aligned in a word
     * }
     * }</pre>
     */
    private static byte[] extCodeReaderCode(Account account) {
        String pushTarget = "73" + account.getAddress().toHexString();
        String code = pushTarget + "3b" + "600055"      // SSTORE(0, EXTCODESIZE(target))
                + pushTarget + "3f" + "600155"          // SSTORE(1, EXTCODEHASH(target))
                + "6017" + "6000" + "6000"              // PUSH1 17 size, PUSH1 00 codeOffset, PUSH1 00 memOffset
                + pushTarget + "3c"                     // EXTCODECOPY(target, 0, 0, 23)
                + "600051" + "600255"                   // SSTORE(2, MLOAD(0))
                + "00";                                 // STOP
        return Hex.decode(code);
    }

    private record Execution(Repository track, RskAddress caller) {

        DataWord callerStorage(DataWord slot) {
            return track.getStorageValue(caller, slot);
        }
    }
}
