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
// 2) Writes (sequential-ish transient writes) - TstoreLoopUntilOutOfGas
//    Selector: runTstoreUntilOutOfGas() -> 0x7b17abde
// ----------------------------
contract TstoreLoopUntilOutOfGas {
    function runTstoreUntilOutOfGas() external {
        assembly {
            // Run tstore in a loop until out of gas
            for { let i := 0 } lt(i, 1000000) { i := add(i, 1) } {
                // Use remaining gas as both key and value, to change each iteration
                tstore(gas(), gas())
            }
        }
    }
}

// ----------------------------
// 3) Reads + Writes (transient) - TstoreAndTloadLoopUntilOutOfGas
//    Selector: runTstoreAndTloadUntilOutOfGas() -> 0xae1978d7
// ----------------------------
contract TstoreAndTloadLoopUntilOutOfGas {
    function runTstoreAndTloadUntilOutOfGas() external {
        assembly {
            for { let i := 0 } lt(i, 1000000) { i := add(i, 1) } {
                let gasValue := gas()
                tstore(gasValue, gas())
                // load again to stress reads; ignore value
                let _ := tload(gasValue)
            }
        }
    }
}

// ----------------------------
// 4) Writes (wide/random-like transient storage) - TstoreWideAddressSpaceLoopUntilOutOfGas
//    Selector: runTstoreWideAddressSpaceUntilOutOfGas() -> 0x09fdcd3f
//    Idea: Generate non-local keys using code size + gas as entropy to reduce locality.
// ----------------------------
contract TstoreWideAddressSpaceLoopUntilOutOfGas {
    function runTstoreWideAddressSpaceUntilOutOfGas() external {
        assembly {
            for { let i := 0 } lt(i, 1000000) { i := add(i, 1) } {
                let pcValue := codesize()
                let shiftedPc := shl(pcValue, 1)
                let key := add(shiftedPc, gas())
                tstore(key, gas())
            }
        }
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


