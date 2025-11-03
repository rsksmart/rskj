// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// NOTE: This file is for reference only. The txload service deploys bytecode directly.
// These contracts document the semantics of the bytecode constants embedded in
// TxLoadGeneratorService and help reproduce or modify behavior if needed.

// ----------------------------
// 1) CPU Stress - GasBurner
//    Selector: burnGas(uint256) -> 0xb9554c59
// ----------------------------
contract GasBurner {
    function burnGas(uint256 lowerLimit) public returns (bool) {
        uint256 counter = 0;
        while (gasleft() > lowerLimit) {
            // Simple tight loop; optionally mix in a cheap operation
            counter++;
        }
        // Prevent optimizer from removing counter
        return counter >= 0;
    }
}

// ----------------------------
// 5) Random Writes (contract storage) - RandomWrites
//    Selectors:
//      - runRandomWrites(uint256,uint256)
//      - runRandomWritesUntilOutOfGas(uint256,uint256)
//    Uses xorshift-like PRNG for 256-bit state to create dispersed storage keys.
// ----------------------------
contract RandomWrites {
    function runRandomWrites(uint256 iterations, uint256 seed) external {
        assembly {
            let x := seed
            for { let i := 0 } lt(i, iterations) { i := add(i, 1) } {
                // xorshift (simple 256-bit variant)
                x := xor(x, shl(13, x))
                x := xor(x, shr(7,  x))
                x := xor(x, shl(17, x))
                sstore(x, gas())
            }
        }
    }

    function runRandomWritesUntilOutOfGas(uint256 seed, uint256 minGasLeft) external {
        assembly {
            let x := seed
            for { } gt(gas(), minGasLeft) { } {
                x := xor(x, shl(13, x))
                x := xor(x, shr(7,  x))
                x := xor(x, shl(17, x))
                sstore(x, gas())
            }
        }
    }
}

// ----------------------------
// 6) Random Writes with keccak-derived keys (heavier CPU) - KeccakRandomWrites
// ----------------------------
contract KeccakRandomWrites {
    function runKeccakRandomWrites(uint256 iterations, uint256 seed) external {
        for (uint256 i = 0; i < iterations; ++i) {
            bytes32 slot = keccak256(abi.encodePacked(seed, i, gasleft(), block.number));
            assembly { sstore(slot, gas()) }
        }
    }
}

// ----------------------------
// 7) Random Reads (contract storage) - RandomReads
//    Selector: runRandomReadsUntilOutOfGas(uint256 seed, uint256 minGasLeft)
// ----------------------------
contract RandomReads {
    function runRandomReadsUntilOutOfGas(uint256 seed, uint256 minGasLeft) external view returns (uint256 acc) {
        for (;;) {
            if (gasleft() <= minGasLeft) break;
            bytes32 slot = keccak256(abi.encodePacked(seed, acc, gasleft(), block.number));
            uint256 v;
            assembly { v := sload(slot) }
            acc ^= v;
        }
    }
}


