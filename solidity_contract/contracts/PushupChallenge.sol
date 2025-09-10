// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title PushupChallenge – 30-day all-or-nothing self-challenge with ETH stakes
/// @notice Two participant wallets each deposit ETH, start a 30-day challenge,
///         and must check in once per day. If all 30 days are completed, both
///         can withdraw their stakes. If any day is missed, the entire pot is
///         slashed to the charity address.
///
/// @dev How users interact (from the UI):
/// - State-changing functions (external) are called via a wallet transaction
///   from the React app (Wagmi/Viem + Coinbase Wallet): deposit, start,
///   checkIn, finalize, withdraw, slash.
/// - Read-only functions (public view) are fetched off-chain via RPC with no
///   gas cost: currentDay and the public getters (p1, p2, stakes, status, etc.).
///
/// Gas model & where it’s indicated:
/// - Any state-changing call ("nonpayable" or "payable") is a transaction and costs gas.
/// - Read-only calls ("view" or "pure") are free off-chain (eth_call), but if invoked
///   inside a transaction they still consume gas as part of that transaction.
/// - Solidity source shows this via function state mutability; the ABI mirrors it in
///   `stateMutability` so frontends know which calls require a transaction.
/// - In this contract:
///   • Write (cost gas): deposit, start, checkIn, finalize, withdraw, slash.
///   • Read (free off-chain): currentDay and public getters (p1, p2, slashRecipient,
///     stakeP1, stakeP2, status, startTime, checkins).


contract PushupChallenge {
    enum Status {
        NotStarted, // 0
        Active,     // 1
        Success,    // 2
        Failed      // 3
    }

    // Participants and slash recipient (charity)
    address public immutable p1;
    address public immutable p2;
    address public immutable slashRecipient;

    // total ETH each participant has deposited.
    uint256 public stakeP1;
    uint256 public stakeP2;
    mapping(address => bool) public withdrawn;

    // Challenge timeline/state
    uint256 public startTime;
    uint8 public constant TOTAL_DAYS = 30;
    uint256 public checkins; // bit i set => day i completed
    Status public status = Status.NotStarted;
    bool public slashed;

    // Basic reentrancy guard
    uint256 private _entered;

    event Deposited(address indexed who, uint256 amount);
    event Started(uint256 startTime);
    event CheckedIn(uint8 day);
    event Finalized(Status result);
    event Withdrawn(address indexed who, uint256 amount);
    event Slashed(uint256 amount);

    // These are custome errors; Something similar to custom execptions
    error NotParticipant(); // caller is neither p1 nor p2.
    error BadStatus(); // function called in the wrong status.
    error AlreadyChecked(); // day already checked in.
    error AlreadyWithdrawn();
    error AlreadySlashed();
    error ZeroValue(); // deposit value was zero.

    constructor(address _p1, address _p2, address _slashRecipient) {
        require(_p1 != address(0) && _p2 != address(0) && _slashRecipient != address(0), "zero address");
        p1 = _p1;
        p2 = _p2;
        slashRecipient = _slashRecipient;
    }

    /*
     * Modifiers in Solidity are reusable pre/post checks you attach to
     * functions. Their code runs, then the function body executes at `_;`.
     * If a modifier reverts, the function body never runs.
     */
    modifier onlyParticipant() {
        // Access control: only p1 or p2 may call guarded functions
        if (msg.sender != p1 && msg.sender != p2) revert NotParticipant();
        _;
    }

    // Simple mutex to prevent reentrancy during ETH transfers
    modifier nonReentrant() {
        require(_entered == 0, "reentrant");
        _entered = 1;
        _;
        _entered = 0;
    }

    receive() external payable {
        revert("use deposit");
    }

    /// @notice Deposit ETH before the challenge starts (participants only)
    /// @dev external: intended to be called from outside via a wallet tx
    function deposit() external payable onlyParticipant {
        if (status != Status.NotStarted) revert BadStatus();
        if (msg.value == 0) revert ZeroValue();
        if (msg.sender == p1) {
            stakeP1 += msg.value;
        } else {
            stakeP2 += msg.value;
        }
        emit Deposited(msg.sender, msg.value);
    }

    /// @notice Start the 30-day challenge when both stakes are present
    /// @dev external: called by a participant’s wallet once both have deposited
    function start() external onlyParticipant {
        if (status != Status.NotStarted) revert BadStatus();
        require(stakeP1 > 0 && stakeP2 > 0, "both must deposit");
        startTime = block.timestamp;
        status = Status.Active;
        emit Started(startTime);
    }

    /// @notice Return the current day index since start (0..29)
    /// @dev public view: read-only; free to query off-chain via RPC
    function currentDay() public view returns (uint8 day) {
        require(status == Status.Active, "not active");
        uint256 elapsed = block.timestamp - startTime;
        day = uint8(elapsed / 1 days);
    }

    /// @notice Daily check-in, once per day across both participants
    /// @dev external: called by either participant’s wallet during the day window
    function checkIn() external onlyParticipant {
        if (status != Status.Active) revert BadStatus();
        uint8 day = currentDay();
        require(day < TOTAL_DAYS, "challenge complete");
        uint256 mask = (uint256(1) << day);
        if ((checkins & mask) != 0) revert AlreadyChecked();
        checkins |= mask;
        emit CheckedIn(day);
    }

    /// @notice Finalize: success after day 30 if all days checked; early fail if a day was missed
    /// @dev external/public: callable by anyone to resolve state; UI calls this when appropriate
    function finalize() public {
        if (status != Status.Active) revert BadStatus();
        uint8 day = currentDay();

        if (day >= TOTAL_DAYS) {
            // After 30 days, success only if all bits are set
            if (checkins == ((uint256(1) << TOTAL_DAYS) - 1)) {
                status = Status.Success;
            } else {
                status = Status.Failed;
            }
            emit Finalized(status);
            return;
        }

        // Early fail detection: count contiguous completed days from 0 upward
        uint8 contiguous = 0;
        while (contiguous < TOTAL_DAYS && ((checkins >> contiguous) & 1) == 1) {
            unchecked {
                contiguous++;
            }
        }
        // If time moved into day 'day' but contiguous completed < day, a day was missed
        if (day > contiguous) {
            status = Status.Failed;
            emit Finalized(status);
        } else {
            revert("challenge ongoing");
        }
    }

    /// @notice Withdraw your stake after a successful challenge
    /// @dev external + nonReentrant: participant’s wallet pulls their stake after success
    function withdraw() external onlyParticipant nonReentrant {
        require(status == Status.Success, "not success");
        if (withdrawn[msg.sender]) revert AlreadyWithdrawn();
        withdrawn[msg.sender] = true;
        uint256 amt = msg.sender == p1 ? stakeP1 : stakeP2;
        (bool ok, ) = msg.sender.call{value: amt}("");
        require(ok, "transfer failed");
        emit Withdrawn(msg.sender, amt);
    }

    /// @notice Slash all funds to the charity after failure
    /// @dev external + nonReentrant: anyone may trigger slash once Failed, sending all ETH to charity
    function slash() external nonReentrant {
        if (status == Status.Active) {
            // Allow anyone to finalize to failed if a day was missed
            // This will revert if still ongoing and no miss yet
            finalize();
        }
        require(status == Status.Failed, "not failed");
        if (slashed) revert AlreadySlashed();
        slashed = true;
        uint256 amt = address(this).balance;
        (bool ok, ) = slashRecipient.call{value: amt}("");
        require(ok, "transfer failed");
        emit Slashed(amt);
    }
}
