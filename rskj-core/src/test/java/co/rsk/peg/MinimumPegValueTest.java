/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import co.rsk.peg.federation.*;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MinimumPegValueTest {
  private ActivationConfig.ForBlock activations;
  private NetworkParameters networkParameters;
  private BridgeConstants bridgeMainNetConstants;

  @BeforeEach
  void setup() {
    activations = mock(ActivationConfig.ForBlock.class);
    bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    networkParameters = bridgeMainNetConstants.getBtcParams();
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void checkProposedPegValues_whenOneInputofMinPeginValueAndOneOutputOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minPeginValue, Coin minPegoutValue) {
    // build federation
    Federation fed = new P2shErpFederationBuilder().withNetworkParameters(networkParameters).build();
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = Arrays.asList(
        createTestEntry(1000, minPegoutValue));
    // list of utxos that contains one utxo with the minimum peg-in value
    List<UTXO> utxos = Arrays.asList(
        new UTXO(getUTXOHash("utxo"), 0, minPeginValue, 0, false, fed.getP2SHScript()));
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        fed,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        fed.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void checkProposedPegValues_whenOneInputOfTenTimesMinPeginValueAndTenOutputsOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minPeginValue, Coin minPegoutValue) {
    // build federation
    Federation fed = new P2shErpFederationBuilder().withNetworkParameters(networkParameters).build();
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      entries.add(createTestEntry(1000 + i, minPegoutValue));
    }
    // list of utxos that contains one utxo with the minimum peg-in value
    List<UTXO> utxos = Arrays.asList(
        new UTXO(getUTXOHash("utxo"), 0, minPeginValue.times(10), 0, false, fed.getP2SHScript()));
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        fed,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        fed.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void checkProposedPegValues_whenTenInputOfMinPeginValueAndTenOutputsOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minPeginValue, Coin minPegoutValue) {
    // build federation
    Federation fed = new P2shErpFederationBuilder().withNetworkParameters(networkParameters).build();
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      entries.add(createTestEntry(1000 + i, minPegoutValue));
    }
    // list of utxos that contains one utxo with the minimum peg-in value
    List<UTXO> utxos = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      utxos.add(
          new UTXO(getUTXOHash("utxo" + i), 0, minPeginValue, 0, false, fed.getP2SHScript()));
    }
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        fed,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        fed.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  private static Stream<Arguments> providePegMinimumParameters() {
    return Stream.of(
        Arguments.of(Coin.valueOf(24_000L), Coin.valueOf(100_000L), Coin.valueOf(80_000L)));
  }

  private Address getAddress(int pk) {
    return BtcECKey.fromPrivate(BigInteger.valueOf(pk))
        .toAddress(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
  }

  private Sha256Hash getUTXOHash(String generator) {
    return Sha256Hash.of(generator.getBytes(StandardCharsets.UTF_8));
  }

  private ReleaseRequestQueue.Entry createTestEntry(int addressPk, Coin amount) {
    return new ReleaseRequestQueue.Entry(getAddress(addressPk), amount);
  }
}
