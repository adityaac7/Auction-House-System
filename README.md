# Distributed Auction System

A fully-featured distributed auction system built with Java, implementing a strict Client-Server architecture with three distinct nodes communicating over TCP sockets. The system features comprehensive JavaFX GUI applications for all components, real-time auction monitoring, automated bidding agents, and robust thread-safe concurrent operations.

---

## My Contributions

**Aditya Chauhan** - *GUI & Frontend Development, Documentation, Bug Fixes*

I handled all three JavaFX GUI applications for this system:

### BankApplication - Bank Server Control Panel
- Built the JavaFX interface for managing the bank server
- Added real-time logging with timestamps
- Implemented server start/stop controls with proper cleanup
- Added status monitoring to show server state and port info
- Wrote Javadoc documentation

### AuctionHouseApplication - Auction House Management Interface
- Created the GUI for auction house operators
- Built countdown timers that update in real-time with color warnings (green when there's time left, red when time is running out)
- Made item tables auto-refresh every 2 seconds
- Added activity logging that shows bid events with visual indicators
- Built item management with validation (can't remove items with active bids)
- Handles auto-deregistration when the app closes

### AgentApplication - Interactive Bidding Client
- Built the bidding interface with real-time updates
- Added command-line parameter support to make testing easier
- Shows balance info in real-time (total, available, and blocked funds)
- Purchase history table that updates automatically when you win items
- Color-coded notifications (green for wins, red for rejections/outbids)
- Handles connection drops and automatically reconnects
- Uses background threads to listen for outbid/winner notifications

### Additional Contributions
- Fixed several bugs (balance unblocking issues, network problems, bid workflow)
- Added Javadoc comments across all the GUI code
- Cleaned up the project structure and removed duplicate files
- Helped resolve merge conflicts and integrate everyone's work

---

## Project Overview
For Project 5, our group built a distributed auction system using a strict Client-Server architecture. The system consists of three distinct nodes that communicate over the network using TCP sockets and Java Object Serialization:

1.  **Bank Server:** The central authority. It maintains user accounts, manages balances in a thread-safe manner, and handles fund transfers between Agents and Auction Houses.
2.  **Auction House:** Acts as a hybrid node. It is a client to the Bank (to verify funds) but a server to Agents (bidders). It manages items, runs 30-second countdown timers, and processes bids.
3.  **Agent:** The client program used by bidders. It connects to the Bank to establish an identity and then connects to various Auction Houses to view items and place bids.

---

## Features

### BankApplication.jar
A JavaFX GUI application for managing the Bank Server, which serves as the central authority for account management and fund transfers.

Features:
- Start and stop the bank server (configurable port, defaults to 9999)
- Scrollable log area showing all server activity with timestamps
- Status display showing if the server is running and what port it's on
- Handles multiple concurrent connections from auction houses and agents
- Validates and processes fund transfers
- Cleanly shuts down and closes connections when you close the app

---

### AuctionHouseApplication.jar
A comprehensive JavaFX GUI for managing an auction house server, allowing operators to list items, monitor bids, and track auctions in real-time.

Features:
- Connect to the bank server (configurable host and port)
- Start the auction house server on a specific port (or let it auto-assign)
- Add and remove auction items (can only remove items without active bids)
- Item table auto-refreshes every 2 seconds
- Countdown timers for each item that update every second (green when there's time, red when running out)
- Shows current bid and who's bidding
- Activity log with timestamps for all bid events
- Separate server log for connections and system events
- 30-second timer resets each time someone places a bid
- Sold items are automatically removed from the listing
- Won't let you close while there are active bids (unless you force close)
- Auto-deregisters from the bank when you close the app

---

### AgentApplication.jar
A user-friendly JavaFX GUI application for manual bidding, allowing users to browse auction houses, view items, and place bids interactively.

Features:
- Connect to the bank with host, port, agent name, and starting balance
- Command-line arguments to pre-fill the login form: `-n/--name`, `-b/--balance`, `-bh/--bank-host`, `-bp/--bank-port`, `-h/--help`
- Dropdown to select from available auction houses (with refresh button)
- Item table shows Item ID, Description, Minimum Bid, and Current Bid
- Auto-refreshes after you place a bid or when bid status changes
- Bid validation (must beat current bid and minimum, can't bid more than available funds)
- Shows your balance in real-time: Total, Available, and Blocked funds (color-coded)
- Purchase history table that updates when you win items
- Status notifications with colors (green for wins, red for rejections/outbids)
- Activity log with timestamps
- Won items are automatically removed from the listing
- Reconnects automatically if connection drops
- Won't let you close if you have unresolved bids (blocked funds)

---

### AutomatedAgent.jar
A command-line automated bidding bot that uses intelligent strategies to participate in auctions with minimal human intervention.

Features:
- Waits until the last 5 seconds to bid (sniping strategy)
- Only bids on items that already have competition (skips items with no bids)
- Default budget is 30% of total balance per item (configurable)
- Tracks active bids to avoid over-committing
- Bids 8% above current bid by default (configurable multiplier with ±2% randomization)
- Watches interesting items before the snipe window opens
- Connects to all available auction houses automatically
- Processes items from all houses in rotation
- Keeps running even if individual items fail
- Clean shutdown with Ctrl+C

**Example Usage:**
```bash
# Default settings
java -jar AutomatedAgent.jar

# Custom configuration
java -jar AutomatedAgent.jar localhost 9999 5000 1.15 0.25
```

---

## Tech Stack

- Java 17+
- JavaFX for the GUI
- TCP Sockets with Java Object Serialization for networking
- Used `ScheduledExecutorService`, `ConcurrentHashMap`, and synchronized methods for thread safety
- Client-Server architecture
- Manually compiled to JAR files (included in the repo)

---

## Key Features

The system includes three full GUI applications built with JavaFX. Everything updates in real-time - countdown timers, item tables, balance displays, and notifications. The system handles multiple concurrent connections safely using synchronized methods and concurrent collections. I focused on making the interfaces intuitive with color coding and clear feedback. The agent application also supports command-line parameters for easier testing.

---

## Team & Division of Labor

This was a group project with four team members. We split the work by component:

* **Utshab Niraula (Bank Component):** Implemented the entire Bank backend. Responsible for the core Bank logic, the thread-safe `BankAccount` class using synchronized methods, and the `BankClientHandler` to manage incoming network connections. Also defined the protocol in `BankMessages`.
* **Sushant Bogati (Agent Component):** Developed the Agent logic. Built the client-side backend (`Agent.java`) that maintains local state and handles asynchronous notifications (like "OUTBID" or "WINNER") using listener threads. Also implemented the `AutomatedAgent` with intelligent bidding strategies like budget management and sniping.
* **Priyash Chandara (Auction House Component):** Worked on the Auction House logic. Responsible for the `AuctionHouseServer` and `AuctionItemManager`. Implemented the logic to auto-detect the machine's public IP address to solve lab network connectivity issues and handled the 30-second countdown timers for items.
* **Aditya Chauhan (GUI & Frontend):** Designed and implemented all three JavaFX applications (`BankApplication`, `AuctionHouseApplication`, `AgentApplication`) that provide user-friendly interfaces for monitoring logs, managing items, and bidding. Also contributed to bug fixes, documentation, and project organization.

---

## Architecture & Design
The system handles complex concurrency issues inherent in distributed systems:

* **Communication:** Uses `ObjectInputStream`/`ObjectOutputStream` to send serialized `Message` objects instead of parsing text strings
* **Thread Safety:** 
    * Bank uses synchronized methods in `BankAccount.java` to prevent race conditions (can't double-spend across auction houses)
    * Auction House uses `ConcurrentHashMap` and synchronized blocks in `AuctionItemManager` to process bids sequentially
* **Timers:** Uses `ScheduledExecutorService` for bid timers. Timer resets to 30 seconds each time someone places a valid bid
* **Network:** `NetworkServer` wrapper finds the machine's LAN IP address automatically so auction houses register correctly with the bank

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
│   │   ├── BankClient.java
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

---

## System Architecture

### Multi-Component Architecture

You can run multiple instances of each component:

- **Multiple Banks:** Can run on different machines with different ports/IPs
- **Multiple Auction Houses:** Can all connect to the same bank, but each needs to run on a separate machine (they bind to the machine's IP). Agents can switch between auction houses using a dropdown. The automated agent finds and connects to all available auction houses automatically
- **Multiple Agents:** Any number of agents can connect to the same bank and bid across different auction houses at the same time

### Bidding & Fund Management

When an agent places a bid, that amount gets blocked in their account. The funds stay blocked until:
- The agent gets outbid (funds are automatically unblocked), or
- The auction ends - if they win, the money goes to the auction house; if they lose, it goes back to their available balance

This prevents agents from bidding more money than they actually have across multiple auctions. The GUI shows total balance, available funds, and blocked funds in real-time so agents always know what they can spend.

---

## Screenshots & Demo

*(Screenshots will be added here)*

Planning to add screenshots of each GUI application and a demo showing how the components interact - multiple agents bidding on the same items with real-time updates across all windows.

---

## About This Project

This was a group project for CS 351 - Design of Large Programs at the University of New Mexico. I'm sharing it here to highlight my work on the GUI components.

This is a group project - see the "Team & Division of Labor" section above for what each team member worked on.

**Course:** CS 351 - Design of Large Programs (University of New Mexico)  
**Original Repository:** [UNM-CS351/project-5-auctions-project-5-group01](https://github.com/UNM-CS351/project-5-auctions-project-5-group01)

