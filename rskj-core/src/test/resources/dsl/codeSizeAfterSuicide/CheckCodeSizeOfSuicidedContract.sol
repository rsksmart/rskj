pragma solidity ^0.5.9;

contract checkSizeOfSuicidedContract {
    constructor() public {
        address addr = address(0xa943B74640c466Fc700AF929Cabacb1aC6CC8895);
        assembly {
            if iszero(extcodesize(addr)) { revert(0, 0) }
            invalid()
        }
    }
}