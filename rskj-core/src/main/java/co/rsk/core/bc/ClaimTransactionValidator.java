package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.RepositorySnapshot;
import co.rsk.util.HexUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ClaimTransactionValidator {
    private static final String SWAPS_MAP_POSITION = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String CLAIM_METHOD_ABI = "[{\"constant\":false,\"inputs\":[{\"name\":\"preimage\",\"type\":\"bytes32\"}, {\"name\":\"amount\",\"type\":\"uint256\"}, {\"name\":\"address\",\"type\":\"address\"}, {\"name\":\"timelock\",\"type\":\"uint256\"}],\"name\":\"claim\",\"outputs\":[],\"payable\":false,\"type\":\"function\"}]";

    private final SignatureCache signatureCache;

    @Nonnull
    private final Constants constants;

    private final CallTransaction.Contract contract = new CallTransaction.Contract(CLAIM_METHOD_ABI);;

    public ClaimTransactionValidator(SignatureCache signatureCache,
                                      @Nonnull Constants constants) {
        this.signatureCache = signatureCache;
        this.constants = Objects.requireNonNull(constants);
    }

    private byte[] calculateSwapHash(Transaction newTx) {

        CallTransaction.Invocation invocation = getTxInvocation(newTx);

        // Get arguments from the invocation object
        byte[] preimage = (byte[]) invocation.args[0];
        byte[] preimageHash = HashUtil.sha256(encodePacked(preimage));
        BigInteger amount = (BigInteger) invocation.args[1];
        DataWord refundAddress = (DataWord) invocation.args[2];
        BigInteger timeLock = (BigInteger) invocation.args[3];


        // Remove the leading zeroes from the arguments bytes and merge them
        byte[] argumentsBytes = encodePacked(
                preimageHash,
                amount,
                newTx.getSender(signatureCache),
                new RskAddress(ByteUtil.toHexString(refundAddress.getLast20Bytes())),
                timeLock
        );

        return HashUtil.keccak256(argumentsBytes);
    }

    private CallTransaction.Invocation getTxInvocation(Transaction newTx) {
        return contract.parseInvocation(newTx.getData());
    }

    private Coin getLockedAmount(Transaction tx) {
        CallTransaction.Invocation invocation = getTxInvocation(tx);
        BigInteger amount = (BigInteger) invocation.args[1];

        return Coin.valueOf(amount.longValue());
    }

    private Coin getTxCost(Transaction tx) {
        BigInteger gasLimit = BigInteger.valueOf(GasCost.toGas(tx.getGasLimit()));

        return tx.getValue().add(tx.getGasPrice().multiply(gasLimit));
    }

    private boolean isSameTx(Transaction pendingTx, Transaction newTx) {
        return pendingTx.getSender(signatureCache).toHexString().equals(newTx.getSender(signatureCache).toHexString())
                && pendingTx.getReceiveAddress().toHexString().equals(newTx.getReceiveAddress().toHexString())
                && Arrays.equals(pendingTx.getData(), newTx.getData());
    }

    public byte[] encodePacked(Object...arguments) {
        byte[] encodedArguments = new byte[]{};

        for(Object arg: arguments) {
            byte[] encodedArg = encodeArgumentAccordingInstanceType(arg);
            encodedArguments = ByteUtil.merge(encodedArguments, encodedArg);
        }

        return encodedArguments;
    }

    private static byte[] encodeArgumentAccordingInstanceType(Object arg) {
        if (arg instanceof byte[]) {
            SolidityType bytes32Type = SolidityType.getType(SolidityType.BYTES32);
            return bytes32Type.encode(arg);
        } else if (arg instanceof RskAddress) {
            SolidityType addressType = SolidityType.getType(SolidityType.ADDRESS);
            byte[] encodedAddress = addressType.encode(((RskAddress) arg).toHexString());
            return org.bouncycastle.util.Arrays.copyOfRange(encodedAddress, 12, encodedAddress.length);
        } else if (arg instanceof BigInteger) {
            SolidityType uint256Type = SolidityType.getType(SolidityType.UINT);
            return uint256Type.encode(arg);
        }
        return new byte[]{};
    }

    public boolean isClaimTx(Transaction tx) {
        return tx.getReceiveAddress() != null
                && tx.getData() != null
                && constants.getEtherSwapContractAddress().equalsIgnoreCase(tx.getReceiveAddress().toHexString())
                && tx.getData().length > 4
                && Arrays.equals(Arrays.copyOfRange(tx.getData(), 0, 4), Constants.CLAIM_FUNCTION_SIGNATURE);
    }

    public boolean hasLockedFunds(Transaction tx, RepositorySnapshot repositorySnapshot) {
        String swapHash = HexUtils.toUnformattedJsonHex(calculateSwapHash(tx));
        byte[] key = HashUtil.keccak256(HexUtils.decode(HexUtils.stringToByteArray(swapHash + SWAPS_MAP_POSITION)));

        DataWord swapRecord = repositorySnapshot.getStorageValue(tx.getReceiveAddress(), DataWord.valueOf(key));

        return swapRecord != null;
    }

    public boolean isFeatureActive (ActivationConfig.ForBlock activationConfig) {
        return activationConfig.isActive(ConsensusRule.RSKIP432);
    }

    public boolean isClaimTxAndValid(Transaction tx, RepositorySnapshot repositorySnapshot, ActivationConfig.ForBlock activationConfig) {
        if(!isFeatureActive(activationConfig)) {
            return false;
        }

        if(!isClaimTx(tx)) {
            return false;
        }

        if(hasLockedFunds(tx, repositorySnapshot)){
            Coin lockedAmount = getLockedAmount(tx);

            Coin balanceWithClaim = repositorySnapshot.getBalance(tx.getSender(signatureCache))
                    .add(lockedAmount);

            Coin txCost = getTxCost(tx);

            return txCost.compareTo(balanceWithClaim) <= 0;
        }

        return false;
    }

    public boolean canPayPendingAndNewClaimTx(Transaction newTx, RepositorySnapshot repositorySnapshot, List<Transaction> pendingTransactions) {
        if(!isClaimTx(newTx)
                || !hasLockedFunds(newTx, repositorySnapshot)) {
            return false;
        }

        Coin totalLockedAmount = getLockedAmount(newTx);
        Coin totalClaimTxCost = getTxCost(newTx);
        Coin totalPendingTxCost = Coin.ZERO;

        for (Transaction tx : pendingTransactions) {
            if(isSameTx(tx, newTx)) {
                return false;
            }

            boolean isReplacedTx = Arrays.equals(tx.getNonce(), newTx.getNonce());

            if (isReplacedTx) {
                continue;
            }

            if(isClaimTx(tx)) {
                totalClaimTxCost = totalClaimTxCost.add(getTxCost(tx));
                totalLockedAmount = totalLockedAmount.add(getLockedAmount(tx));
            } else {
                totalPendingTxCost = totalPendingTxCost.add(getTxCost(tx));
            }
        }

        Coin senderBalance = repositorySnapshot.getBalance(newTx.getSender(signatureCache));
        Coin balanceAfterPendingTx = senderBalance.subtract(totalPendingTxCost);


        return balanceAfterPendingTx.add(totalLockedAmount).compareTo(totalClaimTxCost) >= 0;
    }

    public SignatureCache getSignatureCache() {
        return signatureCache;
    }

    @Nonnull
    public Constants getConstants() {
        return constants;
    }
}
