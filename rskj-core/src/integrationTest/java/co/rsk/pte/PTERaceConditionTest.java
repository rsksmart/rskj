/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.pte;

import co.rsk.util.*;

import co.rsk.util.cli.NodeIntegrationTestCommandLine;
import co.rsk.util.rpc.ContractCaller;
import co.rsk.util.rpc.ContractDeployer;
import co.rsk.util.rpc.RPCBlockRequests;
import co.rsk.util.rpc.SimpleAbi;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.io.IOException;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PTERaceConditionTest {

    //node
    private static final String RSKJ_SERVER_CONF_FILE_NAME = "pte-race-condition.conf";
    private static final String TAG_TO_REPLACE_SERVER_DATABASE_PATH = "<SERVER_NODE_DATABASE_PATH>";
    private static final String TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT = "<CLIENT_RPC_HTTP_PORT>";
    private int rpcPort;
    @TempDir
    public Path tempDirectory;
    private NodeIntegrationTestCommandLine serverNode;

    //needed for testing
    private static final String RACE_POC_BYTECODE = "0x6080604052348015600e575f5ffd5b50610dc78061001c5f395ff3fe608060405234801561000f575f5ffd5b506004361061007b575f3560e01c8063c0e693ce11610059578063c0e693ce146100e7578063e1fa8e8414610103578063ed3c7d401461011f578063ee9a31a21461013b5761007b565b8063517dc9d41461007f57806352ad0d5e1461009b578063bf3fd741146100cb575b5f5ffd5b61009960048036038101906100949190610ad3565b610159565b005b6100b560048036038101906100b09190610ad3565b61033f565b6040516100c29190610b71565b60405180910390f35b6100e560048036038101906100e09190610bbd565b61035b565b005b61010160048036038101906100fc9190610bbd565b61070e565b005b61011d60048036038101906101189190610ad3565b61099b565b005b61013960048036038101906101349190610ad3565b610a5a565b005b610143610a94565b6040516101509190610c3a565b60405180910390f35b6001600281111561016d5761016c610afe565b5b5f5f8381526020019081526020015f205f9054906101000a900460ff16600281111561019c5761019b610afe565b5b146101de57806040517f8cc00d160000000000000000000000000000000000000000000000000000000081526004016101d59190610c62565b60405180910390fd5b60025f5f8381526020019081526020015f205f6101000a81548160ff0219169083600281111561021157610210610afe565b5b02179055505f630100000673ffffffffffffffffffffffffffffffffffffffff166040516024016040516020818303038152906040527f33e08fed000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516102c29190610ccd565b5f604051808303815f865af19150503d805f81146102fb576040519150601f19603f3d011682016040523d82523d5f602084013e610300565b606091505b505090508061033b576040517f84e8169200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5050565b5f602052805f5260405f205f915054906101000a900460ff1681565b5f630100000673ffffffffffffffffffffffffffffffffffffffff166040516024016040516020818303038152906040527f14c89c01000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516104079190610ccd565b5f604051808303815f865af19150503d805f8114610440576040519150601f19603f3d011682016040523d82523d5f602084013e610445565b606091505b5050905080610480576040517f84e8169200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b6001600281111561049457610493610afe565b5b5f5f8581526020019081526020015f205f9054906101000a900460ff1660028111156104c3576104c2610afe565b5b1461050557826040517f8cc00d160000000000000000000000000000000000000000000000000000000081526004016104fc9190610c62565b60405180910390fd5b60025f5f8581526020019081526020015f205f6101000a81548160ff0219169083600281111561053857610537610afe565b5b02179055505f5f630100000673ffffffffffffffffffffffffffffffffffffffff168460405160240161056b9190610cf2565b6040516020818303038152906040527f5521647a000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516105f59190610ccd565b5f604051808303815f865af19150503d805f811461062e576040519150601f19603f3d011682016040523d82523d5f602084013e610633565b606091505b50915091508161066f576040517f84e8169200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b6020815110156106ab576040517f455639ff00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f818060200190518101906106c09190610d3e565b90505f811461070657806040517fbb56685c0000000000000000000000000000000000000000000000000000000081526004016106fd9190610d78565b60405180910390fd5b505050505050565b6001600281111561072257610721610afe565b5b5f5f8481526020019081526020015f205f9054906101000a900460ff16600281111561075157610750610afe565b5b1461079357816040517f8cc00d1600000000000000000000000000000000000000000000000000000000815260040161078a9190610c62565b60405180910390fd5b60025f5f8481526020019081526020015f205f6101000a81548160ff021916908360028111156107c6576107c5610afe565b5b02179055505f5f630100000673ffffffffffffffffffffffffffffffffffffffff16836040516024016107f99190610cf2565b6040516020818303038152906040527f5521647a000000000000000000000000000000000000000000000000000000007bffffffffffffffffffffffffffffffffffffffffffffffffffffffff19166020820180517bffffffffffffffffffffffffffffffffffffffffffffffffffffffff83818316178352505050506040516108839190610ccd565b5f604051808303815f865af19150503d805f81146108bc576040519150601f19603f3d011682016040523d82523d5f602084013e6108c1565b606091505b5091509150816108fd576040517f84e8169200000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b602081511015610939576040517f455639ff00000000000000000000000000000000000000000000000000000000815260040160405180910390fd5b5f8180602001905181019061094e9190610d3e565b90505f811461099457806040517fbb56685c00000000000000000000000000000000000000000000000000000000815260040161098b9190610d78565b60405180910390fd5b5050505050565b5f60028111156109ae576109ad610afe565b5b5f5f8381526020019081526020015f205f9054906101000a900460ff1660028111156109dd576109dc610afe565b5b14610a1f57806040517f77caf672000000000000000000000000000000000000000000000000000000008152600401610a169190610c62565b60405180910390fd5b60015f5f8381526020019081526020015f205f6101000a81548160ff02191690836002811115610a5257610a51610afe565b5b021790555050565b5f5f5f8381526020019081526020015f205f6101000a81548160ff02191690836002811115610a8c57610a8b610afe565b5b021790555050565b630100000681565b5f5ffd5b5f819050919050565b610ab281610aa0565b8114610abc575f5ffd5b50565b5f81359050610acd81610aa9565b92915050565b5f60208284031215610ae857610ae7610a9c565b5b5f610af584828501610abf565b91505092915050565b7f4e487b71000000000000000000000000000000000000000000000000000000005f52602160045260245ffd5b60038110610b3c57610b3b610afe565b5b50565b5f819050610b4c82610b2b565b919050565b5f610b5b82610b3f565b9050919050565b610b6b81610b51565b82525050565b5f602082019050610b845f830184610b62565b92915050565b5f819050919050565b610b9c81610b8a565b8114610ba6575f5ffd5b50565b5f81359050610bb781610b93565b92915050565b5f5f60408385031215610bd357610bd2610a9c565b5b5f610be085828601610abf565b9250506020610bf185828601610ba9565b9150509250929050565b5f73ffffffffffffffffffffffffffffffffffffffff82169050919050565b5f610c2482610bfb565b9050919050565b610c3481610c1a565b82525050565b5f602082019050610c4d5f830184610c2b565b92915050565b610c5c81610aa0565b82525050565b5f602082019050610c755f830184610c53565b92915050565b5f81519050919050565b5f81905092915050565b8281835e5f83830152505050565b5f610ca782610c7b565b610cb18185610c85565b9350610cc1818560208601610c8f565b80840191505092915050565b5f610cd88284610c9d565b915081905092915050565b610cec81610b8a565b82525050565b5f602082019050610d055f830184610ce3565b92915050565b5f819050919050565b610d1d81610d0b565b8114610d27575f5ffd5b50565b5f81519050610d3881610d14565b92915050565b5f60208284031215610d5357610d52610a9c565b5b5f610d6084828501610d2a565b91505092915050565b610d7281610d0b565b82525050565b5f602082019050610d8b5f830184610d69565b9291505056fea264697066735822122016e3710515fe8f2a72e734d575cbdafc11ace8b652dd9a36a6df286b519c2f9364736f6c634300081f0033";
    private static final String BRIDGE_ADDRESS = "0x0000000000000000000000000000000001000006";
    private static final byte[] RACE_ID = HexUtils.stringHexToByteArray("0x8f3c9a2e4b7d1c6f0a9e5d2b4c8a1f7e3b6d9c0e5a2f4b1d7c9e8a6f3d2b0000");

    private ContractCaller racePOCContractCaller;
    private long initialBlock = 0;
    private List<String> senders;


    @BeforeEach
    void setup() throws Exception {
        //node
        this.rpcPort = IntegrationTestUtils.findFreePort();
        Path serverDbDir = tempDirectory.resolve("server/database");
        String rskConfFileChangedServer = configureServerWithGeneratedInformation(serverDbDir);
        serverNode = new NodeIntegrationTestCommandLine(rskConfFileChangedServer, "--regtest");
        serverNode.startNode();
        IntegrationTestUtils.waitFor(20, SECONDS);

        this.initialBlock = RPCBlockRequests.getLatestBlockNumber(this.rpcPort);
        assertTrue(initialBlock > 0);

        //accounts to be used
        String coordinatorAccount = OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(0);
        String authorizedBridgeAccount = OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(1);
        this.senders = buildSenderList();

        //contracts to be used
        String racePOCContractAddress = ContractDeployer.deploy(rpcPort, OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(0), RACE_POC_BYTECODE, 100000);
        this.racePOCContractCaller = new ContractCaller(rpcPort, racePOCContractAddress);
        ContractCaller bridgeContractCaller = new ContractCaller(rpcPort, BRIDGE_ADDRESS);

        //call reset method
        String resetData = SimpleAbi.encode("reset(bytes32)", List.of(RACE_ID));
        Optional<String> resetTx = this.racePOCContractCaller.callIgnoreFailure(coordinatorAccount, resetData);
        resetTx.ifPresent(txHash -> RpcTransactionAssertions.assertMinedSuccess(rpcPort, 50, 2000, txHash));

        //call register method
        String registerData = SimpleAbi.encode("register(bytes32)", List.of(RACE_ID));
        Optional<String> registerTx = this.racePOCContractCaller.call(coordinatorAccount, registerData);
        String registerTxHash = registerTx.orElseThrow(() -> new AssertionError("register tx was not sent"));
        RpcTransactionAssertions.assertMinedSuccess(rpcPort, 50, 2000, registerTxHash);


        // allow bridge to be called by this.racePOCContractCaller contract
        String setBridgeData = SimpleAbi.encode("setUnionBridgeContractAddressForTestnet(address)", List.of(HexUtils.stringHexToByteArray(racePOCContractAddress)));
        String setBridgeTx = bridgeContractCaller.call(authorizedBridgeAccount, setBridgeData).orElseThrow(() -> new AssertionError("setBridge tx not sent"));
        RpcTransactionAssertions.assertMinedSuccess(rpcPort, 50, 2000, setBridgeTx);

        IntegrationTestUtils.waitFor(10, SECONDS);
    }

    @Test
    void whenMultipleSendersCallAcceptSimpleAllMustBeSequential_noPteEdges() throws Exception {
        List<String> sameBlockTransactions = new ArrayList<>();

        String acceptSimpleData = SimpleAbi.encode("acceptSimple(bytes32)", List.of((RACE_ID)));
        for (String from : senders) {
            String txHash = this.racePOCContractCaller.call(from, acceptSimpleData)
                    .orElseThrow(() -> new AssertionError("acceptSimple tx not sent for " + from));
            sameBlockTransactions.add(txHash);
        }
        IntegrationTestUtils.waitFor(10, SECONDS);

        long minedBlock = RpcTransactionAssertions.assertAllMinedInSameBlock(this.rpcPort, sameBlockTransactions);

        Assertions.assertTrue(
                minedBlock > this.initialBlock,
                "Expected mined block > initial block. initial=" + this.initialBlock + " mined=" + minedBlock
        );

        JsonNode block = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(rpcPort, HexUtils.toQuantityJsonHex(minedBlock));
        Assertions.assertNotNull(block);
        Assertions.assertTrue(block.get(0).get("result").get("rskPteEdges").isEmpty());
    }

    @Test
    void whenMultipleSendersCallAcceptUnionAllMustBeSequential_noPteEdges() throws Exception {
        BigInteger amountWei = BigInteger.valueOf(123);
        List<String> sameBlockTransactions = new ArrayList<>();

        String acceptUnionData = SimpleAbi.encode("acceptUnion(bytes32,uint256)", List.of(RACE_ID, amountWei));

        for (String from : senders) {
            String txHash = this.racePOCContractCaller.call(from, acceptUnionData)
                    .orElseThrow(() -> new AssertionError("acceptUnion tx not sent for " + from));
            sameBlockTransactions.add(txHash);
        }

        IntegrationTestUtils.waitFor(10, SECONDS);

        long minedBlock = RpcTransactionAssertions.assertAllMinedInSameBlock(rpcPort, sameBlockTransactions);

        Assertions.assertTrue(
                minedBlock > this.initialBlock,
                "Expected mined block > initial block. initial=" + this.initialBlock + " mined=" + minedBlock
        );

        JsonNode block = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(
                rpcPort,
                HexUtils.toQuantityJsonHex(minedBlock)
        );
        Assertions.assertNotNull(block);
        Assertions.assertTrue(block.get(0).get("result").get("rskPteEdges").isEmpty());
    }

    @Test
    public void whenMultipleSendersCallAcceptUnionPrecheckAllMustBeSequential_noPteEdges() throws Exception {
        BigInteger amountWei = BigInteger.valueOf(123);
        List<String> sameBlockTransactions = new ArrayList<>();

        String acceptUnionData = SimpleAbi.encode("acceptUnionPrecheck(bytes32,uint256)", List.of(RACE_ID, amountWei));

        for (String from : senders) {
            String txHash = this.racePOCContractCaller.call(from, acceptUnionData)
                    .orElseThrow(() -> new AssertionError("acceptUnionPrecheck tx not sent for " + from));
            sameBlockTransactions.add(txHash);
        }

        IntegrationTestUtils.waitFor(10, SECONDS);

        long minedBlock = RpcTransactionAssertions.assertAllMinedInSameBlock(rpcPort, sameBlockTransactions);

        Assertions.assertTrue(
                minedBlock > this.initialBlock,
                "Expected mined block > initial block. initial=" + this.initialBlock + " mined=" + minedBlock
        );

        JsonNode block = OkHttpClientTestFixture.getJsonResponseForGetBestBlockMessage(
                rpcPort,
                HexUtils.toQuantityJsonHex(minedBlock)
        );
        Assertions.assertNotNull(block);
        Assertions.assertTrue(block.get(0).get("result").get("rskPteEdges").isEmpty());

    }

    private static List<String> buildSenderList() {
        return List.of(
                OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(2),
                OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(3),
                OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(4),
                OkHttpClientTestFixture.PRE_FUNDED_ACCOUNTS.get(5)
        );
    }


    private String configureServerWithGeneratedInformation(Path tempDirDatabaseServerPath) throws IOException {
        String originRskConfFileServer = FilesHelper.getAbsolutPathFromResourceFile(getClass(), RSKJ_SERVER_CONF_FILE_NAME);
        Path rskConfFileServer = tempDirectory.resolve("server/" + RSKJ_SERVER_CONF_FILE_NAME);
        rskConfFileServer.getParent().toFile().mkdirs();
        Files.copy(Paths.get(originRskConfFileServer), rskConfFileServer);

        List<Pair<String, String>> tagsWithValues = new ArrayList<>();
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_SERVER_DATABASE_PATH, tempDirDatabaseServerPath.toString()));
        tagsWithValues.add(new ImmutablePair<>(TAG_TO_REPLACE_CLIENT_RPC_HTTP_PORT, String.valueOf(this.rpcPort)));
        RskjConfigurationFileFixture.substituteTagsOnRskjConfFile(rskConfFileServer.toString(), tagsWithValues);

        return rskConfFileServer.toString();
    }
}