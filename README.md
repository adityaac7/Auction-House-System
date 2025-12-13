# CS 351 Project 5: Distributed Auction System

## Group Members
* **Utshab Niraula**
* **Sushant Bogati**
* **Priyash Chandara**
* **Aditya Chauhan**

## Project Overview
For Project 5, our group built a distributed auction system using a strict Client-Server architecture. The system consists of three distinct nodes that communicate over the network using TCP sockets and Java Object Serialization:

1.  **Bank Server:** The central authority. It maintains user accounts, manages balances in a thread-safe manner, and handles fund transfers between Agents and Auction Houses.
2.  **Auction House:** Acts as a hybrid node. It is a client to the Bank (to verify funds) but a server to Agents (bidders). It manages items, runs 30-second countdown timers, and processes bids.
3.  **Agent:** The client program used by bidders. It connects to the Bank to establish an identity and then connects to various Auction Houses to view items and place bids.

---

## Features

### BankApplication.jar
A JavaFX GUI application for managing the Bank Server, which serves as the central authority for account management and fund transfers.

**Key Features:**
- **Server Control:** Start and stop the bank server with a configurable port (default: 9999)
- **Real-time Logging:** View all server activity in a scrollable log area with timestamps
- **Status Monitoring:** Live status display showing server state (running/stopped) and port information
- **Thread-safe Account Management:** Handles concurrent connections from multiple auction houses and agents
- **Fund Transfer Verification:** Validates and processes fund transfers between agents and auction houses
- **Automatic Cleanup:** Properly shuts down server and closes all connections on application close

---

### AuctionHouseApplication.jar
A comprehensive JavaFX GUI for managing an auction house server, allowing operators to list items, monitor bids, and track auctions in real-time.

**Key Features:**
- **Bank Integration:** Connect to the bank server with configurable host and port settings
- **Server Management:** Start auction house server on a specified port (or auto-assign port 0)
- **Item Management:**
  - Add new auction items with description and minimum bid price
  - Remove items that don't have active bids
  - Auto-refreshing item table (updates every 2 seconds)
- **Real-time Auction Monitoring:**
  - Live countdown timers for each item (updates every second)
  - Current bid and bidder information display
  - Color-coded timer warnings (green/yellow/red based on time remaining)
- **Activity Logging:**
  - Timestamped activity log showing all bid events
  - Visual indicators for bid acceptance, rejection, outbidding, and item sales
  - Separate server log for connection and system events
- **Auction Intelligence:**
  - 30-second countdown timer resets with each new valid bid
  - Automatic removal of sold items from the listing
  - Prevents closure while active bids are pending (with force-close option)
- **Auto-deregistration:** Automatically deregisters from the bank on application shutdown

---

### AgentApplication.jar
A user-friendly JavaFX GUI application for manual bidding, allowing users to browse auction houses, view items, and place bids interactively.

**Key Features:**
- **Bank Connection:** Connect to the bank server with custom host, port, agent name, and initial balance
- **Command-line Parameters:** Optional command-line arguments for pre-filling login form:
  - `-n, --name <name>`: Agent name
  - `-b, --balance <amount>`: Initial balance
  - `-bh, --bank-host <host>`: Bank hostname
  - `-bp, --bank-port <port>`: Bank port
  - `-h, --help`: Show usage information
- **Auction House Browsing:**
  - Dropdown menu to select from available auction houses
  - Manual refresh button to update auction house list
  - Automatic connection management to selected auction house
- **Item Viewing:**
  - Real-time item table showing Item ID, Description, Minimum Bid, and Current Bid
  - Auto-refresh after bid status changes
  - Manual refresh button for immediate updates
- **Bid Management:**
  - Place bids by selecting an item and entering bid amount
  - Validation for bid amounts (must exceed current bid and minimum bid)
  - Funds validation (prevents bidding more than available balance)
  - Automatic item refresh after placing bids
- **Financial Tracking:**
  - Real-time balance display: Total Balance, Available Funds, and Blocked Funds
  - Color-coded fund indicators (green for available, red for blocked)
  - Automatic balance updates after bid outcomes
- **Purchase History:**
  - Dedicated table showing all completed purchases
  - Displays Auction House ID, Item ID, Description, and Final Price
  - Auto-updates when items are won
- **Real-time Notifications:**
  - Live status updates for bid acceptance, rejection, outbidding, and wins
  - Color-coded status messages (green for wins, red for rejections/outbids)
  - Activity log with timestamps for all events
  - Automatic item removal when won or sold to another agent
