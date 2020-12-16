pragma solidity >=0.5.0;

contract ErrorHandlingTest {
    event PrecompiledSuccess(address);
    event PrecompiledFailure(address);

    function callPrec(address precAddress) public returns (bool) {
        bytes32 input;
        uint256 inputLen = 32;
        uint256 outLen = 64;

        uint256 retval;

        assembly {
            // allocate output byte array
            let res := mload(0x40)

            // call precompile (STATICCALL returns success or failure)
            retval := staticcall(gas(), precAddress, input, inputLen, res, outLen)
        }

        if(retval == 1) {
            emit PrecompiledSuccess(precAddress);

            return true;
        }

        if(retval == 0) {
            emit PrecompiledFailure(precAddress);

            return false;
        }
    }
}