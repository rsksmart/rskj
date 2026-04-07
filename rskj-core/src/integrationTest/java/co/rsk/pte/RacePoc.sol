// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract RacePoc {
    enum Status {
        NONE,
        REGISTERED,
        ACCEPTED
    }

    mapping(bytes32 => Status) public status;

    address public constant BRIDGE = 0x0000000000000000000000000000000001000006;

    error NotRegistered(bytes32 id);
    error AlreadyRegistered(bytes32 id);
    error PrecompileFailed();
    error BridgeError(int256 code);
    error InvalidReturnData();

    function register(bytes32 id) external {
        if (status[id] != Status.NONE) {
            revert AlreadyRegistered(id);
        }
        status[id] = Status.REGISTERED;
    }

    function acceptSimple(bytes32 id) external {
        if (status[id] != Status.REGISTERED) {
            revert NotRegistered(id);
        }
        status[id] = Status.ACCEPTED;

        (bool ok, ) = BRIDGE.call(abi.encodeWithSignature("getUnionBridgeLockingCap()"));
        if (!ok) {
            revert PrecompileFailed();
        }
    }

    function acceptUnion(bytes32 id, uint256 amount) external {
        if (status[id] != Status.REGISTERED) {
            revert NotRegistered(id);
        }
        status[id] = Status.ACCEPTED;

        (bool ok, bytes memory ret) =
                            BRIDGE.call(abi.encodeWithSignature("requestUnionBridgeRbtc(uint256)", amount));
        if (!ok) {
            revert PrecompileFailed();
        }
        if (ret.length < 32) {
            revert InvalidReturnData();
        }
        int256 code = abi.decode(ret, (int256));
        if (code != 0) {
            revert BridgeError(code);
        }
    }

    function acceptUnionPrecheck(bytes32 id, uint256 amount) external {
        (bool preOk, ) = BRIDGE.call(abi.encodeWithSignature("getBtcBlockchainBestChainHeight()"));
        if (!preOk) {
            revert PrecompileFailed();
        }

        if (status[id] != Status.REGISTERED) {
            revert NotRegistered(id);
        }
        status[id] = Status.ACCEPTED;

        (bool ok, bytes memory ret) =
                            BRIDGE.call(abi.encodeWithSignature("requestUnionBridgeRbtc(uint256)", amount));
        if (!ok) {
            revert PrecompileFailed();
        }
        if (ret.length < 32) {
            revert InvalidReturnData();
        }
        int256 code = abi.decode(ret, (int256));
        if (code != 0) {
            revert BridgeError(code);
        }
    }

    function reset(bytes32 id) external {
        status[id] = Status.NONE;
    }
}