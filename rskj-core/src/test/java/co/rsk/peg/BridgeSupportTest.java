package co.rsk.peg;

import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeSupportTest {

    @Test
    public void activations_is_set() {
        Block block = mock(Block.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP124)).thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                mock(BridgeConstants.class),
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                activations
        );

        Assert.assertTrue(bridgeSupport.getActivations().isActive(ConsensusRule.RSKIP124));
    }

    @Test(expected = NullPointerException.class)
    public void voteFeePerKbChange_nullFeeThrows() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(null));
        when(tx.getSender())
                .thenReturn(new RskAddress(ByteUtil.leftPadBytes(new byte[]{0x43}, 20)));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        bridgeSupport.voteFeePerKbChange(tx, null);
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_unauthorized() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(false);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(-10));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_negativeFeePerKb() {
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.NEGATIVE_SATOSHI), is(-1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.ZERO), is(-1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_unsuccessfulVote_excessiveFeePerKb() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB)), is(1));
        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.valueOf(MAX_FEE_PER_KB + 1)), is(-2));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVote() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(2);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider, never()).setFeePerKb(any());
    }

    @Test
    public void voteFeePerKbChange_successfulVoteWithFeeChange() {
        final long MAX_FEE_PER_KB = 5_000_000L;
        Block block = mock(Block.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        Transaction tx = mock(Transaction.class);
        BridgeConstants constants = mock(BridgeConstants.class);
        AddressBasedAuthorizer authorizer = mock(AddressBasedAuthorizer.class);
        byte[] senderBytes = ByteUtil.leftPadBytes(new byte[]{0x43}, 20);

        when(provider.getFeePerKbElection(any()))
                .thenReturn(new ABICallElection(authorizer));
        when(tx.getSender())
                .thenReturn(new RskAddress(senderBytes));
        when(constants.getFeePerKbChangeAuthorizer())
                .thenReturn(authorizer);
        when(authorizer.isAuthorized(tx))
                .thenReturn(true);
        when(authorizer.isAuthorized(tx.getSender()))
                .thenReturn(true);
        when(authorizer.getRequiredAuthorizedKeys())
                .thenReturn(1);
        when(constants.getMaxFeePerKb())
                .thenReturn(Coin.valueOf(MAX_FEE_PER_KB));

        BridgeSupport bridgeSupport = new BridgeSupport(
                constants,
                provider,
                mock(BridgeEventLogger.class),
                mock(BtcLockSenderProvider.class),
                mock(Repository.class),
                block,
                new Context(constants.getBtcParams()),
                new FederationSupport(constants, provider, block),
                mock(BtcBlockStoreWithCache.Factory.class),
                mock(ActivationConfig.ForBlock.class)
        );

        assertThat(bridgeSupport.voteFeePerKbChange(tx, Coin.CENT), is(1));
        verify(provider).setFeePerKb(Coin.CENT);
    }
}
