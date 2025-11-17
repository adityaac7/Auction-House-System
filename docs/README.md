# CS 351 Project 5: Distributed Auction System

## Group Members
* **Utshab Niraula**
* **Sushant Bogati**
* **Priyash Chandara**
* **Aditya Chauhan**

---

##  Project Overview
This project implements a distributed system simulating an auction environment. It consists of three distinct components communicating over a TCP network:

1.  **Bank Server:** The central authority that manages accounts, funds, and the registry of active Auction Houses. It ensures thread-safe financial transactions.
2.  **Auction House:** A hybrid client/server application. It acts as a client to the Bank (to register and verify funds) and a server to Agents (hosting items and accepting bids).
3.  **Agent:** The client application for users (or automated bots) to connect to the system, view items, and place bids in real-time.

## 🏗️ Architecture & Design
The system is built on a strict Client-Server model using Java Sockets. Thread safety is a core priority, utilizing:
* **Bank:** `synchronized` Monitors for account management to prevent race conditions on funds.
* **Auction House:** `ReentrantLocks` on individual items to allow concurrent bidding without blocking the entire system.

For detailed architecture diagrams, protocol definitions, and class responsibilities, please refer to the design document:
 **`design/cs351project5_design.pdf`**

---

##  Directory Structure
```text
CS351-Auction/
├── README.md           # This file
├── .gitignore          # Git configuration
├── design/             # Documentation and Design PDFs
│   
└── src/                # Source code
    ├── Agent.java
    ├── AuctionHouse.java
    ├── BankServer.java
    ├── BankClient.java
    ├── ItemManager.java
    ├── AuctionServiceThread.java
    └── [Other helper classes...]


```
## Development Status
Initial Commit: Project structure, class skeletons, and design documentation established.

Current Focus: Implementing core thread logic and network protocols.