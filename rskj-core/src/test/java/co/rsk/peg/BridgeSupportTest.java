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
package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.MerkleBranch;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructions;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.MerkleTreeUtils;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.utils.UnrefundablePeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.OneOffWhiteListEntry;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.exception.VMException;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.createUTXO;

class BridgeSupportTest {
    private final BridgeConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
    protected final NetworkParameters btcRegTestParams = bridgeConstantsRegtest.getBtcParams();
    private BridgeSupportBuilder bridgeSupportBuilder;

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    protected ActivationConfig.ForBlock activationsBeforeForks;
    protected ActivationConfig.ForBlock activationsAfterForks;

    protected SignatureCache signatureCache;

    @BeforeEach
    void setUpOnEachTest() {
        activationsBeforeForks = ActivationConfigsForTest.genesis().forBlock(0);
        activationsAfterForks = ActivationConfigsForTest.all().forBlock(0);
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    @Test
    void activations_is_set() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = bridgeSupportBuilder.withActivations(activations).build();

        Assertions.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }

    @Test
    void voteFeePerKbChange_nullFeeThrows() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any())).thenReturn(new ABICallElection(null));
        when(tx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(ByteUtil.leftPadBytes(new byte[]{0x43}, 20)));
        when(constants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any())).thenReturn(true);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        assertThrows(NullPointerException.class, () -> bridgeSupport.voteFeePerKbChange(tx, null));

        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any())).thenReturn(new ABICallElection(authorizer));
        when(tx.getSender(any(SignatureCache.class))).thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer()).thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any())).thenReturn(false);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any()))
            .thenReturn(new ABICallElection(authorizer));
        when(constants.getFeePerKbChangeAuthorizer())
            .thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any()))
            .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
            .thenReturn(2);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI), is(-1));
        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.ZERO), is(-1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        RskAddress sender = new RskAddress(senderBytes);

        when(provider.getFeePerKbElection(any()))
            .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender(any(SignatureCache.class)))
            .thenReturn(sender);
        when(constants.getFeePerKbChangeAuthorizer())
            .thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any()))
            .thenReturn(true);
        when(authorizer.isAuthorized(sender))
            .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
            .thenReturn(2);
        when(constants.getMaxFeePerKb())
            .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB)), is(1));
        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB + 1)), is(-2));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void voteFeePerKbChange_successfulVote() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        RskAddress sender = new RskAddress(senderBytes);

        when(provider.getFeePerKbElection(any()))
            .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender(any(SignatureCache.class)))
            .thenReturn(sender);
        when(constants.getFeePerKbChangeAuthorizer())
            .thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any()))
            .thenReturn(true);
        when(authorizer.isAuthorized(sender))
            .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
            .thenReturn(2);
        when(constants.getMaxFeePerKb())
            .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    void voteFeePerKbChange_successfulVoteWithFeeChange() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        RskAddress sender = new RskAddress(senderBytes);

        when(provider.getFeePerKbElection(any()))
            .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender(any(SignatureCache.class)))
            .thenReturn(sender);
        when(constants.getFeePerKbChangeAuthorizer())
            .thenReturn(authorizer);
        when(authorizer.isAuthorized(eq(tx), any()))
            .thenReturn(true);
        when(authorizer.isAuthorized(sender))
            .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
            .thenReturn(1);
        when(constants.getMaxFeePerKb())
            .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .build();

        MatcherAssert.assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider).setFeePerKb(Coin.CENT);
    }

    @Test
    void getLockingCap() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getInitialLockingCap()).thenReturn(Coin.SATOSHI);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(null).thenReturn(constants.getInitialLockingCap());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withActivations(activations)
            .build();

        // First time should also call setLockingCap as it was null
        assertEquals(constants.getInitialLockingCap(), bridgeSupport.getLockingCap());
        // Second time should just return the value
        assertEquals(constants.getInitialLockingCap(), bridgeSupport.getLockingCap());
        // Verify the set was called just once
        verify(provider, times(1)).setLockingCap(constants.getInitialLockingCap());
    }

    @Test
    void getActivePowpegRedeemScript_before_RSKIP293_activation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withActivations(activations)
            .build();

        assertEquals(Optional.empty(), bridgeSupport.getActivePowpegRedeemScript());
    }

    @Test
    void getActivePowpegRedeemScript_after_RSKIP293_activation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withActivations(activations)
            .build();

        assertTrue(bridgeSupport.getActivePowpegRedeemScript().isPresent());
        assertEquals(
            bridgeConstantsRegtest.getGenesisFederation().getRedeemScript(),
            bridgeSupport.getActivePowpegRedeemScript().get()
        );
    }

    @Test
    void increaseLockingCap_unauthorized() {
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class), any())).thenReturn(false);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withActivations(ActivationConfigsForTest.all().forBlock(0))
            .build();

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.SATOSHI));
    }

    @Test
    void increaseLockingCap_below_current_value() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(Coin.COIN);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class), any())).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withActivations(ActivationConfigsForTest.all().forBlock(0))
            .build();

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.SATOSHI));
    }

    @Test
    void increaseLockingCap_above_upper_value() {
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(Coin.COIN);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class), any())).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);

        int multiplier = 2;
        when(constants.getLockingCapIncrementsMultiplier()).thenReturn(multiplier);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withActivations(ActivationConfigsForTest.all().forBlock(0))
            .build();

        assertFalse(bridgeSupport.increaseLockingCap(mock(Transaction.class), Coin.COIN.multiply(multiplier).plus(Coin.SATOSHI)));
    }

    @Test
    void increaseLockingCap() {
        Coin lastValue = Coin.COIN;
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getLockingCap()).thenReturn(lastValue);

        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        when(authorizer.isAuthorized(any(Transaction.class), any())).thenReturn(true);

        BridgeConstants constants = mock(BridgeConstants.class);
        when(constants.getIncreaseLockingCapAuthorizer()).thenReturn(authorizer);
        int multiplier = 2;
        when(constants.getLockingCapIncrementsMultiplier()).thenReturn(multiplier);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withActivations(ActivationConfigsForTest.all().forBlock(0))
            .build();

        // Accepts up to the last value (increment 0)
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue));

        // Accepts up to the last value plus one
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.plus(Coin.SATOSHI)));

        // Accepts a value in the middle
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.plus(Coin.CENT)));

        // Accepts up to the last value times multiplier
        assertTrue(bridgeSupport.increaseLockingCap(mock(Transaction.class), lastValue.multiply(multiplier)));
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.ZERO, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.SATOSHI, Coin.ZERO, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5).add(Coin.SATOSHI), Coin.ZERO, Coin.ZERO, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    void isBtcTxHashAlreadyProcessed() throws IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withActivations(activations)
            .build();

        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(hash1));
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(hash2));
    }

    @Test
    void getBtcTxHashProcessedHeight() throws IOException {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProvider)
            .withActivations(activations)
            .build();

        assertEquals(Long.valueOf(1), bridgeSupport.getBtcTxHashProcessedHeight(hash1));
        assertEquals(Long.valueOf(-1), bridgeSupport.getBtcTxHashProcessedHeight(hash2));
    }

    @Test
    void eventLoggerLogLockBtc_before_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }

    @Test
    void eventLoggerLogLockBtc_after_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstantsRegtest.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
    }
    @Test
    void eventLoggerLogPeginRejectionEvents_before_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, never()).logUnrefundablePegin(any(BtcTransaction.class), any(UnrefundablePeginReason.class));
    }

    @Test
    void eventLoggerLogPeginRejectionEvents_after_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, atLeastOnce()).logUnrefundablePegin(any(BtcTransaction.class), any(UnrefundablePeginReason.class));
    }

    @Test
    void eventLoggerLogPeginBtc_before_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, never()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    void eventLoggerLogPeginBtc_after_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(mockBridgeStorageProvider.getLockWhitelist()).thenReturn(lockWhitelist);
        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        Block executionBlock = mock(Block.class);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, BitcoinTestUtils.createHash(1), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock,
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, atLeastOnce()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    @SuppressWarnings("squid:S5961")
    void registerBtcTransactionLockTxNotWhitelisted_before_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        //Creates federation 1
        List<BtcECKey> federation1Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation federation1 = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        //Creates federation 2
        List<BtcECKey> federation2Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03")));

        Federation federation2 = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcRegTestParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcRegTestParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            bridgeEventLogger,
            executionBlock,
            mockFactory,
            activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = PegTestUtils.getMockedRskTxWithHash("aa");
        Transaction rskTx2 = PegTestUtils.getMockedRskTxWithHash("bb");
        Transaction rskTx3 = PegTestUtils.getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assertions.assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getOldFederationBtcUTXOs().size());

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(5).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey1.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(1);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(7).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey3.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(2, pegoutBtcTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = pegoutBtcTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).collect(Collectors.toList());
        Assertions.assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        Assertions.assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        Assertions.assertEquals(0, releaseOutpoints.get(0).getIndex());
        Assertions.assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());

        // Third release tx should correspond to the 10 BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(2);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(10).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey2.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx2.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());

        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    @SuppressWarnings("squid:S5961")
    void registerBtcTransactionLockTxNotWhitelisted_after_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        //Creates federation 1
        List<BtcECKey> federation1Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation federation1 = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        //Creates federation 2
        List<BtcECKey> federation2Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03")));

        Federation federation2 = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcRegTestParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcRegTestParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);
        provider.setOldFederation(federation2);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            bridgeEventLogger,
            executionBlock,
            mockFactory,
            activations
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = PegTestUtils.getMockedRskTxWithHash("aa");
        Transaction rskTx2 = PegTestUtils.getMockedRskTxWithHash("bb");
        Transaction rskTx3 = PegTestUtils.getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        Assertions.assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getOldFederationBtcUTXOs().size());

        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(5).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey1.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
        // First Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx1.getHash().getBytes(), pegoutBtcTx, Coin.COIN.multiply(5));

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(1);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(7).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey3.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(2, pegoutBtcTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = pegoutBtcTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).collect(Collectors.toList());
        Assertions.assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        Assertions.assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        Assertions.assertEquals(0, releaseOutpoints.get(0).getIndex());
        Assertions.assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());
        // third Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx3.getHash().getBytes(), pegoutBtcTx, Coin.COIN.multiply(7));

        // Third release tx should correspond to the 10 BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(2);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(Coin.COIN.multiply(10).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(srcKey2.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx2.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());
        // Second Rsk tx corresponds to this release
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(rskTx2.getHash().getBytes(), pegoutBtcTx, Coin.COIN.multiply(10));

        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void registerBtcTransaction_sending_segwit_tx_twice_locks_just_once() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock mockedActivations = mock(ActivationConfig.ForBlock.class);
        when(mockedActivations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction txWithWitness = new BtcTransaction(btcRegTestParams);

        // first input spends P2PKH
        BtcECKey srcKey1 = new BtcECKey();
        txWithWitness.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // second input spends P2SH-P2PWKH (actually, just has a witness doesn't matter if it truly spends a witness for the test's sake)
        txWithWitness.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        txWithWitness.setWitness(0, txWit);

        List<BtcECKey> fedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        Federation fed = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
        );

        txWithWitness.addOutput(Coin.COIN.multiply(5), fed.getAddress());

        // Create the pmt without witness and calculate the block merkle root
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits,
            Collections.singletonList(txWithWitness.getHash()), 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits,
            Collections.singletonList(txWithWitness.getHash(true)), 1);

        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(fed);
        when(provider.getCoinbaseInformation(registerHeader.getHash())).thenReturn(new CoinbaseInformation(witnessMerkleRoot));
        when(provider.getLockWhitelist()).thenReturn(new LockWhitelist(new HashMap<>(), 0));
        when(provider.getLockingCap()).thenReturn(Coin.FIFTY_COINS);
        // mock an actual store for the processed txs
        HashMap<Sha256Hash, Long> processedTxs = new HashMap<>();
        doAnswer(a -> {
            processedTxs.put(a.getArgument(0), a.getArgument(1));
            return null;
        }).when(provider).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        doAnswer(a -> Optional.ofNullable(processedTxs.get(a.getArgument(0))))
            .when(provider).getHeightIfBtcTxhashIsAlreadyProcessed(any());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(666L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(mockedActivations)
            .build();

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Tx is locked
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithWitness.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(true), executionBlock.getNumber());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(false), executionBlock.getNumber());

        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, txWithWitness.bitcoinSerialize());
        txWithoutWitness.setWitness(0, null);
        assertFalse(txWithoutWitness.hasWitness());

        // Tx is NOT locked again!
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithoutWitness.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(txWithoutWitness.getHash(), executionBlock.getNumber());

        assertNotEquals(txWithWitness.getHash(true), txWithoutWitness.getHash());
    }

    @Test
    void callProcessFundsMigration_is_migrating_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    void callProcessFundsMigration_is_migrating_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(35, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        tx.sign(new ECKey().getPrivKeyBytes());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());
        PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(tx.getHash().getBytes(), entry.getBtcTransaction(), Coin.COIN);
    }

    @Test
    void callProcessFundsMigration_is_migrated_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    void callProcessFundsMigration_is_migrated_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());
        PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(tx.getHash().getBytes(), entry.getBtcTransaction(), Coin.COIN);
    }

    @Test
    void updateFederationCreationBlockHeights_before_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration1);

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(provider, never()).getNextFederationCreationBlockHeight();
        verify(provider, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(provider, never()).clearNextFederationCreationBlockHeight();
    }

    @Test
    void updateFederationCreationBlockHeights_after_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = bridgeConstantsRegtest.getGenesisFederation();

        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 35
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);
        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        sufficientUTXOsForMigration1.add(createUTXO(Coin.COIN, oldFederation.getAddress()));
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.empty());

        bridgeSupport.updateCollections(tx);

        verify(provider, times(1)).getNextFederationCreationBlockHeight();
        verify(provider, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(provider, never()).clearNextFederationCreationBlockHeight();

        when(provider.getNextFederationCreationBlockHeight()).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(provider, times(2)).getNextFederationCreationBlockHeight();
        verify(provider, times(1)).setActiveFederationCreationBlockHeight(1L);
        verify(provider, times(1)).clearNextFederationCreationBlockHeight();
    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_postRSKIP326_emitNewPegoutConfirmedEvent() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        long rskBlockNumber = 1L;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, rskBlockNumber)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(spiedBridgeConstants)
                .withProvider(provider)
                .withExecutionBlock(executionBlock)
                .withActivations(activations)
                .withEventLogger(eventLogger)
                .build();

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger, times(1)).logPegoutConfirmed(btcTx.getHash(), rskBlockNumber);

    }

    @Test
    void rskTxWaitingForSignature_preRSKIP326_noPegoutConfirmedEventEmitted() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(false);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        long rskBlockNumber = 1L;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, rskBlockNumber)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(spiedBridgeConstants)
                .withProvider(provider)
                .withExecutionBlock(executionBlock)
                .withActivations(activations)
                .withEventLogger(eventLogger)
                .build();

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger, times(0)).logPegoutConfirmed(btcTx.getHash(), rskBlockNumber);

    }

    @Test
    void rskTxWaitingForSignature_postRSKIP326NoTxWithEnoughConfirmation_pegoutConfirmedEventNotEmitted() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(new HashSet<>()));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(spiedBridgeConstants)
                .withProvider(provider)
                .withExecutionBlock(executionBlock)
                .withActivations(activations)
                .withEventLogger(eventLogger)
                .build();

        Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(Constants.REGTEST_CHAIN_ID)
                .value(DUST_AMOUNT)
                .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger, times(0)).logPegoutConfirmed(btcTx.getHash(), 1L);

    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_146_activation_if_release_transaction_doesnt_have_rstTxHash() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_uses_release_transaction_rstTxHash_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(rskTxHash));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_176_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private static Stream<BridgeConstants> provideBridgeConstants() {
        return Stream.of(BridgeRegTestConstants.getInstance(), BridgeTestNetConstants.getInstance(), BridgeMainNetConstants.getInstance());
    }

    @Test
    void rskTxWaitingForSignature_fail_adding_an_already_existing_key_after_rskip_375() throws IOException {
        // Arrange
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        // Set state to make concur a pegout migration tx and pegout batch creation on the same updateCollection
        Federation oldFederation = bridgeConstants.getGenesisFederation();
        Federation newFederation = new StandardMultisigFederation(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstants.getBtcParams()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getFeePerKb())
            .thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(pegoutsWaitingForConfirmations);

        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();
        when(provider.getPegoutsWaitingForSignatures())
            .thenReturn(pegoutWaitingForSignatures);

        // Federation change on going
        when(provider.getOldFederation())
            .thenReturn(oldFederation);
        when(provider.getNewFederation())
            .thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxos = PegTestUtils.createUTXOs(10, oldFederation.getAddress());
        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(utxos);

        List<UTXO> utxosNew = PegTestUtils.createUTXOs(10, newFederation.getAddress());
        when(provider.getNewFederationBtcUTXOs())
            .thenReturn(utxosNew);

        // Advance blockchain to migration phase
        BlockGenerator blockGenerator = new BlockGenerator();
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        // Assert two transactions are added to pegoutsWaitingForConfirmations, one pegout batch and one migration tx
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        // Get new fed wallet to identify the migration tx
        Wallet newFedWallet = BridgeUtils.getFederationNoSpendWallet(
            new Context(bridgeConstants.getBtcParams()),
            newFederation,
            false,
            null
        );

        PegoutsWaitingForConfirmations.Entry migrationTx = null;
        PegoutsWaitingForConfirmations.Entry pegoutBatchTx = null;

        // If all outputs are sent to the active fed then it's the migration tx; if not, it's the peg-out batch
        for (PegoutsWaitingForConfirmations.Entry entry : pegoutsWaitingForConfirmations.getEntries()) {
            List<TransactionOutput> walletOutputs = entry.getBtcTransaction().getWalletOutputs(newFedWallet);
            if (walletOutputs.size() == entry.getBtcTransaction().getOutputs().size()){
                migrationTx = entry;
            } else {
                pegoutBatchTx = entry;
            }
        }

        // Assert the two added pegouts have the same pegoutCreationRskTxHash
        assertEquals(migrationTx.getPegoutCreationRskTxHash(), pegoutBatchTx.getPegoutCreationRskTxHash());

        // Assert no pegouts were moved to pegoutsWaitingForSignatures
        assertEquals(0, pegoutWaitingForSignatures.size());

        // Advance blockchain to the height where both pegouts have enough confirmations to be moved to waitingForSignature
        rskCurrentBlock = blockGenerator.createBlock(184, 1);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .build();

        tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        // Get the transaction that was confirmed and the one that stayed unconfirmed
        PegoutsWaitingForConfirmations.Entry unconfirmedEntry = pegoutsWaitingForConfirmations.getEntries().iterator().next();
        BtcTransaction firstTransactionConfirmed = pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey());

        // Since only one pegout per updateCollection call is move to pegoutsWaitingForSignatures
        // assert one of the two pegout get moved to pegoutsWaitingForSignatures and one is left on pegoutsWaitingForConfirmation
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
        assertEquals(1, pegoutWaitingForSignatures.size());

        /*
            Advance blockchain one block so the bridge now will attempt to move the pegoutBatchTx to pegoutsWaitingForSignatures
            but will fail due to there's already an entry using that pegoutCreationRskTx as key. It's not allowed to
            overwrite pegoutsWaitingForSignatures entries.
         */
        rskCurrentBlock = blockGenerator.createBlock(185, 1);
        BridgeSupport bridgeSupportForFailingTx = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .build();

        Transaction throwsExceptionTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        assertThrows(IllegalStateException.class, () -> bridgeSupportForFailingTx.updateCollections(throwsExceptionTx));

        // assert both collections still without any change
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().contains(unconfirmedEntry));
        assertEquals(1, pegoutWaitingForSignatures.size());
        assertEquals(firstTransactionConfirmed, pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey()));

        // Now we remove the confirmed transaction from pegoutsWaitingForSignatures pretending it was signed,
        // to assert that in the next updateCollections call the unconfirmed transaction will be confirmed
        pegoutWaitingForSignatures.remove(pegoutWaitingForSignatures.firstKey());

        rskCurrentBlock = blockGenerator.createBlock(186, 1);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .build();

        tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        bridgeSupport.updateCollections(tx);

        assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
        assertEquals(1, pegoutWaitingForSignatures.size());
        assertEquals(unconfirmedEntry.getBtcTransaction(), pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey()));
    }

    @ParameterizedTest
    @MethodSource("provideBridgeConstants")
    void rskTxWaitingForSignature_uses_pegoutCreation_rskTxHash_after_rskip_375_activation(BridgeConstants bridgeConstants) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L + bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        bridgeSupport.updateCollections(tx);

        assertNull(provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(pegoutCreationRskTxHash));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @ParameterizedTest
    @MethodSource("provideBridgeConstants")
    void rskTxWaitingForSignature_override_entry_is_allowed_before_rskip_375_activation(BridgeConstants bridgeConstants) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(false);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTxNewEntryValue = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTxNewEntryValue, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L + bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations());

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        TreeMap<Keccak256, BtcTransaction> txsWaitingForSignatures = new TreeMap<>();
        BtcTransaction existingBtcTxEntryValue = mock(BtcTransaction.class);
        txsWaitingForSignatures.put(tx.getHash(), existingBtcTxEntryValue);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(txsWaitingForSignatures);

        bridgeSupport.updateCollections(tx);

        BtcTransaction updatedBtcTxEntryValue = provider.getPegoutsWaitingForSignatures().get(tx.getHash());

        assertEquals(btcTxNewEntryValue, updatedBtcTxEntryValue);
        assertNotEquals(existingBtcTxEntryValue, updatedBtcTxEntryValue);
        assertNull(provider.getPegoutsWaitingForSignatures().get(pegoutCreationRskTxHash));
        assertEquals(1, provider.getPegoutsWaitingForSignatures().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test()
    void rskTxWaitingForSignature_override_entry_attempt_after_rskip_375_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(BridgeRegTestConstants.getInstance());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        TreeMap<Keccak256, BtcTransaction> txsWaitingForSignatures = new TreeMap<>();

        txsWaitingForSignatures.put(pegoutCreationRskTxHash, btcTx);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(txsWaitingForSignatures);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        Transaction tx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();

        assertThrows(IllegalStateException.class, () -> bridgeSupport.updateCollections(tx));
    }

    @Test
    void when_registerBtcTransaction_sender_not_recognized_before_rskip170_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
                signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_sender_not_recognized_after_rskip170_lock() throws Exception {
        // Assert
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(rskDestinationAddress, Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_beforeFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        MatcherAssert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        // Don't whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        MatcherAssert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        //First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        //Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        MatcherAssert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(Coin.COIN.multiply(5), provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, amountToLock));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        MatcherAssert.assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, null);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigWithWitnessType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WSH, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigWithWitnessType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WSH, btcAddress, null);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_before_rskip_143_activation() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);
        tx1.addOutput(Coin.COIN, Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withRepository(repository)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertThrows(VerificationException.EmptyInputsOrOutputs.class, () -> bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize()));

        // When we send a segwit tx when the fork is not enabled, the tx is rejected because it does not have the
        // expected input format, therefore this method is never reached
        verify(btcBlockStore, never()).getStoredBlockAtMainChainHeight(height);
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_with_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstantsRegtest,
            activations
        );
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(false)).isPresent());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_and_unregistered_coinbase_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(BridgeRegTestConstants.getInstance().getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_and_unqual_witness_root_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstantsRegtest,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_rejects_tx_without_witness_unequal_roots_after_rskip_143() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstantsRegtest,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_without_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_version1_after_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDerivedAddress));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void registerBtcTransaction_ignores_pegin_instructions_before_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            contractAddress,
            bridgeConstantsRegtest,
            activations
        );
        provider.setNewFederation(federation1);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        whitelist.put(btcAddressFromBtcLockSender, new OneOffWhiteListEntry(btcAddressFromBtcLockSender, amountToLock));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        Assertions.assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDestinationAddress));
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDerivedAddress));
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(amountToLock, provider.getNewFederationBtcUTXOs().get(0).getValue());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void when_registerBtcTransaction_invalidPeginProtocolVersion_afterFork_no_lock_and_refund()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, contractAddress, bridgeConstantsRegtest, activations);
        provider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddressFromBtcLockSender, rskAddress);

        PeginInstructions peginInstructions = mock(PeginInstructions.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(99);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        Assertions.assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));
        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        Assertions.assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        Assertions.assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .collect(Collectors.toList());

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
        MatcherAssert.assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        Assertions.assertEquals(btcAddressFromBtcLockSender, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        Assertions.assertEquals(1, pegoutBtcTx.getInputs().size());
        Assertions.assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        Assertions.assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        Assertions.assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        Assertions.assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void isBlockMerkleRootValid_equal_merkle_roots() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(merkleRoot);
        Assertions.assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_unequal_merkle_roots_before_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Sha256Hash merkleRoot = BitcoinTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        Assertions.assertFalse(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_null_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(null);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assertions.assertFalse(bridgeSupport.isBlockMerkleRootValid(PegTestUtils.createHash(1), btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_not_null_and_unequal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(PegTestUtils.createHash(1));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assertions.assertFalse(bridgeSupport.isBlockMerkleRootValid(PegTestUtils.createHash(2), btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_not_null_and_equal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Sha256Hash merkleRoot = PegTestUtils.createHash(1);
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(merkleRoot);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        Assertions.assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void getBtcTransactionConfirmations_rejects_tx_with_witness_before_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        MerkleBranch merkleBranch = mock(MerkleBranch.class);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(
            tx1.getHash(true),
            registerHeader.getHash(),
            merkleBranch
        );
        Assertions.assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_accepts_tx_with_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        ));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assertions.assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_unregistered_coinbase_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations)
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assertions.assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_registered_coinbase_unequal_witnessroot_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest,
            activations));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        Assertions.assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_tx_without_witness_unequal_roots_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(PegTestUtils.createHash(5));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        Assertions.assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_accepts_tx_without_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations)
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(blockMerkleRoot);

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(blockMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        Assertions.assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_wrong_witnessReservedValue_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        byte[] witnessReservedValue = new byte[10];

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_MerkleTreeWrongFormat_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            new byte[]{6, 6, 6},
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_HashNotInPmt_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_notVerify_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        BtcTransaction tx = new BtcTransaction(btcRegTestParams);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        Sha256Hash hash = registerHeader.getHash();
        when(btcBlockStore.getFromCache(hash)).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));

        byte[] btcTxSerialized = tx.bitcoinSerialize();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] bytes = Sha256Hash.ZERO_HASH.getBytes();
        assertThrows(VerificationException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            btcTxSerialized,
            hash,
            pmtSerialized,
            mock(Sha256Hash.class),
            bytes
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_not_equal_merkle_root_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);
        txWithoutWitness.setWitness(0, null);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(txWithoutWitness.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            PegTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        BtcBlock btcBlock = mock(BtcBlock.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(storedBlock);

        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_null_stored_block_noSent() throws BlockStoreException, AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(null);

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void registerBtcCoinbaseTransaction() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash mRoot = MerkleTreeUtils.combineLeftRight(tx1.getHash(), secondHashTx);

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);
        byte[] witnessRootBytes = witnessRoot.getReversedBytes();
        byte[] wc = tx1.getOutputs().stream().filter(t -> t.getValue().getValue() == 0).collect(Collectors.toList()).get(0).getScriptPubKey().getChunks().get(1).data;
        wc = Arrays.copyOfRange(wc, 4, 36);
        Sha256Hash witCom = Sha256Hash.wrap(wc);

        Assertions.assertEquals(Sha256Hash.twiceOf(witnessRootBytes, witnessReservedValue), witCom);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        //Merkle root is from the original block
        Assertions.assertEquals(merkleRoot, mRoot);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));
        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            witnessRoot,
            witnessReservedValue
        );

        ArgumentCaptor<CoinbaseInformation> argumentCaptor = ArgumentCaptor.forClass(CoinbaseInformation.class);
        verify(provider).setCoinbaseInformation(eq(registerHeader.getHash()), argumentCaptor.capture());
        assertEquals(witnessRoot, argumentCaptor.getValue().getWitnessMerkleRoot());
    }

    @Test
    void hasBtcCoinbaseTransaction_before_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activations);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        Assertions.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void hasBtcCoinbaseTransaction_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activations);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        Assertions.assertTrue(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void hasBtcCoinbaseTransaction_fails_with_null_coinbase_information_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Repository repository = createRepository();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Assertions.assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void isAlreadyBtcTxHashProcessedHeight_true() throws IOException {
        Repository repository = createRepository();
        BtcTransaction btcTransaction = new BtcTransaction(btcRegTestParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);

        provider.setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(), 1L);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstantsRegtest, provider);

        Assertions.assertTrue(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    void isAlreadyBtcTxHashProcessedHeight_false() throws IOException {
        BtcTransaction btcTransaction = new BtcTransaction(btcRegTestParams);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstantsRegtest, mock(BridgeStorageProvider.class));

        Assertions.assertFalse(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    void validationsForRegisterBtcTransaction_negative_height() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction tx = new BtcTransaction(btcRegTestParams);
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstantsRegtest, activationsBeforeForks);
        BridgeSupport bridgeSupport = getBridgeSupport(bridgeConstantsRegtest, provider);

        byte[] data = Hex.decode("ab");

        Assertions.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), -1, data, data));
    }

    @Test
    void validationsForRegisterBtcTransaction_insufficient_confirmations() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction tx = new BtcTransaction(btcRegTestParams);
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstantsRegtest.getBtcParams());
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activationsBeforeForks
        );
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        byte[] data = Hex.decode("ab");

        Assertions.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), 100, data, data));
    }

    @Test
    void validationsForRegisterBtcTransaction_invalid_pmt() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
            "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca64010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mockFactory
        );

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(
            btcTx.getHash(),
            btcTxHeight,
            pmtSerialized,
            btcTx.bitcoinSerialize()
        ));
    }

    @Test
    void validationsForRegisterBtcTransaction_hash_not_in_pmt() throws BlockStoreException, AddressFormatException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(BitcoinTestUtils.createHash(0));

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mockFactory
        );

        Assertions.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
    }

    @Test
    void validationsForRegisterBtcTransaction_exception_in_getTxnHashAndMerkleRoot()
        throws BlockStoreException, AddressFormatException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        PartialMerkleTree pmt = mock(PartialMerkleTree.class);
        when(pmt.getTxnHashAndMerkleRoot(anyList())).thenReturn(Sha256Hash.ZERO_HASH).thenThrow(VerificationException.class);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        assertThrows(BridgeIllegalArgumentException.class, () -> {
            BridgeSupport bridgeSupport = getBridgeSupport(
                bridgeConstants,
                mock(BridgeStorageProvider.class),
                mock(Repository.class),
                mock(BridgeEventLogger.class),
                null,
                mockFactory
            );
            Assertions.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
        });
    }


    @Test
    void validationsForRegisterBtcTransaction_tx_without_inputs_before_rskip_143() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mockFactory,
            activations
        );

        Sha256Hash hash = btcTx.getHash();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] btcTxSerialized = btcTx.bitcoinSerialize();
        assertThrows(VerificationException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(hash, btcTxHeight, pmtSerialized, btcTxSerialized));
    }

    @Test
    void validationsForRegisterBtcTransaction_tx_without_inputs_after_rskip_143() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            null,
            mockFactory,
            activations
        );

        Sha256Hash hash = btcTx.getHash();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] decode = Hex.decode("00000000000100");
        assertThrows(VerificationException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(hash, 0, pmtSerialized, decode));
    }

    @Test
    void validationsForRegisterBtcTransaction_invalid_block_merkle_root() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create tx and PMT. Also create a btc block, that has not relation with tx and PMT.
        // The tx will be rejected because merkle block doesn't match.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), Sha256Hash.ZERO_HASH,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mockBridgeStorageProvider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            mock(FederationSupport.class),
            btcBlockStoreFactory,
            mock(ActivationConfig.ForBlock.class),
            signatureCache
        );

        Assertions.assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), height, pmt.bitcoinSerialize(), tx.bitcoinSerialize()));
    }

    @Test
    void validationsForRegisterBtcTransaction_successful() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        when(mockBridgeStorageProvider.getNewFederation()).thenReturn(bridgeConstantsRegtest.getGenesisFederation());

        BtcBlockStoreWithCache.Factory btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(lockValue, mockBridgeStorageProvider.getNewFederation().getAddress());
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT. The block includes a valid merkleRoot calculated from the PMT.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, PegTestUtils.createHash(), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mockBridgeStorageProvider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            mock(FederationSupport.class),
            btcBlockStoreFactory,
            mock(ActivationConfig.ForBlock.class),
            signatureCache
        );

        Assertions.assertTrue(bridgeSupport.validationsForRegisterBtcTransaction(tx.getHash(), height, pmt.bitcoinSerialize(), tx.bitcoinSerialize()));
    }

    @Test
    void processPegIn_version0_tx_no_lockable_by_invalid_sender() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            true,
            false,
            TxSenderAddressType.P2SHMULTISIG,
            ConsensusRule.RSKIP143
        );
    }

    @Test
    void processPegIn_version0_tx_no_lockable_by_not_whitelisted_address() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            false,
            false,
            TxSenderAddressType.P2PKH,
            null
        );
    }

    @Test
    void processPegIn_version0_tx_no_lockable_by_surpassing_locking_cap() throws IOException, RegisterBtcTransactionException {
        assertRefundInProcessPegInVersionLegacy(
            true,
            true,
            TxSenderAddressType.P2PKH,
            ConsensusRule.RSKIP134
        );
    }

    @Test
    void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap() throws IOException,
        RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.P2PKH,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_with_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        BtcECKey key = new BtcECKey();
        Address btcRefundAddress = key.toAddress(btcRegTestParams);

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.of(btcRefundAddress),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void processPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_without_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInProcessPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void processPegIn_noPeginInstructions() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            activations
        );

        BtcTransaction btcTx = mock(BtcTransaction.class);
        when(btcTx.getValueSentToMe(any())).thenReturn(Coin.valueOf(1));

        assertThrows(RegisterBtcTransactionException.class, () -> bridgeSupport.processPegIn(
            btcTx,
            PegTestUtils.createHash3(1),
            0
        ));
    }

    @Test
    void processPegIn_errorParsingPeginInstructions_beforeRskip170_dontRefundSender() throws IOException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        Repository repository = createRepository();

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstantsRegtest.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        // Act
        try {
            bridgeSupport.processPegIn(btcTx, PegTestUtils.createHash3(1), 0);
            Assertions.fail(); // Should have thrown a RegisterBtcTransactionException
        } catch (Exception e) {
            // Assert
            Assertions.assertTrue(e instanceof RegisterBtcTransactionException);
            Assertions.assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
        }
    }

    @Test
    void processPegIn_errorParsingPeginInstructions_afterRskip170_refundSender()
        throws IOException, PeginInstructionsException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = srcKey1.toAddress(btcRegTestParams);

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstantsRegtest.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcSenderAddress,
            rskAddress
        );

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(btcTx)).thenThrow(PeginInstructionsException.class);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        // Act
        try {
            bridgeSupport.processPegIn(btcTx, PegTestUtils.createHash3(1), 0);
            Assertions.fail(); // Should have thrown a RegisterBtcTransactionException
        } catch (Exception ex) {
            // Assert
            Assertions.assertTrue(ex instanceof RegisterBtcTransactionException);
            Assertions.assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

            // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
            boolean successfulRejection = false;
            for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
                BtcTransaction refundTx = e.getBtcTransaction();
                if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                    refundTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams).equals(btcSenderAddress)) {
                    successfulRejection = true;
                    break;
                }
            }

            Assertions.assertTrue(successfulRejection);
        }
    }

    @Test
    void receiveHeader_time_not_present_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsBeforeForks
        );

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, times(1)).put(storedBlock2);
        verify(provider, times(1)).getReceiveHeadersLastTimestamp();
        verify(provider, times(1)).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    void receiveHeader_time_exceed_X() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            activationsAfterForks
        );

        long timeStamp_old = executionBlockMock.getTimestamp() - (bridgeConstantsRegtest.getMinSecondsBetweenCallsToReceiveHeader() * 2L);
        doReturn(Optional.of(timeStamp_old)).when(provider).getReceiveHeadersLastTimestamp();

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, times(1)).put(storedBlock2);
        verify(provider, times(1)).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    void receiveHeader_time_less_than_X() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            activationsAfterForks
        );

        long timeStamp_old = executionBlockMock.getTimestamp() - (bridgeConstantsRegtest.getMinSecondsBetweenCallsToReceiveHeader() / 2L);
        doReturn(Optional.of(timeStamp_old)).when(provider).getReceiveHeadersLastTimestamp();

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assertions.assertEquals(-1, result);
    }

    @Test
    void receiveHeader_unexpected_exception() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.of(new byte[]{}));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        int result = bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock);
        verify(provider, times(1)).getReceiveHeadersLastTimestamp();
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assertions.assertEquals(-99, result);
    }

    @Test
    void receiveHeader_previous_block_not_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        when(btcBlockStore.get(any())).thenReturn(null);
        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assertions.assertEquals(-3, result);
    }

    @Test
    void receiveHeader_block_too_old() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        when(storedBlock.getHeight()).thenReturn(10, 5000, 10);

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assertions.assertEquals(-2, result);
    }

    @Test
    void receiveHeader_block_exist_in_storage() throws IOException, BlockStoreException {
        Repository repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock = mock(BtcBlock.class);
        Sha256Hash btcBlockHash = PegTestUtils.createHash(1);
        when(btcBlock.getHash()).thenReturn(btcBlockHash);
        when(btcBlockStore.get(btcBlockHash)).thenReturn(mock(StoredBlock.class));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
                contractAddress,
                bridgeConstantsRegtest,
                activationsAfterForks
            )
        );

        BridgeSupport bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock,
            btcBlockStore,
            provider,
            storedBlock,
            activationsAfterForks
        );

        // when(btcBlockStore.get(any())).thenReturn(nul);
        int result = bridgeSupport.receiveHeader(btcBlock);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(any(StoredBlock.class));
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        Assertions.assertEquals(-4, result);
    }

    private void assertRefundInProcessPegInVersionLegacy(
        boolean isWhitelisted,
        boolean mockLockingCap,
        TxSenderAddressType lockSenderAddressType,
        @Nullable ConsensusRule consensusRule) throws IOException, RegisterBtcTransactionException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        if (consensusRule != null) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstantsRegtest.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(eq(btcAddress), any(Coin.class), any(int.class))).thenReturn(isWhitelisted);
        when(provider.getLockWhitelist()).thenReturn(lockWhitelist);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        if (mockLockingCap) {
            when(provider.getLockingCap()).thenReturn(Coin.COIN.multiply(1));
        }

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcAddress, rskAddress);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        // Act
        bridgeSupport.processPegIn(btcTx, PegTestUtils.createHash3(1), 0);

        // Assert
        Assertions.assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        // Check rejection tx input was created from btc tx
        boolean successfulRejection = false;
        for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
            if (e.getBtcTransaction().getInput(0).getOutpoint().getHash() == btcTx.getHash()) {
                successfulRejection = true;
                break;
            }
        }

        Assertions.assertTrue(successfulRejection);

        // Check tx was not marked as processed
        Assertions.assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTx.getHash()).isPresent());
    }

    @Test
    void migrating_many_utxos_works_before_rskip294_divide_in_2() throws IOException {
        test_migrating_many_utxos(false, 380, 2);
    }

    @Test
    void migrating_many_utxos_works_before_rskip294_divide_in_4() throws IOException {
        test_migrating_many_utxos(false, 600, 4);
    }

    @Test
    void migrating_many_utxos_works_after_rskip294_even_utxos_distribution() throws IOException {
        int utxosToCreate = 400;
        int expectedTransactions = (int) Math.ceil((double) utxosToCreate / bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction());
        test_migrating_many_utxos(true, utxosToCreate, expectedTransactions);
    }

    @Test
    void migrating_many_utxos_works_after_rskip294_uneven_utxos_distribution() throws IOException {
        int utxosToCreate = 410;
        int expectedTransactions = (int) Math.ceil((double) utxosToCreate / bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction());
        test_migrating_many_utxos(true, utxosToCreate, expectedTransactions);
    }

    private void test_migrating_many_utxos(boolean isRskip294Active, int utxosToCreate, int expectedTransactions) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP294)).thenReturn(isRskip294Active);

        List<FederationMember> oldFedMembers = new ArrayList<>();
        int oldFedMembersAmount = 13;
        for (int i = 0; i < oldFedMembersAmount; i++) {
            oldFedMembers.add(FederationMember.getFederationMemberFromKey(new BtcECKey()));
        }

        Federation oldFed = new StandardMultisigFederation(
            oldFedMembers,
            Instant.now(),
            0,
            btcRegTestParams
        );
        Federation newFed = new StandardMultisigFederation(
            Arrays.asList(
                FederationMember.getFederationMemberFromKey(new BtcECKey()),
                FederationMember.getFederationMemberFromKey(new BtcECKey()),
                FederationMember.getFederationMemberFromKey(new BtcECKey())
            ),
            Instant.now(),
            1,
            btcRegTestParams
        );

        Block block = mock(Block.class);
        // Set block right after the migration should start
        long blockNumber = newFed.getCreationBlockNumber() +
            bridgeConstantsRegtest.getFederationActivationAge(activations) +
            bridgeConstantsRegtest.getFundsMigrationAgeSinceActivationBegin() +
            1;
        when(block.getNumber()).thenReturn(blockNumber);

        List<UTXO> utxosToMigrate = new ArrayList<>();
        for (int i = 0; i < utxosToCreate; i++) {
            utxosToMigrate.add(new UTXO(
                PegTestUtils.createHash(i),
                0,
                Coin.COIN,
                0,
                false,
                oldFed.getP2SHScript())
            );
        }

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(Collections.emptySet());
        BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);
        when(bridgeStorageProvider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(new ArrayList<>()));
        when(bridgeStorageProvider.getNewFederation()).thenReturn(newFed);
        when(bridgeStorageProvider.getOldFederation()).thenReturn(oldFed);
        when(bridgeStorageProvider.getOldFederationBtcUTXOs()).thenReturn(utxosToMigrate);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(block)
            .build();

        // Ensure a new transaction is created after each call to updateCollections
        // until the expected number is reached
        for (int i = 0; i < expectedTransactions; i++) {
            bridgeSupport.updateCollections(mock(Transaction.class));
            Assertions.assertEquals(i+1, pegoutsWaitingForConfirmations.getEntries().size());
        }
        Assertions.assertTrue(utxosToMigrate.isEmpty()); // Migrated UTXOs are removed from the list

        // Assert inputs size of each transaction
        List<Integer> expectedInputSizes = new ArrayList<>();
        int remainingUtxos = utxosToCreate;
        while (remainingUtxos > 0) {
            int expectedSize;
            if (isRskip294Active) {
                int maxInputsPerTransaction = bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction();
                expectedSize = Math.min(remainingUtxos, maxInputsPerTransaction);
            } else {
                expectedSize = remainingUtxos;
                while (expectedSize > 200) { // Approximately 200 inputs fit in a release transaction with this federation size
                    expectedSize = (int) Math.ceil((double) expectedSize / 2);
                }
            }
            expectedInputSizes.add(expectedSize);
            remainingUtxos -= expectedSize;
        }

        pegoutsWaitingForConfirmations.getEntries().forEach(e -> {
            Integer inputsSize = e.getBtcTransaction().getInputs().size();
            expectedInputSizes.remove(inputsSize);
        });

        Assertions.assertTrue(expectedInputSizes.isEmpty()); // All expected sizes should have been found and removed
    }

    @Test
    void getNextPegoutCreationBlockNumber_before_RSKIP271_activation() {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .build();

        assertEquals(0L, bridgeSupport.getNextPegoutCreationBlockNumber());
    }

    @Test
    void getNextPegoutCreationBlockNumber_after_RSKIP271_activation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(10L));

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        assertEquals(10L, bridgeSupport.getNextPegoutCreationBlockNumber());
    }

    @Test
    void getQueuedPegoutsCount_before_RSKIP271_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .withProvider(provider)
            .build();

        verify(provider, never()).getReleaseRequestQueueSize();
        assertEquals(0, bridgeSupport.getQueuedPegoutsCount());
    }

    @Test
    void getQueuedPegoutsCount_after_RSKIP271_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueueSize()).thenReturn(2);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        assertEquals(2, bridgeSupport.getQueuedPegoutsCount());
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        Federation standardFed = bridgeConstants.getGenesisFederation();

        ActivationConfig.ForBlock preRSKIP271_activations = mock(ActivationConfig.ForBlock.class);
        when(preRSKIP271_activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);
        when(preRSKIP271_activations.isActive(ConsensusRule.RSKIP385)).thenReturn(false);

        Federation p2shFed = new ErpFederation(
            members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            preRSKIP271_activations,
            new P2shErpRedeemScriptBuilder()
        );

        Stream<Arguments> preRskip271 = Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                preRSKIP271_activations,
                standardFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                preRSKIP271_activations,
                standardFed,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                preRSKIP271_activations,
                standardFed,
                150,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                preRSKIP271_activations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                preRSKIP271_activations,
                p2shFed,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                preRSKIP271_activations,
                p2shFed,
                150,
                Coin.valueOf(0L)
            )
        );
        return preRskip271;
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        Federation standardFed = bridgeConstants.getGenesisFederation();

        ActivationConfig.ForBlock preRSKIP385_activations = mock(ActivationConfig.ForBlock.class);
        when(preRSKIP385_activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(preRSKIP385_activations.isActive(ConsensusRule.RSKIP385)).thenReturn(false);

        Federation p2shFed = new ErpFederation(
            members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            preRSKIP385_activations,
            new P2shErpRedeemScriptBuilder()
        );

        Stream<Arguments> preRskip385 = Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                preRSKIP385_activations,
                standardFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                preRSKIP385_activations,
                standardFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                preRSKIP385_activations,
                standardFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                preRSKIP385_activations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                preRSKIP385_activations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 161200L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                preRSKIP385_activations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 638000L)
            )
        );
        return preRskip385;
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(BridgeConstants bridgeConstants) {
        ActivationConfig.ForBlock postRSKIP385_activations = mock(ActivationConfig.ForBlock.class);
        when(postRSKIP385_activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);
        when(postRSKIP385_activations.isActive(ConsensusRule.RSKIP385)).thenReturn(true);

        Federation standardFed = bridgeConstants.getGenesisFederation();
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        ErpFederation p2shFed = new ErpFederation(
            members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay(),
            postRSKIP385_activations,
            new P2shErpRedeemScriptBuilder()
        );

        Stream<Arguments> postRskip385 = Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                postRSKIP385_activations,
                standardFed,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 233800L: 65400L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                postRSKIP385_activations,
                standardFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                postRSKIP385_activations,
                standardFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                postRSKIP385_activations,
                p2shFed,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 151400L: 158000L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                postRSKIP385_activations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 161200L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                postRSKIP385_activations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 638000L)
            )
        );
        return postRskip385;
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider() {
        BridgeRegTestConstants bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();

        Stream<Arguments> preRskip271RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeConstantsRegtest);
        Stream<Arguments> preRskip385RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeConstantsRegtest);
        Stream<Arguments> postRskip385Regtest = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeConstantsRegtest);

        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

        Stream<Arguments> preRskip271Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeMainNetConstants);
        Stream<Arguments> preRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeMainNetConstants);
        Stream<Arguments> postRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeMainNetConstants);

        return Stream.of(
            preRskip271RegTest,
            preRskip385RegTest,
            postRskip385Regtest,
            preRskip271Mainnet,
            preRskip385Mainnet,
            postRskip385Mainnet
        ).flatMap(Function.identity());
    }

    @ParameterizedTest
    @MethodSource("getEstimatedFeesForNextPegOutEventArgsProvider")
    void getEstimatedFeesForNextPegOutEvent(
        ActivationConfig.ForBlock activations,
        Federation federation,
        int pegoutRequestsCount,
        Coin expectedEstimatedFee
    ) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getReleaseRequestQueueSize()).thenReturn(pegoutRequestsCount);
        when(provider.getNewFederation()).thenReturn(federation);

        Coin feePerKB = Coin.MILLICOIN;
        when(provider.getFeePerKb()).thenReturn(feePerKB);
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .build();

        // Act
        Coin estimatedFeesForNextPegOutEvent = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

        // Assert
        assertEquals(expectedEstimatedFee, estimatedFeesForNextPegOutEvent);
    }

    private void assertRefundInProcessPegInVersion1(
        TxSenderAddressType lockSenderAddressType,
        Optional<Address> btcRefundAddress,
        List<ConsensusRule> consensusRules)
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        for (ConsensusRule consensusRule : consensusRules) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        Repository repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = null;
        if (lockSenderAddressType != TxSenderAddressType.UNKNOWN) {
            btcSenderAddress = srcKey1.toAddress(btcRegTestParams);
        }

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), bridgeConstantsRegtest.getGenesisFederation().getAddress());
        btcTx.addInput(PegTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        when(provider.getLockingCap()).thenReturn(Coin.COIN.multiply(1));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcSenderAddress, rskAddress);

        if (!btcRefundAddress.isPresent()  && btcSenderAddress != null) {
            btcRefundAddress = Optional.of(btcSenderAddress);
        }
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskAddress,
            btcRefundAddress
        );

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            btcLockSenderProvider,
            peginInstructionsProvider,
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        // Act
        bridgeSupport.processPegIn(btcTx, PegTestUtils.createHash3(1), 0);

        // Assert
        if (lockSenderAddressType == TxSenderAddressType.UNKNOWN && !btcRefundAddress.isPresent()) {
            // Unknown sender and no refund address. Can't refund
            Assertions.assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
        } else {
            Assertions.assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

            // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
            boolean successfulRejection = false;
            for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
                BtcTransaction refundTx = e.getBtcTransaction();
                if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                    refundTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams).equals(btcRefundAddress.get())) {
                    successfulRejection = true;
                    break;
                }
            }

            Assertions.assertTrue(successfulRejection);
        }
    }

    private void assertLockingCap(
        boolean shouldLock,
        boolean isLockingCapEnabled,
        Coin lockingCap,
        Coin amountSentToNewFed,
        Coin amountSentToOldFed,
        Coin amountInNewFed,
        Coin amountInOldFed) throws BlockStoreException, IOException, BridgeIllegalArgumentException {

        // Configure if locking cap should be evaluated
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(isLockingCapEnabled);

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getMinimumPeginTxValue(activations)).thenReturn(Coin.SATOSHI);
        when(bridgeConstants.getBtcParams()).thenReturn(BridgeRegTestConstants.getInstance().getBtcParams());
        when(bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()).thenReturn(1);
        when(bridgeConstants.getGenesisFeePerKb()).thenReturn(BridgeRegTestConstants.getInstance().getGenesisFeePerKb());
        when(bridgeConstants.getMaxRbtc()).thenReturn(BridgeRegTestConstants.getInstance().getMaxRbtc());
        when(bridgeConstants.getOldFederationAddress()).thenReturn(BridgeRegTestConstants.getInstance().getOldFederationAddress());

        // Configure locking cap
        when(bridgeConstants.getInitialLockingCap()).thenReturn(lockingCap);

        Repository repository = createRepository();

        Federation oldFederation = PegTestUtils.createSimpleActiveFederation(bridgeConstants);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );
        // We need a random new fed
        provider.setNewFederation(PegTestUtils.createFederation(
            bridgeConstants,
            Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
            )
        ));
        // Use genesis fed as old
        provider.setOldFederation(oldFederation);

        Coin currentFunds = Coin.ZERO;

        // Configure existing utxos in both federations
        if (amountInOldFed != null) {
            UTXO utxo = new UTXO(
                Sha256Hash.wrap(TestUtils.generateBytes("amountInOldFed",32)),
                0,
                amountInOldFed,
                1,
                false,
                new Script(new byte[]{})
            );
            provider.getOldFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInOldFed);
        }
        if (amountInNewFed != null) {
            UTXO utxo = new UTXO(
                Sha256Hash.wrap(TestUtils.generateBytes("amountInNewFed",32)),
                0,
                amountInNewFed,
                1,
                false,
                new Script(new byte[]{})
            );
            provider.getNewFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInNewFed);
        }
        // Fund bridge in RBTC, substracting the funds that we are indicating were locked in the federations (which means a user locked)
        co.rsk.core.Coin bridgeBalance = LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(currentFunds));
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, bridgeBalance);

        // The locking cap shouldn't be surpassed by the initial configuration
        Assertions.assertFalse(isLockingCapEnabled && currentFunds.isGreaterThan(lockingCap));

        Coin lockValue = Coin.ZERO;
        int newUtxos = 0;

        // Create transaction setting the outputs to the configured values
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        if (amountSentToOldFed != null) {
            tx.addOutput(amountSentToOldFed, provider.getOldFederation().getAddress());
            lockValue = lockValue.add(amountSentToOldFed);
            newUtxos++;
        }
        if (amountSentToNewFed != null) {
            tx.addOutput(amountSentToNewFed, provider.getNewFederation().getAddress());
            lockValue = lockValue.add(amountSentToNewFed);
            newUtxos++;
        }
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, srcKey)
        );

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // Get the tx sender public key
        byte[] data = tx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the addresses
        LockWhitelist whitelist = provider.getLockWhitelist();
        Address address = senderBtcKey.toAddress(bridgeConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));
        // The address is whitelisted
        MatcherAssert.assertThat(whitelist.isWhitelisted(address), is(true));

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstants,
            provider,
            repository,
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            executionBlock,
            mockFactory,
            activations,
            signatureCache
        );

        // Simulate blockchain
        int height = 1;
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Transaction rskTx = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTx.getHash()).thenReturn(hash);

        // Try to register tx
        bridgeSupport.registerBtcTransaction(rskTx, tx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        // If the address is no longer whitelisted, it means it was consumed, whether the lock was rejected by lockingCap or not
        MatcherAssert.assertThat(whitelist.isWhitelisted(address), is(false));

        // The Btc transaction should have been processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(tx.getHash()));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(shouldLock ? lockValue : Coin.ZERO);
        RskAddress srcKeyRskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey.getPrivKey()).getAddress());

        // Verify amount was locked
        Assertions.assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(srcKeyRskAddress));
        Assertions.assertEquals(bridgeBalance.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(PrecompiledContracts.BRIDGE_ADDR));

        if (!shouldLock) {
            // Release tx should have been created directly to the signatures stack
            BtcTransaction pegoutBtcTx = provider.getPegoutsWaitingForConfirmations().getEntries().iterator().next().getBtcTransaction();
            Assertions.assertNotNull(pegoutBtcTx);
            // returns the funds to the sender
            Assertions.assertEquals(1, pegoutBtcTx.getOutputs().size());
            Assertions.assertEquals(address, pegoutBtcTx.getOutputs().get(0).getAddressFromP2PKHScript(bridgeConstants.getBtcParams()));
            Assertions.assertEquals(lockValue, pegoutBtcTx.getOutputs().get(0).getValue().add(pegoutBtcTx.getFee()));
            // Uses the same UTXO(s)
            Assertions.assertEquals(newUtxos, pegoutBtcTx.getInputs().size());
            for (int i = 0; i < newUtxos; i++) {
                TransactionInput input = pegoutBtcTx.getInput(i);
                Assertions.assertEquals(tx.getHash(), input.getOutpoint().getHash());
                Assertions.assertEquals(i, input.getOutpoint().getIndex());
            }
        }
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider) {
        return getBridgeSupport(constants, provider, null, mock(BridgeEventLogger.class), null, null, null);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, ActivationConfig.ForBlock activations) {
        return getBridgeSupport(constants, provider, null, mock(BridgeEventLogger.class), null, null, activations);
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
        BridgeEventLogger eventLogger, Block executionBlock,
        BtcBlockStoreWithCache.Factory blockStoreFactory,
        ActivationConfig.ForBlock activations) {
        return bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withRepository(track)
            .withEventLogger(eventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(blockStoreFactory)
            .withActivations(activations)
            .build();
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
        BtcLockSenderProvider btcLockSenderProvider, PeginInstructionsProvider peginInstructionsProvider,
        Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
        ActivationConfig.ForBlock activations, SignatureCache signatureCache) {

        if (btcLockSenderProvider == null) {
            btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        }
        if (peginInstructionsProvider == null) {
            peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        return new BridgeSupport(
            constants,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            peginInstructionsProvider,
            track,
            executionBlock,
            new Context(constants.getBtcParams()),
            new FederationSupport(constants, provider, executionBlock, activations),
            blockStoreFactory,
            activations,
            signatureCache
        );
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
        BridgeEventLogger eventLogger, Block executionBlock,
        BtcBlockStoreWithCache.Factory blockStoreFactory) {
        return bridgeSupportBuilder
            .withBridgeConstants(constants)
            .withProvider(provider)
            .withRepository(track)
            .withEventLogger(eventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(blockStoreFactory)
            .build();
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
        BtcBlock btcBlock,
        BtcBlockStoreWithCache btcBlockStore,
        BridgeStorageProvider provider,
        StoredBlock storedBlock,
        ActivationConfig.ForBlock activation
    ) throws BlockStoreException {
        return getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock,
            btcBlockStore,
            provider,
            storedBlock,
            mock(Block.class),
            activation
        );
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
        BtcBlock btcBlock,
        BtcBlockStoreWithCache btcBlockStore,
        BridgeStorageProvider provider,
        StoredBlock storedBlock,
        Block rskBlock,
        ActivationConfig.ForBlock activation
    ) throws BlockStoreException {

        doReturn(10).when(storedBlock).getHeight();

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        doReturn(BitcoinTestUtils.createHash(1)).when(btcBlock2).getHash();
        doReturn(btcBlock2).when(storedBlock).getHeader();

        doReturn(storedBlock).when(btcBlockStore).getChainHead();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        when(rskBlock.getTimestamp()).thenReturn(1611169584L);

        return getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BridgeEventLogger.class),
            rskBlock,
            mockFactory,
            activation
        );
    }

    private BtcLockSenderProvider getBtcLockSenderProvider(BtcLockSender.TxSenderAddressType txSenderAddressType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private PeginInstructionsProvider getPeginInstructionsProviderForVersion1(RskAddress rskDestinationAddress, Optional<Address> btcRefundAddress)
        throws PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructions = mock(PeginInstructionsVersion1.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(1);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        when(peginInstructions.getBtcRefundAddress()).thenReturn(btcRefundAddress);

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        return peginInstructionsProvider;
    }
}
