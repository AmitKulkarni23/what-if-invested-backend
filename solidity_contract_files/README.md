# PushupChallenge – Foundry Deployment

This folder contains a minimal Foundry setup to deploy the `PushupChallenge` contract to Base Sepolia.

## Prerequisites

- Foundry installed (macOS):
  - `curl -L https://foundry.paradigm.xyz | bash`
  - `source ~/.zshrc` (or `~/.bashrc`)
  - `foundryup`
- A wallet with Base Sepolia test ETH (for the deployer).
- Three addresses:
  - `P1_ADDRESS`, `P2_ADDRESS` – your participant wallets
  - `CHARITY_ADDRESS` – where funds go if the challenge fails (can be a burn address)

## Configure

1) Copy `.env.example` to `.env` and fill in values:

```
RPC_URL=https://sepolia.base.org
PRIVATE_KEY=0xYOUR_DEPLOYER_PRIVATE_KEY
P1_ADDRESS=0x...
P2_ADDRESS=0x...
CHARITY_ADDRESS=0x...
# Optional for verification
# BASESCAN_API_KEY=...
```

2) Review `foundry.toml` (already set to use `contracts` as src).

## Build

```
forge build
```

## Deploy (Base Sepolia)

```
source .env
forge script script/DeployPushupChallenge.s.sol:DeployPushupChallenge \
  --rpc-url "$RPC_URL" \
  --private-key "$PRIVATE_KEY" \
  --broadcast
```

- The script prints the deployed address.
- If you prefer a Ledger/Trezor or a configured keystore, see `forge script --help` for alternative signers.

## Verify (optional)

If you have a Basescan API key, uncomment the section in `foundry.toml` and export `BASESCAN_API_KEY`, then:

```
forge verify-contract \
  --chain basesepolia \
  --watch \
  <DEPLOYED_ADDRESS> \
  contracts/PushupChallenge.sol:PushupChallenge \
  --constructor-args $(cast abi-encode "constructor(address,address,address)" $P1_ADDRESS $P2_ADDRESS $CHARITY_ADDRESS)
```

## Contract

- Source: `contracts/PushupChallenge.sol`
- Constructor args (order matters): `_p1`, `_p2`, `_slashRecipient`

## Wire into Frontend

Update `what-if-invested-frontend/.env`:

```
REACT_APP_CHALLENGE_ADDRESS=0xDeployedAddress
REACT_APP_P1=0x...
REACT_APP_P2=0x...
REACT_APP_CHARITY=0x...
```

Then restart the frontend and open `/challenge`.

