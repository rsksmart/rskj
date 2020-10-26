package co.rsk.peg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.btcLockSender.P2pkhBtcLockSender;
import co.rsk.peg.pegininstructions.PeginInstructionsException;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsVersion1;
import java.util.Optional;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.crypto.ECKey;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PeginInformationTest {

    private static BridgeConstants bridgeConstants;
    private static NetworkParameters networkParameters;

    @BeforeClass
    public static void setup() {
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void parse_fromBtcLockSender() throws PeginInstructionsException {
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
        Assert.assertEquals(0, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assert.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
    }

    @Test
    public void parse_fromPeginInstructions() throws PeginInstructionsException {
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
        Assert.assertEquals(1, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assert.assertEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assert.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assert.assertNotEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assert.assertNotEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
    }

    @Test
    public void parse_fromPeginInstructions_withoutBtcLockSender() throws PeginInstructionsException {
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
        Assert.assertEquals(1, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assert.assertEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
        Assert.assertNull(peginInformation.getSenderBtcAddress());
        Assert.assertEquals(TxSenderAddressType.UNKNOWN, peginInformation.getSenderBtcAddressType());
    }

    @Test
    public void parse_fromPeginInstructions_withoutBtcRefundAddress() throws PeginInstructionsException {
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
        Assert.assertEquals(1, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assert.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assert.assertNotEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
    }

    @Test
    public void parse_fromPeginInstructions_withoutBtcLockSender_withoutBtcRefundAddress() throws PeginInstructionsException {
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
        Assert.assertEquals(1, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assert.assertNull(peginInformation.getBtcRefundAddress());
        Assert.assertNull(peginInformation.getSenderBtcAddress());
        Assert.assertEquals(TxSenderAddressType.UNKNOWN, peginInformation.getSenderBtcAddressType());
    }

    @Test(expected = PeginInstructionsException.class)
    public void parse_fromPeginInstructions_invalidProtocolVersion() throws PeginInstructionsException {
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
        peginInformation.parse(btcTx);
    }

    @Test(expected = PeginInstructionsException.class)
    public void parse_withoutBtcLockSender_withoutPeginInstructions() throws PeginInstructionsException {
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
        peginInformation.parse(btcTx);
    }

    @Test
    public void parse_withBtcLockSender_withPeginInstructions_preIris() throws PeginInstructionsException {
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
        Assert.assertEquals(0, peginInformation.getProtocolVersion());
        Assert.assertEquals(rskDestinationAddressFromBtcLockSender, peginInformation.getRskDestinationAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getBtcRefundAddress());
        Assert.assertEquals(btcRefundAddressFromBtcLockSender, peginInformation.getSenderBtcAddress());
        Assert.assertEquals(senderBtcAddressType, peginInformation.getSenderBtcAddressType());
        Assert.assertNotEquals(rskDestinationAddressFromPeginInstructions, peginInformation.getRskDestinationAddress());
        Assert.assertNotEquals(btcRefundAddressFromPeginInstructions, peginInformation.getBtcRefundAddress());
    }

    @Test(expected = PeginInstructionsException.class)
    public void parse_withoutBtcLockSender_withPeginInstructions_preIris() throws PeginInstructionsException {
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
        peginInformation.parse(btcTx);
    }
}
