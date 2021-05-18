package co.rsk.peg.peginstrategy;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.panic.PanicProcessor;
import co.rsk.peg.*;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.TransferInvoke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base Class to hold common methods for all PegIn versions.
 * <p>
 * Created by Kelvin Isievwore on 11/05/2021.
 */
public abstract class PegInVersionAbstractClass {

    private static final Logger logger = LoggerFactory.getLogger(PegInVersionAbstractClass.class);

    private static final PanicProcessor panicProcessor = new PanicProcessor();

    final BridgeEventLogger eventLogger;
    final ActivationConfig.ForBlock activations;
    final Repository rskRepository;
    final FederationSupport federationSupport;
    final BridgeSupport bridgeSupport;
    final BridgeStorageProvider provider;
    final BridgeConstants bridgeConstants;
    final Context btcContext;
    final Block rskExecutionBlock;

    private final List<ProgramSubtrace> subtraces = new ArrayList<>();

    protected PegInVersionAbstractClass(BridgeEventLogger eventLogger,
                                        ActivationConfig.ForBlock activations,
                                        Repository rskRepository,
                                        FederationSupport federationSupport,
                                        BridgeSupport bridgeSupport,
                                        BridgeStorageProvider provider,
                                        BridgeConstants bridgeConstants,
                                        Block rskExecutionBlock) {
        this.eventLogger = eventLogger;
        this.activations = activations;
        this.rskRepository = rskRepository;
        this.federationSupport = federationSupport;
        this.bridgeSupport = bridgeSupport;
        this.provider = provider;
        this.bridgeConstants = bridgeConstants;
        this.btcContext = new Context(bridgeConstants.getBtcParams());
        this.rskExecutionBlock = rskExecutionBlock;
    }

    public List<ProgramSubtrace> getSubtraces() {
        return Collections.unmodifiableList(this.subtraces);
    }

    public void executePegIn(BtcTransaction btcTx, PeginInformation peginInformation, Coin amount) throws IOException {
        RskAddress rskDestinationAddress = peginInformation.getRskDestinationAddress();
        Address senderBtcAddress = peginInformation.getSenderBtcAddress();
        BtcLockSender.TxSenderAddressType senderBtcAddressType = peginInformation.getSenderBtcAddressType();
        int protocolVersion = peginInformation.getProtocolVersion();
        co.rsk.core.Coin amountInWeis = co.rsk.core.Coin.fromBitcoin(amount);

        logger.debug("[executePegIn] [btcTx:{}] Is a lock from a {} sender", btcTx.getHash(), senderBtcAddressType);
        transferTo(peginInformation.getRskDestinationAddress(), amountInWeis);
        logger.info(
                "[executePegIn] Transferring from BTC Address {}. RSK Address: {}. Amount: {}",
                senderBtcAddress,
                rskDestinationAddress,
                amountInWeis
        );

        if (activations.isActive(ConsensusRule.RSKIP146)) {
            if (activations.isActive(ConsensusRule.RSKIP170)) {
                eventLogger.logPeginBtc(rskDestinationAddress, btcTx, amount, protocolVersion);
            } else {
                eventLogger.logLockBtc(rskDestinationAddress, btcTx, senderBtcAddress, amount);
            }
        }

        // Save UTXOs from the federation(s) only if we actually locked the funds
        federationSupport.saveNewUTXOs(btcTx);
    }

    /**
     * Internal method to transfer RSK to an RSK account
     * It also produce the appropiate internal transaction subtrace if needed
     *
     * @param receiver address that receives the amount
     * @param amount   amount to transfer
     */
    public void transferTo(RskAddress receiver, co.rsk.core.Coin amount) {
        rskRepository.transfer(
                PrecompiledContracts.BRIDGE_ADDR,
                receiver,
                amount
        );

        DataWord from = DataWord.valueOf(PrecompiledContracts.BRIDGE_ADDR.getBytes());
        DataWord to = DataWord.valueOf(receiver.getBytes());
        long gas = 0L;
        DataWord value = DataWord.valueOf(amount.getBytes());

        TransferInvoke invoke = new TransferInvoke(from, to, gas, value);
        ProgramResult result = ProgramResult.empty();
        ProgramSubtrace subtrace = ProgramSubtrace.newCallSubtrace(CallType.CALL, invoke, result, null, Collections.emptyList());

        logger.info("Transferred {} weis to {}", amount, receiver);

        this.subtraces.add(subtrace);
    }