- **Connection Management:**
  - Automatic reconnection if connection to auction house is lost
  - Background listener threads for receiving outbid/winner notifications
  - Prevents application closure if there are unresolved bids (blocked funds)

---

### AutomatedAgent.jar
A command-line automated bidding bot that uses intelligent strategies to participate in auctions with minimal human intervention.

**Key Features:**
- **Sniping Strategy:**
  - Waits until the last 5 seconds of an auction before placing bids
  - Minimizes opportunities for counter-bids from other agents
  - Only bids on items that already have competition (no first bids)
- **Budget Management:**
  - Configurable maximum budget per item (default: 30% of total balance)
  - Prevents over-commitment by tracking active bids
  - Automatically calculates available funds accounting for blocked bids
- **Intelligent Bidding:**
  - Configurable bid multiplier (default: 1.08 = 8% above current bid)
  - Small randomization (±2%) to avoid predictable patterns
  - Minimum 1% increment to ensure competitiveness
  - Skips items where maximum bid would exceed budget limit
- **Watchlist System:**
  - Tracks interesting items before snipe window opens
  - Automatic cleanup of stale items (sold or expired)
  - Efficient memory management
- **Multi-Auction House Support:**
  - Automatically discovers and connects to all available auction houses
  - Processes items from all houses in rotation
  - Handles connection failures gracefully
- **Configurable Parameters:**
  - Command-line arguments: `[bankHost] [bankPort] [balance] [bidMultiplier] [maxBudgetRatio]`
  - Sensible defaults for quick start: `localhost`, port `9999`, balance `10000`, multiplier `1.08`, budget ratio `0.3`
  - Runtime configuration display on startup
- **Robust Error Handling:**
  - Continues running despite individual item processing failures
  - Automatic connection recovery
  - Clean shutdown on interruption (Ctrl+C)

**Example Usage:**
```bash
# Default settings
java -jar AutomatedAgent.jar

# Custom configuration
java -jar AutomatedAgent.jar localhost 9999 5000 1.15 0.25
```

---

## Division of Labor
We divided the project implementation by component:

* **Utshab Niraula (Bank Component):** Worked on the entire Bank backend. Responsible for implementing the core Bank logic, the thread-safe `BankAccount` class using synchronized methods, and the `BankClientHandler` to manage incoming network connections. Also defined the protocol in `BankMessages`.
* **Sushant Bogati (Agent Component):** Worked on the Agent logic. Built the client-side backend (`Agent.java`) that maintains local state and handles asynchronous notifications (like "OUTBID" or "WINNER") using listener threads. Also implemented the `AutomatedAgent` which uses specific strategies like budget management and sniping.
* **Priyash Chandara (Auction House Component):** Worked on the Auction House logic. Responsible for the `AuctionHouseServer` and `AuctionItemManager`. Implemented the logic to auto-detect the machine's public IP address to solve lab network connectivity issues and handled the 30-second countdown timers for items.
* **Aditya Chauhan (GUI & Frontend):** Worked on the Visual Interface. Responsible for developing the JavaFX applications (`BankApplication`, `AuctionHouseApplication`, `AgentApplication`) that wrap the backend logic, providing a user-friendly interface for monitoring logs, managing items, and bidding.

---

## Architecture & Design
The system handles complex concurrency issues inherent in distributed systems:

* **Communication:** We use `ObjectInputStream` and `ObjectOutputStream` to send serialized `Message` objects (defined in the messages package) rather than parsing raw text strings.
* **Thread Safety:**
    * **Bank:** Uses synchronized methods in `BankAccount.java` to prevent race conditions (e.g., ensuring a user cannot double-spend funds across two auction houses).
    * **Auction House:** Uses `ConcurrentHashMap` and synchronized blocks in `AuctionItemManager` to ensure bids are processed sequentially.
* **Timers:** The Auction House uses a `ScheduledExecutorService` to manage bid timers. Every time a valid bid is placed, the timer resets to 30 seconds.
* **Network:** We implemented a `NetworkServer` wrapper that dynamically finds the machine's LAN IP address so the Auction House registers correctly with the Bank even on the university Linux machines.

---

## Directory Structure
The project is organized into specific packages to separate concerns:

