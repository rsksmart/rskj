// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.18;

contract BFVAddTest {
    function bfvAddTest(bytes memory data) public view returns (bytes memory) {
        address addAddr = 0x0000000000000000000000000000000001000011;
        return bfvOp(addAddr, data);
    }

    function bfvSubTest(bytes memory data) public view returns (bytes memory) {
        address addAddr = 0x0000000000000000000000000000000001000012;
        return bfvOp(addAddr, data);
    }

    function bfvMulTest(bytes memory data) public view returns (bytes memory) {
        address addAddr = 0x0000000000000000000000000000000001000013;
        return bfvOp(addAddr, data);
    }

    function bfvOp(address precAddress, bytes memory data)
        public
        view
        returns (bytes memory)
    {
        //one of the default accounts of regtest
        //address addr = 0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826;
        bytes memory input = data;
        uint256 inputLen = input.length;
        uint256 outLen = 32768;

        bytes memory retval;

        assembly {
            // allocate output byte array
            let res := mload(outLen)

            // call precompile (STATICCALL returns success or failure)
            retval := staticcall(
               gas(),
                precAddress,
                input,
                inputLen,
                res,
                outLen
            )
        }

        return retval;
    }
}

