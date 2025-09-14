// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import {PushupChallenge} from "../contracts/PushupChallenge.sol";

contract DeployPushupChallenge is Script {
    function run() external {
        address p1 = vm.envAddress("P1_ADDRESS");
        address p2 = vm.envAddress("P2_ADDRESS");
        address charity = vm.envAddress("CHARITY_ADDRESS");

        vm.startBroadcast();
        PushupChallenge challenge = new PushupChallenge(p1, p2, charity);
        vm.stopBroadcast();

        console2.log("PushupChallenge deployed at:", address(challenge));
    }
}

