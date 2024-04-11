/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package co.rsk;

import co.rsk.util.ContractUtil;
import co.rsk.util.HexUtils;
import co.rsk.util.TestUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.Response;
import org.ethereum.config.Constants;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class EtherSwapTest {
    private static final int LOCAL_PORT = 4444;

    private static final CallTransaction.Function CALL_LOCK_FUNCTION = CallTransaction.Function.fromSignature(
            "lock",
            new String[]{"bytes32", "address", "uint256"},
            new String[]{}
    );
    private static final CallTransaction.Function CALL_CLAIM_FUNCTION = CallTransaction.Function.fromSignature(
            "claim",
            new String[]{"bytes32", "uint256", "address", "uint256"},
            new String[]{}
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Response lockTxRequest(String refundAddress, byte[] lockData, BigInteger amount) throws IOException {
        String lockTxRequestContent = "[{\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + refundAddress + "\",\n" +
                "        \"to\": \"0x" + Constants.regtest().getEtherSwapContractAddress() + "\",\n" +
                "        \"data\": \"0x" + ByteUtil.toHexString(lockData) + "\",\n" +
                "        \"value\": \"" + HexUtils.toQuantityJsonHex(amount.longValue()) + "\",\n" +
                "        \"gas\": \"0xc350\",\n" +
                "        \"gasPrice\": \"0x1\"\n" +
                "    }],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return TestUtils.sendJsonRpcMessage(lockTxRequestContent, LOCAL_PORT);
    }

    private Response claimTxRequest(String claimAddress, byte[] claimData) throws IOException {
        String claimTxRequestContent = "[{\n" +
                "    \"method\": \"eth_sendTransaction\",\n" +
                "    \"params\": [{\n" +
                "        \"from\": \"" + claimAddress + "\",\n" +
                "        \"to\": \"0x" + Constants.regtest().getEtherSwapContractAddress() + "\",\n" +
                "        \"data\": \"0x" + ByteUtil.toHexString(claimData) + "\",\n" +
                "        \"value\": \"0x0\",\n" +
                "        \"gas\": \"0xc350\",\n" +
                "        \"gasPrice\": \"0x1\"\n" +
                "    }],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return TestUtils.sendJsonRpcMessage(claimTxRequestContent, LOCAL_PORT);
    }

    private Response getBalanceRequest(String address) throws IOException {
        String getBalanceRequestContent = "[{\n" +
                "    \"method\": \"eth_getBalance\",\n" +
                "    \"params\": [\n" +
                "        \"" + address + "\",\n" +
                "        \"latest\"\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return TestUtils.sendJsonRpcMessage(getBalanceRequestContent, LOCAL_PORT);
    }

    private Response getTxReceiptRequest(String txHash) throws IOException {
        String getReceiptRequestContent = "[{\n" +
                "    \"method\": \"eth_getTransactionReceipt\",\n" +
                "    \"params\": [\n" +
                "        \"" + txHash + "\"\n" +
                "    ],\n" +
                "    \"id\": 1,\n" +
                "    \"jsonrpc\": \"2.0\"\n" +
                "}]";

        return TestUtils.sendJsonRpcMessage(getReceiptRequestContent, LOCAL_PORT);
    }

    @Test
    void whenClaimTxIsSend_shouldShouldFailDueToLowFundsInContract() throws Exception {
        String refundAddress = "0x7986b3df570230288501eea3d890bd66948c9b79";
        String claimAddress = "0x8486054b907b0d79569723c761b7113736d32c5a";
        byte[] preimage = "preimage".getBytes(StandardCharsets.UTF_8);
        byte[] preimageHash = HashUtil.sha256(ContractUtil.encodePacked(preimage));
        BigInteger amount = BigInteger.valueOf(5000);

        byte[] lockData = CALL_LOCK_FUNCTION.encode(
                preimageHash,
                claimAddress,
                8000000);

        byte[] claimData = CALL_CLAIM_FUNCTION.encode(
                preimage,
                amount,
                refundAddress,
                8000000);


        Response getBalanceResponse = getBalanceRequest(claimAddress);
        JsonNode jsonRpcResponse = objectMapper.readTree(getBalanceResponse.body().string());
        JsonNode currentBalance = jsonRpcResponse.get(0).get("result");
        BigInteger balanceBigInt = new BigInteger(HexUtils.removeHexPrefix(currentBalance.asText()), 16);
        Assertions.assertEquals(0, balanceBigInt.compareTo(BigInteger.ZERO));

        lockTxRequest(refundAddress, lockData, amount);
        TimeUnit.SECONDS.sleep(5);

        claimTxRequest(claimAddress, claimData);
        TimeUnit.SECONDS.sleep(5);

        getBalanceResponse = getBalanceRequest(claimAddress);
        jsonRpcResponse = objectMapper.readTree(getBalanceResponse.body().string());
        currentBalance = jsonRpcResponse.get(0).get("result");
        balanceBigInt = new BigInteger(HexUtils.removeHexPrefix(currentBalance.asText()), 16);

        Assertions.assertEquals(0, balanceBigInt.compareTo(BigInteger.ZERO));
    }


    @Test
    void whenClaimTxIsSend_shouldExecuteEvenIfSenderHasNoFunds() throws Exception {
        String refundAddress = "0x7986b3df570230288501eea3d890bd66948c9b79";
        String claimAddress = "0x8486054b907b0d79569723c761b7113736d32c5a";
        byte[] preimage = "preimage".getBytes(StandardCharsets.UTF_8);
        byte[] preimageHash = HashUtil.sha256(ContractUtil.encodePacked(preimage));
        BigInteger amount = BigInteger.valueOf(500000);

        byte[] lockData = CALL_LOCK_FUNCTION.encode(
                preimageHash,
                claimAddress,
                8000000);

        byte[] claimData = CALL_CLAIM_FUNCTION.encode(
                preimage,
                amount,
                refundAddress,
                8000000);


        Response getBalanceResponse = getBalanceRequest(claimAddress);
        JsonNode jsonRpcResponse = objectMapper.readTree(getBalanceResponse.body().string());
        JsonNode currentBalance = jsonRpcResponse.get(0).get("result");
        BigInteger balanceBigInt = new BigInteger(HexUtils.removeHexPrefix(currentBalance.asText()), 16);
        Assertions.assertEquals(0, balanceBigInt.compareTo(BigInteger.ZERO));

        lockTxRequest(refundAddress, lockData, amount);
        TimeUnit.SECONDS.sleep(5);

        Response claimResponse = claimTxRequest(claimAddress, claimData);
        TimeUnit.SECONDS.sleep(5);

        jsonRpcResponse = objectMapper.readTree(claimResponse.body().string());
        JsonNode claimTxHash = jsonRpcResponse.get(0).get("result");

        Response getTxReceiptResponse = getTxReceiptRequest(claimTxHash.asText());
        jsonRpcResponse = objectMapper.readTree(getTxReceiptResponse.body().string());
        JsonNode gasUsed = jsonRpcResponse.get(0).get("result").get("gasUsed");
        BigInteger expectedBalance = amount.subtract(new BigInteger(HexUtils.removeHexPrefix(gasUsed.asText()), 16));

        getBalanceResponse = getBalanceRequest(claimAddress);
        jsonRpcResponse = objectMapper.readTree(getBalanceResponse.body().string());
        currentBalance = jsonRpcResponse.get(0).get("result");
        balanceBigInt = new BigInteger(HexUtils.removeHexPrefix(currentBalance.asText()), 16);

        Assertions.assertEquals(0, balanceBigInt.compareTo(expectedBalance));
    }
}
