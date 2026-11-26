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
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Test;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.executeLegacyCall;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.rskip545Active;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.saveCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelegatedCallOpcodesTest {

    private static final byte OP_CALL = (byte) 0xf1;
    private static final byte OP_CALLCODE = (byte) 0xf2;
    private static final byte OP_DELEGATECALL = (byte) 0xf4;
    private static final byte OP_STATICCALL = (byte) 0xfa;

    private static final DataWord SUCCESS_SLOT = DataWord.valueOf(0);
    private static final DataWord RETURNED_WORD_SLOT = DataWord.valueOf(1);

    private static final DataWord DELEGATE_SLOT = DataWord.valueOf(0x10);
    private static final DataWord MARKER = DataWord.valueOf(0x2a);

    /**
     * PUSH1 2a PUSH1 00 MSTORE PUSH1 20 PUSH1 00 RETURN.
     * Solidity: {@code fallback() external { assembly { mstore(0, 0x2a) return(0, 32) } }}
     */
    private static final byte[] D_RETURNS_CONSTANT = Hex.decode("602a60005260206000f3");
    /**
     * PUSH1 2a PUSH1 10 SSTORE STOP: writes to slot 0x10 of the account it runs as.
     * Solidity: {@code fallback() external { assembly { sstore(0x10, 0x2a) } }}
     */
    private static final byte[] D_WRITES_STORAGE = Hex.decode("602a60105500");
    /**
     * ADDRESS PUSH1 00 MSTORE PUSH1 20 PUSH1 00 RETURN: returns the account it runs as.
     * Solidity: {@code fallback() external { assembly { mstore(0, address()) return(0, 32) } }}
     */
    private static final byte[] D_RETURNS_ADDRESS = Hex.decode("3060005260206000f3");

    @Test
    void delegatecall_toDelegatedEoa_runsDelegateCode() {
        Execution execution = callDelegatedEoa(OP_DELEGATECALL, D_RETURNS_CONSTANT);

        execution.assertCallSucceeded();
        assertEquals(MARKER, execution.callerStorage(RETURNED_WORD_SLOT));
    }

    @Test
    void delegatecall_toDelegatedEoa_writesToCallerStorage() {
        Execution execution = callDelegatedEoa(OP_DELEGATECALL, D_WRITES_STORAGE);

        execution.assertCallSucceeded();
        assertEquals(MARKER, execution.callerStorage(DELEGATE_SLOT), "DELEGATECALL runs the delegate as Caller");
        assertNull(execution.authorityStorage(DELEGATE_SLOT), "the delegated EOA only supplies the code");
    }

    @Test
    void callcode_toDelegatedEoa_runsDelegateCode() {
        Execution execution = callDelegatedEoa(OP_CALLCODE, D_RETURNS_CONSTANT);

        execution.assertCallSucceeded();
        assertEquals(MARKER, execution.callerStorage(RETURNED_WORD_SLOT));
    }

    @Test
    void callcode_toDelegatedEoa_writesToCallerStorage() {
        Execution execution = callDelegatedEoa(OP_CALLCODE, D_WRITES_STORAGE);

        execution.assertCallSucceeded();
        assertEquals(MARKER, execution.callerStorage(DELEGATE_SLOT), "CALLCODE runs the delegate as Caller");
        assertNull(execution.authorityStorage(DELEGATE_SLOT), "the delegated EOA only supplies the code");
    }

    @Test
    void staticcall_toDelegatedEoa_runsDelegateCode() {
        Execution execution = callDelegatedEoa(OP_STATICCALL, D_RETURNS_CONSTANT);

        execution.assertCallSucceeded();
        assertEquals(MARKER, execution.callerStorage(RETURNED_WORD_SLOT));
    }

    @Test
    void staticcall_toDelegatedEoa_runsInTheDelegatedEoaContext() {
        Execution execution = callDelegatedEoa(OP_STATICCALL, D_RETURNS_ADDRESS);

        execution.assertCallSucceeded();
        assertEquals(DataWord.valueOf(execution.authority.getBytes()), execution.callerStorage(RETURNED_WORD_SLOT));
    }

    @Test
    void staticcall_toDelegatedEoa_delegateWritingStorage_fails() {
        Execution execution = callDelegatedEoa(OP_STATICCALL, D_WRITES_STORAGE);

        execution.assertCallFailed();
        assertNull(execution.callerStorage(DELEGATE_SLOT));
        assertNull(execution.authorityStorage(DELEGATE_SLOT));
    }

    @Test
    void call_toDelegatedEoa_runsInTheDelegatedEoaContext() {
        Execution execution = callDelegatedEoa(OP_CALL, D_RETURNS_ADDRESS);

        execution.assertCallSucceeded();
        assertEquals(DataWord.valueOf(execution.authority.getBytes()), execution.callerStorage(RETURNED_WORD_SLOT));
    }

    private static Execution callDelegatedEoa(byte opcode, byte[] delegateCode) {
        TestSystemProperties config = rskip545Active();
        Repository track = createRepository().startTracking();

        Account alice = createAccount("delegatedCallOpcodesAlice", track, Coin.valueOf(6_000_000));
        Account caller = createAccount("delegatedCallOpcodesCaller", track, Coin.ZERO);
        Account authority = createAccount("delegatedCallOpcodesAuthority", track, Coin.ZERO);
        Account delegate = createAccount("delegatedCallOpcodesDelegate", track, Coin.ZERO);

        // alice ──tx legacy──▶ Caller ──OPCODE──▶ Authority (EOA delegated) ┄┄code┄┄▶ D (delegate)
        saveCode(track, caller, callerCode(opcode, authority));
        saveCode(track, authority, DelegationCodeResolver.createDelegatedCode(delegate.getAddress()));
        saveCode(track, delegate, delegateCode);
        track.commit();

        executeLegacyCall(config, track, alice, caller);

        return new Execution(track, caller.getAddress(), authority.getAddress());
    }

    /**
     * Caller's code, with {@code opcode} = DELEGATECALL:
     * <pre>{@code
     * assembly {
     *     let ok := delegatecall(0x0fffff, target, 0, 0, 0, 32) // D's returned word goes to memory[0..32]
     *     sstore(0, ok)         // slot 0: 1 if the call succeeded, 0 if it failed
     *     sstore(1, mload(0))   // slot 1: D's returned word
     * }
     * }</pre>
     * CALL and CALLCODE take an extra value argument (0 here).
     */
    private static byte[] callerCode(byte opcode, Account account) {
        RskAddress target = account.getAddress();
        boolean takesValue = opcode == OP_CALL || opcode == OP_CALLCODE;
        String code = "6020"                        // PUSH1 20  outSize
                + "6000"                            // PUSH1 00  outOffset
                + "6000"                            // PUSH1 00  inSize
                + "6000"                            // PUSH1 00  inOffset
                + (takesValue ? "6000" : "")        // PUSH1 00  value
                + "73" + target.toHexString()       // PUSH20    target
                + "620fffff"                        // PUSH3     gas
                + Hex.toHexString(new byte[]{opcode})
                + "600055"                          // SSTORE(0, success)
                + "600051" + "600155"               // SSTORE(1, MLOAD(0))
                + "00";                             // STOP
        return Hex.decode(code);
    }

    private record Execution(Repository track, RskAddress caller, RskAddress authority) {

        DataWord callerStorage(DataWord slot) {
            return track.getStorageValue(caller, slot);
        }

        DataWord authorityStorage(DataWord slot) {
            return track.getStorageValue(authority, slot);
        }

        void assertCallSucceeded() {
            assertEquals(DataWord.ONE, callerStorage(SUCCESS_SLOT), "the call must succeed");
        }

        void assertCallFailed() {
            DataWord success = callerStorage(SUCCESS_SLOT);
            assertTrue(success == null || success.isZero(), "the call must fail");
        }
    }
}
