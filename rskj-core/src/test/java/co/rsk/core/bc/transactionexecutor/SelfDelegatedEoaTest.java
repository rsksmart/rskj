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
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Account;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.core.bc.BlockExecutorTest.createAccount;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.GAS_LIMIT;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.execute;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.executeLegacyCall;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.legacyCall;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.rskip545Active;
import static co.rsk.core.bc.transactionexecutor.DelegatedEoaTestSupport.saveCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelfDelegatedEoaTest {

    private static final Coin VALUE = Coin.valueOf(1_000);
    private static final DataWord SUCCESS_SLOT = DataWord.valueOf(0);

    private final TestSystemProperties config = rskip545Active();
    private Repository track;
    private Account alice;
    private Account authority;

    @BeforeEach
    void setUp() {
        track = createRepository().startTracking();
        alice = createAccount("selfDelegatedEoaAlice", track, Coin.valueOf(6_000_000));
        authority = createAccount("selfDelegatedEoaAuthority", track, Coin.ZERO);
    }

    @Test
    void legacyTx_toSelfDelegatedEoa_haltsOnTheIndicator() {
        // alice ──tx legacy + value──▶ Authority (code = ef0100‖Authority)
        delegate(authority, authority);
        track.commit();

        TransactionExecutor executor = execute(config, track, legacyCall(config, alice, authority, VALUE));

        assertHaltedOnTheIndicator(executor);
    }

    @Test
    void type4Tx_selfDelegatingTheEoaItCalls_haltsOnTheIndicator() {
        // alice ──tx type 4 + value, authorization Authority → Authority──▶ Authority
        track.commit();

        TransactionExecutor executor = execute(config, track, type4SelfDelegationCall());

        assertArrayEquals(indicatorOf(authority), track.getCode(authority.getAddress()), "the authorization is applied even though the call fails");
        assertHaltedOnTheIndicator(executor);
    }

    @Test
    void internalCall_toSelfDelegatedEoa_fails() {
        // alice ──tx legacy──▶ Caller ──CALL──▶ Authority (code = ef0100‖Authority)
        Account caller = createAccount("selfDelegatedEoaCaller", track, Coin.ZERO);
        saveCode(track, caller, callerCode(authority, Coin.ZERO));
        delegate(authority, authority);
        track.commit();

        executeLegacyCall(config, track, alice, caller);

        assertNull(track.getStorageValue(caller.getAddress(), SUCCESS_SLOT), "the CALL must return 0");
    }

    @Test
    void internalCallWithValue_toSelfDelegatedEoa_failsAndKeepsTheValue() {
        // alice ──tx legacy──▶ Caller ──CALL + value──▶ Authority (code = ef0100‖Authority)
        Account caller = createAccount("selfDelegatedEoaCaller", track, VALUE);
        saveCode(track, caller, callerCode(authority, VALUE));
        delegate(authority, authority);
        track.commit();

        executeLegacyCall(config, track, alice, caller);

        assertNull(track.getStorageValue(caller.getAddress(), SUCCESS_SLOT), "the CALL must return 0");
        assertEquals(VALUE, track.getBalance(caller.getAddress()), "Caller keeps the value");
        assertEquals(Coin.ZERO, track.getBalance(authority.getAddress()), "Authority is not credited");
    }

    @Test
    void internalCallWithValue_toEoaDelegatedToAStoppingContract_succeeds() {
        Account caller = createAccount("selfDelegatedEoaCaller", track, VALUE);
        Account stop = createAccount("selfDelegatedEoaStop", track, Coin.ZERO);
        saveCode(track, caller, callerCode(authority, VALUE));
        saveCode(track, stop, new byte[]{0x00});
        delegate(authority, stop);
        track.commit();

        executeLegacyCall(config, track, alice, caller);

        assertEquals(DataWord.ONE, track.getStorageValue(caller.getAddress(), SUCCESS_SLOT), "the CALL must return 1");
        assertEquals(VALUE, track.getBalance(authority.getAddress()));
    }

    private void assertHaltedOnTheIndicator(TransactionExecutor executor) {
        assertNotNull(executor.getResult().getException(), "0xEF is an invalid opcode");
        assertEquals(GAS_LIMIT, executor.getGasConsumed(), "an exceptional halt consumes all the gas");
        assertEquals(Coin.ZERO, track.getBalance(authority.getAddress()), "the value is not credited");
    }

    private void delegate(Account eoa, Account target) {
        saveCode(track, eoa, indicatorOf(target));
    }

    private static byte[] indicatorOf(Account target) {
        return DelegationCodeResolver.createDelegatedCode(target.getAddress());
    }

    private Transaction type4SelfDelegationCall() {
        byte chainId = config.getNetworkConstants().getChainId();
        Transaction tx = Transaction.builder()
                .type(TransactionType.TYPE_4)
                .chainId(chainId)
                .nonce(BigInteger.ZERO)
                .gasLimit(BigInteger.valueOf(GAS_LIMIT))
                .maxPriorityFeePerGas(Coin.valueOf(1))
                .maxFeePerGas(Coin.valueOf(1))
                .receiveAddress(authority.getAddress())
                .value(VALUE)
                .accessList(Rskip545TestSupport.EMPTY_ACCESS_LIST)
                .authorizationList(List.of(Rskip545TestSupport.createSignedAuthorization(
                        authority.getEcKey(), authority.getAddress(), BigInteger.ZERO, chainId)))
                .build();
        tx.sign(alice.getEcKey().getPrivKeyBytes());
        return tx;
    }

    /**
     * Caller's code:
     * <pre>{@code
     * assembly {
     *     sstore(0, call(0x0fffff, target, value, 0, 0, 0, 0))   // slot 0: 1 if the call succeeded, 0 if it failed
     * }
     * }</pre>
     */
    private static byte[] callerCode(Account target, Coin value) {
        String code = "6000" + "6000" + "6000" + "6000"         // outSize, outOffset, inSize, inOffset = 0
                + "7f" + Hex.toHexString(DataWord.valueOf(value.getBytes()).getData())   // PUSH32 value
                + "73" + target.getAddress().toHexString()     // PUSH20 target
                + "620fffff"                                    // PUSH3 gas
                + "f1"                                          // CALL
                + "600055"                                      // SSTORE(0, success)
                + "00";                                         // STOP
        return Hex.decode(code);
    }
}
