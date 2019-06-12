pragma solidity ^0.5.9;
import "./Creator.sol";

contract CreateCreator {
    constructor() public{
        bytes memory code = type(Creator).creationCode;
        uint256 salt = 0;
        address addr;
        assembly { addr := create2(0, add(code,0x20), mload(code), salt)}
        Creator c = Creator(addr);
        c.run();
    }   
}