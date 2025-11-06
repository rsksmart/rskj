/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
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
package co.rsk.rpc.modules.eth;

import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.parameters.AccountOverrideParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public class AccountOverride {

    private BigInteger balance;
    private Long nonce;
    private byte[] code;
    private Map<DataWord, DataWord> state;
    private Map<DataWord, DataWord> stateDiff;
    private RskAddress address;
    private RskAddress movePrecompileToAddress;

    public AccountOverride(RskAddress address) {
        if (address == null) {
            throw invalidParamError("Address cannot be null");
        }
        this.address = address;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        if (balance.compareTo(BigInteger.ZERO) < 0) {
            throw invalidParamError("Balance must be equal or bigger than zero");
        }
        this.balance = balance;
    }

    public Long getNonce() {
        return nonce;
    }

    public void setNonce(Long nonce) {
        if (nonce < 0) {
            throw invalidParamError("Nonce must be equal or bigger than zero");
        }
        this.nonce = nonce;
    }

    public byte[] getCode() {
        return code;
    }

    public void setCode(byte[] code) {
        this.code = code;
    }

    public Map<DataWord, DataWord> getState() {
        return state;
    }

    public void setState(Map<DataWord, DataWord> state) {
        this.state = state;
    }

    public Map<DataWord, DataWord> getStateDiff() {
        return stateDiff;
    }

    public void setStateDiff(Map<DataWord, DataWord> stateDiff) {
        this.stateDiff = stateDiff;
    }

    public RskAddress getAddress() {
        return address;
    }

    public void setMovePrecompileToAddress(RskAddress movePrecompileToAddress) {
        this.movePrecompileToAddress = movePrecompileToAddress;
    }

    public RskAddress getMovePrecompileToAddress() {
        return movePrecompileToAddress;
    }

    public AccountOverride fromAccountOverrideParam(AccountOverrideParam accountOverrideParam) {

        if (accountOverrideParam.getMovePrecompileToAddress() != null) {
            this.setMovePrecompileToAddress(accountOverrideParam.getMovePrecompileToAddress().getAddress());
        }

        if (accountOverrideParam.getBalance() != null) {
            this.setBalance(HexUtils.stringHexToBigInteger(accountOverrideParam.getBalance().getHexNumber()));
        }

        if (accountOverrideParam.getNonce() != null) {
            this.setNonce(HexUtils.jsonHexToLong(accountOverrideParam.getNonce().getHexNumber()));
        }

        if (accountOverrideParam.getCode() != null) {
            this.setCode(accountOverrideParam.getCode().getRawDataBytes());
        }

        if (accountOverrideParam.getState() != null) {
            Map<DataWord, DataWord> newState = new HashMap<>();
            for (Map.Entry<HexDataParam, HexDataParam> entry : accountOverrideParam.getState().entrySet()) {
                newState.put(entry.getKey().getAsDataWord(),entry.getValue().getAsDataWord());
            }
            this.setState(newState);
        }

        if (accountOverrideParam.getStateDiff() != null) {
            Map<DataWord, DataWord> newStateDiff = new HashMap<>();
            for (Map.Entry<HexDataParam, HexDataParam> entry : accountOverrideParam.getStateDiff().entrySet()) {
                newStateDiff.put(entry.getKey().getAsDataWord(),entry.getValue().getAsDataWord());
            }
            this.setStateDiff(newStateDiff);
        }

        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AccountOverride that = (AccountOverride) o;
        return Objects.equals(balance, that.balance) &&
                Objects.equals(nonce, that.nonce) &&
                Objects.deepEquals(code, that.code) &&
                Objects.equals(state, that.state) &&
                Objects.equals(stateDiff, that.stateDiff) &&
                Objects.equals(address, that.address) &&
                Objects.equals(movePrecompileToAddress, that.movePrecompileToAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(balance, nonce, Arrays.hashCode(code), state, stateDiff, address, movePrecompileToAddress);
    }

}
