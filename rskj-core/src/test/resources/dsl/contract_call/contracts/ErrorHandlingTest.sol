pragma solidity >=0.5.0;

contract ErrorHandlingTest {
    event ErrorHandlingOk(); // when everything ends as expected
    event PrecompiledSuccess(address); // when a precompiled call it's executed properly
    event PrecompiledFailure(address); // when a precompiled call fails
    event PrecompiledUnexpected(address); // when a precompiled call ends unexpected

    address[] rskPrecompiles;
    address[] ethPrecompiles;

    constructor() public {
        rskPrecompiles = [
            // BRIDGE
            address(0x0000000000000000000000000000000001000006),
            // REMASC
            address(0x0000000000000000000000000000000001000008),
            // HD_WALLET_UTILS
            address(0x0000000000000000000000000000000001000009),
            // BLOCK_HEADER
            address(0x0000000000000000000000000000000001000010)
        ];
        ethPrecompiles = [
            // ECRECOVER
            address(0x0000000000000000000000000000000000000001),
            // SHA256
            address(0x0000000000000000000000000000000000000002),
            // RIPEMPD160
            address(0x0000000000000000000000000000000000000003),
            // IDENTITY
            address(0x0000000000000000000000000000000000000004),
            // BIG_INT_MODEXP
            address(0x0000000000000000000000000000000000000005),
            // ALT_BN_128_ADD
            address(0x0000000000000000000000000000000000000006),
            // ALT_BN_128_MUL
            address(0x0000000000000000000000000000000000000007),
            // ALT_BN_128_PAIRING
            address(0x0000000000000000000000000000000000000008)
        ];
    }

    function errorHandlingRskPrecompiles() public returns (bool) {
        return callPrecompiles(rskPrecompiles);
    }

    function errorHandlingEthPrecompiles() public returns (bool) {
        return callPrecompiles(ethPrecompiles);
    }

    function callPrecompiles(address[] memory precompiles) public returns (bool) {
        for(uint256 i = 0; i < precompiles.length; i++) {
            address precompiled = precompiles[i];
            callPrec(precompiled);
        }

        emit ErrorHandlingOk();

        return true;
    }

    function callPrec(address precAddress) public returns (bool) {
        bytes32 input = "";
        uint256 inputLen = 32;
        uint256 outLen = 64;

        uint256 retval;

        assembly {
            // allocate output byte array
            let res := mload(0x40)

            // call precompile (STATICCALL returns success or failure)
            retval := staticcall(gas(), precAddress, input, inputLen, res, outLen)
        }

        if(retval > 1) {
            emit PrecompiledUnexpected(precAddress);

            return false;
        }

        if(retval == 0) {
            emit PrecompiledFailure(precAddress);

            return false;
        }

        emit PrecompiledSuccess(precAddress);

        return true;
    }
}