```text
project-5-auctions-project-5-group01/
├── README.md                    # This file
├── BankApplication.jar          # Executable JAR for Bank Server
├── AuctionHouseApplication.jar  # Executable JAR for Auction House
├── AgentApplication.jar         # Executable JAR for Agent (GUI)
├── AutomatedAgent.jar           # Executable JAR for Automated Agent
├── src/                         # Source code
│   ├── bank/                    # Bank Server Logic & GUI
│   │   ├── Bank.java
│   │   ├── BankServer.java
│   │   ├── BankClientHandler.java
│   │   ├── BankAccount.java
│   │   └── BankApplication.java
│   ├── auctionhouse/            # Auction House Logic & GUI
│   │   ├── AuctionHouse.java
│   │   ├── AuctionHouseServer.java
│   │   ├── AuctionItemManager.java
│   │   ├── AuctionHouseClientHandler.java
│   │   └── AuctionHouseApplication.java
│   ├── agent/                   # Agent Logic, Bot & GUI
│   │   ├── Agent.java
│   │   ├── AutomatedAgent.java
│   │   └── AgentApplication.java
│   ├── common/                  # Shared Network Wrappers & Data Objects
│   │   ├── NetworkClient.java
│   │   ├── NetworkServer.java
│   │   ├── AuctionItem.java
│   │   └── AuctionHouseInfo.java
│   └── messages/                # Protocol Definitions
│       ├── Message.java
│       ├── BankMessages.java
│       └── AuctionMessages.java
├── JavaFX/                      # JavaFX libraries (for GUI applications)
└── docs/                        # Documentation


```

## How to Run

The system must be started in a specific order. You can run the applications using the pre-built JAR files in the root directory.

### Prerequisites
- Java 17 or higher
- JavaFX runtime (included in the `JavaFX/` folder or available in your Java installation)

### Running the System

**1. Start the Bank Server**

```bash
java -jar BankApplication.jar
```

* Click "Start Server" (defaults to port 9999).
* Note the IP address of the machine running the bank.

**2. Start an Auction House**

```bash
java -jar AuctionHouseApplication.jar
```

* Enter the Bank's Hostname and Port.
* Click "Start Server". The Auction House will automatically register itself with the Bank and begin listening for Agents.

**3. Start an Agent (Manual/GUI)**

```bash
java -jar AgentApplication.jar
```

* Enter the Bank's Host/Port.
* Select an Agent Name and Initial Balance.
* Once connected, select an Auction House from the dropdown to load items and place bids.

**4. Start an Agent (Automated Bot)**

You can run the automated bot from the command line to simulate traffic. It creates a bot that bids above the current price using configurable strategies.

**Basic usage:**
```bash
java -jar AutomatedAgent.jar
```

**With custom parameters:**
```bash
java -jar AutomatedAgent.jar [bankHost] [bankPort] [balance] [bidMultiplier] [maxBudgetRatio]
```

**Example:**
```bash
java -jar AutomatedAgent.jar localhost 9999 10000 1.15 0.3
```

**Parameter defaults:**
- `bankHost`: localhost
- `bankPort`: 9999
- `balance`: 10000.0
- `bidMultiplier`: 1.08 (bids 8% above current price)
- `maxBudgetRatio`: 0.3 (max 30% of balance per item)

### JavaFX Troubleshooting (Linux/Mac Users)

If you encounter the error "JavaFX runtime components are missing", you need to specify the JavaFX module path when running the GUI applications:

```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar BankApplication.jar
```

Alternatively, install a JDK that includes JavaFX (such as Liberica Full JDK or Azul Zulu FX).

### Network Setup for Multiple Machines

To run the system across multiple machines on a network:

**1. On the Bank Server Machine:**
```bash
java -jar BankApplication.jar
```
- Click "Start Server" and note the IP address of this machine (e.g., `192.168.1.100`)

**2. On the Auction House Machine(s):**
```bash
java -jar AuctionHouseApplication.jar
```
- Enter the Bank Host: `192.168.1.100` (or the actual bank server IP)
- Enter the Bank Port: `9999`
- Click "Start Server"

**3. On Agent Machine(s):**
```bash
java -jar AgentApplication.jar
# or
java -jar AutomatedAgent.jar 192.168.1.100 9999
```
- Enter the Bank Host: `192.168.1.100` (bank server IP)

**Important:** Ensure firewall settings allow connections on:
- Bank port (default: 9999)
- Auction house ports (dynamically assigned, or set to a specific port in the GUI)

### Alternative: Running from Source

If you need to run from source code instead of JAR files:

1. Compile the project using your IDE or `javac`
2. Run the main classes:
   - `bank.BankApplication`
   - `auctionhouse.AuctionHouseApplication`
   - `agent.AgentApplication`
   - `agent.AutomatedAgent`

