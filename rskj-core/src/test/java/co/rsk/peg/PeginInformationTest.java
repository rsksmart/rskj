package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.btcLockSender.P2pkhBtcLockSender;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import java.util.Optional;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PeginInformationTest {

    private static BridgeConstants bridgeConstants;
    private static NetworkParameters networkParameters;

    private static final ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock tbd600Activations = ActivationConfigsForTest.tbd600().forBlock(0);

    @BeforeAll
     static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    void parse_fromBtcLockSender() throws PeginInstructionsException {
        // Arrange
        BtcECKey key = new BtcECKey();
        RskAddress rskDestinationAddressFromBtcLockSender = new RskAddress(ECKey.fromPublicOnly(key.getPubKey()).getAddress());
        Address btcRefundAddressFromBtcLockSender = key.toAddress(networkParameters);
        TxSenderAddressType senderBtcAddressType = TxSenderAddressType.P2PKH;
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSenderMock = mock(P2pkhBtcLockSender.class);
        when(btcLockSenderMock.getRskAddress()).thenReturn(rskDestinationAddressFromBtcLockSender);
        when(btcLockSenderMock.getBTCAddress()).thenReturn(btcRefundAddressFromBtcLockSender);
        when(btcLockSenderMock.getTxSenderAddressType()).thenReturn(senderBtcAddressType);

        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.of(btcLockSenderMock));

        PeginInstructionsProvider peginInstructionsProviderMock = mock(
            PeginInstructionsProvider.class);

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(0, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
    }

    @Test
    void parse_fromPeginInstructions() throws PeginInstructionsException {
        // Arrange
        BtcECKey address1Key = new BtcECKey();
        RskAddress rskDestinationAddressFromBtcLockSender = new RskAddress(ECKey.fromPublicOnly(address1Key.getPubKey()).getAddress());
        Address btcRefundAddressFromBtcLockSender = address1Key.toAddress(networkParameters);
        TxSenderAddressType senderBtcAddressType = TxSenderAddressType.P2PKH;
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSenderMock = mock(P2pkhBtcLockSender.class);
        when(btcLockSenderMock.getRskAddress()).thenReturn(rskDestinationAddressFromBtcLockSender);
        when(btcLockSenderMock.getBTCAddress()).thenReturn(btcRefundAddressFromBtcLockSender);
        when(btcLockSenderMock.getTxSenderAddressType()).thenReturn(senderBtcAddressType);

        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.of(btcLockSenderMock));

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());
        Address btcRefundAddressFromPeginInstructions = address2Key.toAddress(networkParameters);

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress())
            .thenReturn(Optional.of(btcRefundAddressFromPeginInstructions));

        PeginInstructionsProvider peginInstructionsProviderMock = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(1, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assertions.assertEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assertions.assertNotEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assertions.assertNotEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
    }

    @Test
    void parse_fromPeginInstructions_withoutBtcLockSender() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());
        Address btcRefundAddressFromPeginInstructions = address2Key.toAddress(networkParameters);

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress())
            .thenReturn(Optional.of(btcRefundAddressFromPeginInstructions));

        PeginInstructionsProvider peginInstructionsProviderMock = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(1, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assertions.assertEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
        Assertions.assertNull(peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(TxSenderAddressType.UNKNOWN, peginInformation.getSenderBtcAddressType());
    }

    @Test
    void parse_fromPeginInstructions_withoutBtcRefundAddress() throws PeginInstructionsException {
        // Arrange
        BtcECKey address1Key = new BtcECKey();
        RskAddress rskDestinationAddressFromBtcLockSender = new RskAddress(ECKey.fromPublicOnly(address1Key.getPubKey()).getAddress());
        Address btcRefundAddressFromBtcLockSender = address1Key.toAddress(networkParameters);
        TxSenderAddressType senderBtcAddressType = TxSenderAddressType.P2PKH;
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSenderMock = mock(P2pkhBtcLockSender.class);
        when(btcLockSenderMock.getRskAddress()).thenReturn(rskDestinationAddressFromBtcLockSender);
        when(btcLockSenderMock.getBTCAddress()).thenReturn(btcRefundAddressFromBtcLockSender);
        when(btcLockSenderMock.getTxSenderAddressType()).thenReturn(senderBtcAddressType);

        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.of(btcLockSenderMock));

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress()).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProviderMock = mock(
            PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(1, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assertions.assertNotEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
    }

    @Test
    void parse_fromPeginInstructions_withoutBtcLockSender_withoutBtcRefundAddress() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.empty());

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress())
            .thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProviderMock = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(1, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assertions.assertNull(peginInformation.getBtcRefundAddress());
        Assertions.assertNull(peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(TxSenderAddressType.UNKNOWN, peginInformation.getSenderBtcAddressType());
    }

    @Test
    void parse_fromPeginInstructions_invalidProtocolVersion() throws PeginInstructionsException {
        // Arrange
        BtcECKey address1Key = new BtcECKey();
        RskAddress rskDestinationAddressFromBtcLockSender = new RskAddress(ECKey.fromPublicOnly(address1Key.getPubKey()).getAddress());
        Address btcRefundAddressFromBtcLockSender = address1Key.toAddress(networkParameters);
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSenderMock = mock(P2pkhBtcLockSender.class);
        when(btcLockSenderMock.getRskAddress()).thenReturn(rskDestinationAddressFromBtcLockSender);
        when(btcLockSenderMock.getBTCAddress()).thenReturn(btcRefundAddressFromBtcLockSender);

        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.of(btcLockSenderMock));

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(0);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);

        PeginInstructionsProvider peginInstructionsProviderMock = mock(
            PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );

        Assertions.assertThrows(PeginInstructionsException.class, () -> peginInformation.parse(btcTx));
    }

    @Test
    void parse_withoutBtcLockSender_withoutPeginInstructions() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProviderMock = mock(
            PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.empty());

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );

        Assertions.assertThrows(PeginInstructionsException.class, () -> peginInformation.parse(btcTx));
    }

    @Test
    void parse_withBtcLockSender_withPeginInstructions_preIris() throws PeginInstructionsException {
        // Arrange
        BtcECKey address1Key = new BtcECKey();
        RskAddress rskDestinationAddressFromBtcLockSender = new RskAddress(ECKey.fromPublicOnly(address1Key.getPubKey()).getAddress());
        Address btcRefundAddressFromBtcLockSender = address1Key.toAddress(networkParameters);
        TxSenderAddressType senderBtcAddressType = TxSenderAddressType.P2PKH;
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        BtcLockSender btcLockSenderMock = mock(P2pkhBtcLockSender.class);
        when(btcLockSenderMock.getRskAddress()).thenReturn(rskDestinationAddressFromBtcLockSender);
        when(btcLockSenderMock.getBTCAddress()).thenReturn(btcRefundAddressFromBtcLockSender);
        when(btcLockSenderMock.getTxSenderAddressType()).thenReturn(senderBtcAddressType);

        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx))
            .thenReturn(Optional.of(btcLockSenderMock));

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());
        Address btcRefundAddressFromPeginInstructions = address2Key.toAddress(networkParameters);

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress())
            .thenReturn(Optional.of(btcRefundAddressFromPeginInstructions));

        PeginInstructionsProvider peginInstructionsProviderMock = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );
        peginInformation.parse(btcTx);

        // Assert
        Assertions.assertEquals(0, peginInformation.getProtocolVersion());
        Assertions.assertEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assertions.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assertions.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assertions.assertNotEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assertions.assertNotEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
    }

    @Test
    void parse_withoutBtcLockSender_withPeginInstructions_preIris() throws PeginInstructionsException {
        // Arrange
        BtcTransaction btcTx = new BtcTransaction(networkParameters);
        BtcLockSenderProvider btcLockSenderProviderMock = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProviderMock.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        BtcECKey address2Key = new BtcECKey();
        RskAddress rskDestinationAddressFromPeginInstructions = new RskAddress(ECKey.fromPublicOnly(address2Key.getPubKey()).getAddress());
        Address btcRefundAddressFromPeginInstructions = address2Key.toAddress(networkParameters);

        PeginInstructionsVersion1 peginInstructionsMock = mock(PeginInstructionsVersion1.class);
        when(peginInstructionsMock.getProtocolVersion()).thenReturn(1);
        when(peginInstructionsMock.getRskDestinationAddress())
            .thenReturn(rskDestinationAddressFromPeginInstructions);
        when(peginInstructionsMock.getBtcRefundAddress())
            .thenReturn(Optional.of(btcRefundAddressFromPeginInstructions));

        PeginInstructionsProvider peginInstructionsProviderMock = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProviderMock.buildPeginInstructions(btcTx))
            .thenReturn(Optional.of(peginInstructionsMock));

        ActivationConfig.ForBlock activationsMock = mock(ActivationConfig.ForBlock.class);
        when(activationsMock.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProviderMock,
            activationsMock
        );

        Assertions.assertThrows(PeginInstructionsException.class, () -> peginInformation.parse(btcTx));
    }


    private static Stream<Arguments> fingerroot_and_arrowhead_activations_args() {
        return Stream.of(
            // before RSKIP379 activation
            Arguments.of(
                fingerrootActivations
            ),
            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations
            ),
            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations
            )
        );
    }

    @ParameterizedTest
    @MethodSource("fingerroot_and_arrowhead_activations_args")
    void parse_bech32_withPeginInstructions(ActivationConfig.ForBlock activations) {
        // Arrange
        String rawTx = "02000000000101cf8b3b2baa22df50b1959d83b2f279ef231fb7cf2009ebfa35644d9e1f0184930200000000fdffffff02706408000000000017a9146e4b5ae85d86e4db0e6e5db09f8c276328cdbf3f87ec5cd40500000000160014d7aa00421cd50c8f282dc7d32992a5e2932a92f3024730440220313d11b58bd2861e4a2f8b2d1b644250569e35c946fcba426de94ed28974f12102207b9074796eea9f823a2d56239f49674c4ec34d40f519b3babd5eba44f94495eb012102acbad9efed3a451f646b93b5fd37a796c1758875cc33eed1626d4ed673d00a9b5abd2600";
        BtcTransaction btcTx = new BtcTransaction(BridgeTestNetConstants.getInstance().getBtcParams(), Hex.decode(rawTx));

        BtcLockSenderProvider btcLockSenderProviderMock = new BtcLockSenderProvider();
        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();

        // Act
        PeginInformation peginInformation = new PeginInformation(
            btcLockSenderProviderMock,
            peginInstructionsProvider,
            activations
        );

        // assert
        if (activations == fingerrootActivations) {
            Assertions.assertThrows(PeginInstructionsException.class, () -> peginInformation.parse(btcTx));
        } else {
            Assertions.assertDoesNotThrow(() -> {
                peginInformation.parse(btcTx);
                Assertions.assertEquals(0, peginInformation.getProtocolVersion());
                Assertions.assertEquals(TxSenderAddressType.UNKNOWN, peginInformation.getSenderBtcAddressType());
                Assertions.assertNull(peginInformation.getBtcRefundAddress());
                Assertions.assertNull(peginInformation.getSenderBtcAddress());
                Assertions.assertNull(peginInformation.getRskDestinationAddress());
            });
        }
    }
}