    public boolean verifyLockDoesNotSurpassLockingCap(BtcTransaction btcTx, Coin totalAmount) {
        if (!activations.isActive(ConsensusRule.RSKIP134)) {
            return true;
        }

        Coin fedCurrentFunds = bridgeSupport.getBtcLockedInFederation();
        Coin lockingCap = bridgeSupport.getLockingCap();
        logger.trace("Evaluating locking cap for: TxId {}. Value to lock {}. Current funds {}. Current locking cap {}", btcTx.getHash(true), totalAmount, fedCurrentFunds, lockingCap);
        Coin fedUTXOsAfterThisLock = fedCurrentFunds.add(totalAmount);
        // If the federation funds (including this new UTXO) are smaller than or equals to the current locking cap, we are fine.
        if (fedUTXOsAfterThisLock.compareTo(lockingCap) <= 0) {
            return true;
        }

        logger.info("locking cap exceeded! btc Tx {}", btcTx);
        return false;
    }

    public void generateRejectionRelease(
            BtcTransaction btcTx,
            Address senderBtcAddress,
            Transaction rskTx,
            Coin totalAmount
    ) throws IOException {
        WalletProvider createWallet = (BtcTransaction a, Address b) -> {
            // Build the list of UTXOs in the BTC transaction sent to either the active
            // or retiring federation
            List<UTXO> utxosToUs = btcTx.getWalletOutputs(
                    federationSupport.getNoSpendWalletForLiveFederations(false)
            )
                    .stream()
                    .map(output ->
                            new UTXO(
                                    btcTx.getHash(),
                                    output.getIndex(),
                                    output.getValue(),
                                    0,
                                    btcTx.isCoinBase(),
                                    output.getScriptPubKey()
                            )
                    ).collect(Collectors.toList());
            // Use the list of UTXOs to build a transaction builder
            // for the return btc transaction generation
            return federationSupport.getUTXOBasedWalletForLiveFederations(utxosToUs, false);
        };
        generateRejectionRelease(btcTx, senderBtcAddress, rskTx.getHash(), totalAmount, createWallet);
    }

    public void generateRejectionRelease(
            BtcTransaction btcTx,
            Address btcRefundAddress,
            Keccak256 rskTxHash,
            Coin totalAmount,
            WalletProvider walletProvider) throws IOException {

        ReleaseTransactionBuilder txBuilder = new ReleaseTransactionBuilder(
                btcContext.getParams(),
                walletProvider.provide(btcTx, null),
                btcRefundAddress,
                getFeePerKb(),
                activations
        );

        Optional<ReleaseTransactionBuilder.BuildResult> buildReturnResult = txBuilder.buildEmptyWalletTo(btcRefundAddress);
        if (buildReturnResult.isPresent()) {
            if (activations.isActive(ConsensusRule.RSKIP146)) {
                provider.getReleaseTransactionSet().add(buildReturnResult.get().getBtcTx(), rskExecutionBlock.getNumber(), rskTxHash);
                eventLogger.logReleaseBtcRequested(rskTxHash.getBytes(), buildReturnResult.get().getBtcTx(), totalAmount);
            } else {
                provider.getReleaseTransactionSet().add(buildReturnResult.get().getBtcTx(), rskExecutionBlock.getNumber());
            }
            logger.info("Rejecting peg-in: return tx build successful to {}. Tx {}. Value {}.", btcRefundAddress, rskTxHash, totalAmount);
        } else {
            logger.warn("Rejecting peg-in: return tx build for btc tx {} error. Return was to {}. Tx {}. Value {}", btcTx.getHash(), btcRefundAddress, rskTxHash, totalAmount);
            panicProcessor.panic("peg-in-refund", String.format("peg-in money return tx build for btc tx %s error. Return was to %s. Tx %s. Value %s", btcTx.getHash(), btcRefundAddress, rskTxHash, totalAmount));
        }
    }

    /**
     * @return Current fee per kb in BTC.
     */
    public Coin getFeePerKb() {
        Coin currentFeePerKb = provider.getFeePerKb();

        if (currentFeePerKb == null) {
            currentFeePerKb = bridgeConstants.getGenesisFeePerKb();
        }

        return currentFeePerKb;
    }
}
