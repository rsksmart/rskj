pragma solidity ^0.5.9;
import "./TestChild.sol";

contract Suicider {
    constructor() public {
        TestChild child = TestChild(0xa943B74640c466Fc700AF929Cabacb1aC6CC8895);
        child.destroy();
    }
}