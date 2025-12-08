# CS 351 Project 5: Distributed Auction System

### Group Members
* **Utshab Niraula**
* **Sushant Bogati**
* **Priyash Chandara**
* **Aditya Chauhan**

## Project Overview
For Project 5, our group is building a distributed auction system. The basic idea is to have three different programs that talk to each other over the network using TCP sockets:

1.  **Bank Server:** This runs the whole time and keeps track of everyone's money and accounts. It also handles the Auction Houses registering. We are making sure it's thread-safe so money doesn't get messed up.
2.  **Auction House:** This is kind of in the middle. It acts like a client when it talks to the Bank (to check funds), but it acts like a server for the Agents (bidders). It holds the items and runs the auctions.
3.  **Agent:** This is the program the user runs to bid on things. It connects to the Bank to get an account and then connects to an Auction House to start bidding.

## Who Is Doing What
We split the work up by component to keep things organized:

* **Utshab Niraula (Bank Backend):** I am working on the Bank. So far, I've implemented the `Bank` logic, the thread-safe `BankAccount` class, and the `BankClientHandler` to handle incoming connections. I also defined the `BankMessages` protocol so everyone knows how to talk to the bank.
* **Sushant Bogati (Agent Client):** Sushant is working on the Agent logic. He is building the client that connects to the bank, handles the user's balance, and eventually will handle the bidding loop.
* **Priyash Chandara (Auction House Server):** Priyash is handling the Auction House logic. He is working on the `ItemManager` (locking items for bids) and the `AuctionHouseServer` that listens for Agents to connect.
* **Aditya Chauhan (GUI & Frontend):** Aditya is in charge of the visual part. Once our backend logic is solid, he is going to build the JavaFX applications (`BankApplication`, `AgentApplication`, etc.) so we have a nice interface instead of just console text.

## Architecture & Design
The system is built on a strict Client-Server model using Java Sockets. Thread safety is a core priority:
* **Bank:** Uses `synchronized` methods in `BankAccount.java` to prevent race conditions when blocking or transferring funds.
* **Auction House:** Uses `ReentrantLocks` on items to ensure two people can't bid on the same item at the exact same microsecond.

## Directory Structure
We recently refactored the code into packages to keep it clean:

```text
CS351-Auction/
├── README.md           # This file
├── .gitignore          
├── design/             
│   └── cs351project5_design.pdf
└── src/                
    ├── bank/           # Bank logic & Server
    │   ├── Bank.java
    │   ├── BankServer.java
    │   └── BankClientHandler.java
    ├── agent/          # Agent logic
    │   └── Agent.java
    ├── auctionhouse/   # Auction House logic
    │   ├── AuctionHouse.java
    │   └── ItemManager.java
    ├── common/         # Shared files (NetworkServer, AuctionItem)
    └── messages/       # Protocol definitions (BankMessages, etc.)

```

## Current Status
* Done:
* Bank backend is functional (Accounts, logic, message routing).
* Network protocols are defined.
* Project structure is organized into packages.

* Doing:
* Connecting the Agent and Auction House logic to the network using the new package structure.
* Drafting the JavaFX GUI screens.