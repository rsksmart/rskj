/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.validators;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.SenderResolverVisitor;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.config.Constants;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by SDL on 12/4/2017.
 */
public class BlockTxsFieldsValidationRule implements BlockParentDependantValidationRule {
    private static final Logger logger = LoggerFactory.getLogger("blockvalidator");
    private final SenderResolverVisitor senderResolver;

    public BlockTxsFieldsValidationRule(SenderResolverVisitor senderResolver) {
        this.senderResolver = senderResolver;
    }

    @Override
    public boolean isValid(Block block, Block parent) {
        if (block == null) {
            logger.warn("BlockTxsFieldsValidationRule - block is null");
            return false;
        }

        List<Transaction> txs = block.getTransactionsList();
        for (Transaction tx : txs) {
            try {
                validate(tx);
            } catch (RuntimeException e) {
                logger.warn("Unable to verify transaction", e);
                return false;
            }
        }

        return true;
    }

    private void validate(Transaction tx) {
        if (tx.getNonce().length > DataWord.BYTES) {
            throw new RuntimeException("Nonce is not valid");
        }
        RskAddress receiveAddress = tx.getReceiveAddress();
        if (receiveAddress != null && receiveAddress.getBytes().length != 0 && receiveAddress.getBytes().length != Constants.getMaxAddressByteLength()) {
            throw new RuntimeException("Receive address is not valid");
        }
        if (tx.getGasLimit().length > DataWord.BYTES) {
            throw new RuntimeException("Gas Limit is not valid");
        }
        Coin gasPrice = tx.getGasPrice();
        if (gasPrice != null && gasPrice.getBytes().length > DataWord.BYTES) {
            throw new RuntimeException("Gas Price is not valid");
        }
        if (tx.getValue().getBytes().length > DataWord.BYTES) {
            throw new RuntimeException("Value is not valid");
        }
        ECKey.ECDSASignature signature = tx.getSignature();
        if (signature != null) {
            if (BigIntegers.asUnsignedByteArray(signature.r).length > DataWord.BYTES) {
                throw new RuntimeException("Signature R is not valid");
            }
            if (BigIntegers.asUnsignedByteArray(signature.s).length > DataWord.BYTES) {
                throw new RuntimeException("Signature S is not valid");
            }
            RskAddress sender = tx.accept(senderResolver);
            if (sender.getBytes() != null && sender.getBytes().length != Constants.getMaxAddressByteLength()) {
                throw new RuntimeException("Sender is not valid");
            }
        }
    }
}
