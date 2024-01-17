package co.rsk.test.builders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.RskAddress;
import co.rsk.peg.Bridge;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeSupportFactory;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.vm.MessageCall;
import org.ethereum.vm.MessageCall.MsgType;
import org.ethereum.vm.PrecompiledContractArgs;
import org.ethereum.vm.PrecompiledContractArgsBuilder;
import org.ethereum.vm.PrecompiledContracts;

public class BridgeBuilder {
    private RskAddress contractAddress;
    private Constants constants;
    private ActivationConfig activationConfig;
    private BridgeSupport bridgeSupport;
    private SignatureCache signatureCache;

    // Precompiled contract args
    private Transaction transaction;
    private Block executionBlock;
    private MessageCall.MsgType msgType;

    public BridgeBuilder() {
        contractAddress = PrecompiledContracts.BRIDGE_ADDR;
        constants = Constants.mainnet();
        bridgeSupport = new BridgeSupportBuilder().build();
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

        transaction = mock(Transaction.class);
        executionBlock = new BlockGenerator().getGenesisBlock();
        msgType = MsgType.CALL;
    }

    public BridgeBuilder contractAddress(RskAddress contractAddress) {
        this.contractAddress = contractAddress;
        return this;
    }

    public BridgeBuilder constants(Constants constants) {
        this.constants = constants;
        return this;
    }

    public BridgeBuilder activationConfig(ActivationConfig activationConfig) {
        this.activationConfig = activationConfig;
        return this;
    }

    public BridgeBuilder signatureCache(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
        return this;
    }

    public BridgeBuilder bridgeSupport(BridgeSupport bridgeSupport) {
        this.bridgeSupport = bridgeSupport;
        return this;
    }

    public BridgeBuilder transaction(Transaction transaction) {
        this.transaction = transaction;
        return this;
    }

    public BridgeBuilder executionBlock(Block executionBlock) {
        this.executionBlock = executionBlock;
        return this;
    }

    public BridgeBuilder msgType(MsgType msgType) {
        this.msgType = msgType;
        return this;
    }

    public Bridge build() {
        BridgeSupportFactory bridgeSupportFactory = mock(BridgeSupportFactory.class);
        when(bridgeSupportFactory.newInstance(any(), any(), any(), any())).thenReturn(bridgeSupport);

        Bridge bridge = new Bridge(
            contractAddress,
            constants,
            activationConfig,
            bridgeSupportFactory,
            signatureCache
        );

        PrecompiledContractArgs args = PrecompiledContractArgsBuilder.builder()
            .transaction(transaction)
            .executionBlock(executionBlock)
            .msgType(msgType)
            .build();
        bridge.init(args);

        return bridge;
    }
}
