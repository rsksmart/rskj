package co.rsk.bridge;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.bridge.btcLockSender.BtcLockSender;
import co.rsk.bridge.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.bridge.btcLockSender.BtcLockSenderProvider;
import co.rsk.bridge.btcLockSender.P2pkhBtcLockSender;
import co.rsk.bridge.pegininstructions.PeginInstructionsException;
import co.rsk.bridge.pegininstructions.PeginInstructionsProvider;
import co.rsk.bridge.pegininstructions.PeginInstructionsVersion1;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PeginInformationTest {

    private static BridgeConstants bridgeConstants;
    private static NetworkParameters networkParameters;

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
}
