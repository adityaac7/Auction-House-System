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
CS351-Auction/
├── README.md           # This file
├── src/                
    ├── bank/           # Bank Server Logic & GUI
    │   ├── Bank.java
    │   ├── BankServer.java
    │   ├── BankClientHandler.java
    │   ├── BankAccount.java
    │   └── BankApplication.java
    ├── auctionhouse/   # Auction House Logic & GUI
    │   ├── AuctionHouse.java
    │   ├── AuctionHouseServer.java
    │   ├── AuctionItemManager.java
    │   ├── AuctionHouseClientHandler.java
    │   └── AuctionHouseApplication.java
    ├── agent/          # Agent Logic, Bot & GUI
    │   ├── Agent.java
    │   ├── AutomatedAgent.java
    │   └── AgentApplication.java
    ├── common/         # Shared Network Wrappers & Data Objects
    │   ├── NetworkClient.java
    │   ├── NetworkServer.java
    │   ├── AuctionItem.java
    │   └── AuctionHouseInfo.java
    └── messages/       # Protocol Definitions
        ├── Message.java
        ├── BankMessages.java
        └── AuctionMessages.java


```

## How to Run

The system must be started in a specific order.

1. Start the Bank

* Run bank.BankApplication.
* Click "Start Server" (defaults to port 9999).
* Note the IP address of the machine running the bank.

2. Start an Auction House

* Run auctionhouse.AuctionHouseApplication.
* Enter the Bank's Hostname and Port.
* Click "Start Server". The Auction House will automatically register itself with the Bank and begin listening for 
  Agents.

3. Start an Agent (Manual)

* Run agent.AgentApplication.
* Enter the Bank's Host/Port.
* Select an Agent Name and Initial Balance.
* Once connected, select an Auction House from the dropdown to load items and place bids.

4. Start an Agent (Automated Bot)

* You can run the automated bot from the command line to simulate traffic. It creates a bot that bids 1.15x the 
current price and stops if it runs low on funds.

# Example Command
```bash 

java -cp out/production/project agent.AutomatedAgent -n AutoBot -b 10000 -bh localhost -bp 9999

