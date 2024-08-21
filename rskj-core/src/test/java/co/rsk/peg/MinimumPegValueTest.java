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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.*;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.trie.Trie;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MinimumPegValueTest {

  private static final BigInteger NONCE = new BigInteger("0");
  private static final BigInteger GAS_PRICE = new BigInteger("100");
  private static final BigInteger GAS_LIMIT = new BigInteger("1000");
  private static final String DATA = "80af2871";
  private static final ECKey SENDER = new ECKey();

  private ActivationConfig.ForBlock activations;
  private NetworkParameters networkParameters;
  private BridgeConstants bridgeMainNetConstants;
  private Federation federation;

  @BeforeEach
  void setup() {
    activations = ActivationConfigsForTest.all().forBlock(0L);
    bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    networkParameters = bridgeMainNetConstants.getBtcParams();
    federation = new P2shErpFederationBuilder().withNetworkParameters(networkParameters).build();
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void whenOneInputofMinPeginValueAndOneOutputOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minimumPeginValue, Coin minimumPegoutValue) {
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = Arrays.asList(
        createTestEntry(1000, minimumPegoutValue));
    // list of utxos that contains one utxo with the minimum peg-in value
    List<UTXO> utxos = Arrays.asList(
        new UTXO(getUTXOHash("utxo"), 0, minimumPeginValue, 0, false, federation.getP2SHScript()));
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        federation,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        federation.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void whenOneInputOfTenTimesMinPeginValueAndTenOutputsOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minimumPeginValue, Coin minimumPegoutValue) {
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      entries.add(createTestEntry(1000 + i, minimumPegoutValue));
    }
    // list of utxos that contains one utxo with ten times the minimum peg-in value
    List<UTXO> utxos = Arrays.asList(
        new UTXO(getUTXOHash("utxo"), 0, minimumPeginValue.times(10), 0, false, federation.getP2SHScript()));
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        federation,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        federation.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void whenTenInputOfMinPeginValueAndTenOutputsOfMinPegoutValue_shouldBuildTransactionSuccessfully(
      Coin feePerKb, Coin minimumPeginValue, Coin minimumPegoutValue) {
    // list of peg-out requests with the given minimum peg-out value
    List<ReleaseRequestQueue.Entry> entries = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      entries.add(createTestEntry(1000 + i, minimumPegoutValue));
    }
    // list of utxos that contains ten utxos with the minimum peg-in value
    List<UTXO> utxos = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      utxos.add(
          new UTXO(getUTXOHash("utxo" + i), 0, minimumPeginValue, 0, false, federation.getP2SHScript()));
    }
    // the federation wallet, with flyover support
    Wallet fedWallet = BridgeUtils.getFederationSpendWallet(
        new Context(networkParameters),
        federation,
        utxos,
        true,
        mock(BridgeStorageProvider.class));
    // build release transaction builder for current fed
    ReleaseTransactionBuilder releaseTransactionBuilder = new ReleaseTransactionBuilder(
        networkParameters,
        fedWallet,
        federation.getAddress(),
        feePerKb,
        activations);

    // build batch peg-out transaction
    ReleaseTransactionBuilder.BuildResult result = releaseTransactionBuilder.buildBatchedPegouts(entries);

    // the proposed feePerKb and peg values are compatible
    assertEquals(ReleaseTransactionBuilder.Response.SUCCESS, result.getResponseCode());
  }

  @ParameterizedTest()
  @MethodSource("providePegMinimumParameters")
  void whenReleaseBtcCalledWithMinimumPegoutValue_shouldLogReleaseBtcRequestReceived(
      Coin feePerKb, Coin minimumPeginValue, Coin minimumPegoutValue) throws IOException {
    BridgeConstants bridgeConstants = mock(BridgeConstants.class);
    when(bridgeConstants.getBtcParams()).thenReturn(bridgeMainNetConstants.getBtcParams());
    when(bridgeConstants.getMinimumPegoutValuePercentageToReceiveAfterFee())
        .thenReturn(bridgeMainNetConstants.getMinimumPegoutValuePercentageToReceiveAfterFee());
    when(bridgeConstants.getMinimumPegoutTxValue()).thenReturn(minimumPegoutValue);

    List<LogInfo> logInfo = new ArrayList<>();
    SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    BridgeEventLoggerImpl eventLogger = spy(new BridgeEventLoggerImpl(
        bridgeConstants, activations, logInfo, signatureCache));

    FeePerKbSupport feePerKbSupport = mock(FeePerKbSupportImpl.class);
    when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

    BridgeSupport bridgeSupport = initBridgeSupport(
        bridgeConstants, eventLogger, activations, signatureCache, feePerKbSupport);

    bridgeSupport.releaseBtc(buildReleaseRskTx(minimumPegoutValue));

    verify(eventLogger).logReleaseBtcRequestReceived(any(), any(), any());
  }

  private static Stream<Arguments> providePegMinimumParameters() {
    return Stream.of(
        // 0.001 BTC - 0.0008 BTC: current feePerKb value
        Arguments.of(Coin.valueOf(24_000L), Coin.valueOf(100_000L), Coin.valueOf(80_000L)),
        // 0.001 BTC - 0.0008 BTC: maximum feePerKb value that allows all cases to
        // succeed
        Arguments.of(Coin.valueOf(24_750L), Coin.valueOf(100_000L), Coin.valueOf(80_000L)),

        // 0.0025 BTC - 0.002 BTC: current feePerKb value
        Arguments.of(Coin.valueOf(24_000L), Coin.valueOf(250_000L), Coin.valueOf(200_000L)),
        // 0.0025 BTC - 0.002 BTC: maximum feePerKb value that allows all cases to
        // succeed
        Arguments.of(Coin.valueOf(61_750L), Coin.valueOf(250_000L), Coin.valueOf(200_000L)));
  }

  /**********************************
   * ------- UTILS ------- *
   *********************************/

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

  private BridgeSupport initBridgeSupport(BridgeConstants bridgeConstants, BridgeEventLogger eventLogger,
      ActivationConfig.ForBlock activations, SignatureCache signatureCache, FeePerKbSupport feePerKbSupport) {
    Repository repository = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(null, new Trie())));

    StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
    FederationStorageProvider federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
    UTXO utxo = new UTXO(getUTXOHash("utxo"), 0, Coin.COIN.multiply(2), 1, false, federation.getP2SHScript());
    federationStorageProvider.getNewFederationBtcUTXOs(networkParameters, activations).add(utxo);
    federationStorageProvider.setNewFederation(federation);

    FederationSupport federationSupport = new FederationSupportBuilder()
        .withFederationConstants(bridgeConstants.getFederationConstants())
        .withFederationStorageProvider(federationStorageProvider)
        .build();

    BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR,
        networkParameters, activations);

    return new BridgeSupportBuilder()
        .withBridgeConstants(bridgeConstants)
        .withProvider(provider)
        .withRepository(repository)
        .withEventLogger(eventLogger)
        .withActivations(activations)
        .withSignatureCache(signatureCache)
        .withFederationSupport(federationSupport)
        .withFeePerKbSupport(feePerKbSupport)
        .build();
  }

  private Transaction buildReleaseRskTx(Coin coin) {
    Transaction releaseTx = Transaction
        .builder()
        .nonce(NONCE)
        .gasPrice(GAS_PRICE)
        .gasLimit(GAS_LIMIT)
        .destination(PrecompiledContracts.BRIDGE_ADDR.toHexString())
        .data(Hex.decode(DATA))
        .chainId(Constants.MAINNET_CHAIN_ID)
        .value(co.rsk.core.Coin.fromBitcoin(coin).asBigInteger())
        .build();
    releaseTx.sign(SENDER.getPrivKeyBytes());
    return releaseTx;
  }
}
