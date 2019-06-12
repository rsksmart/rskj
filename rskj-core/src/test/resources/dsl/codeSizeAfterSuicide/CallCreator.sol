pragma solidity ^0.5.9;
import "./Creator.sol";

contract CallCreator {
    constructor() public{
        Creator child = Creator(0xF4e52B74aa8C285cdC89Acc9a3c8eeC5d9707ac6);
        child.run();
    }   
}