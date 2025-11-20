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

## Who Did What (So Far)
We just started and split up the initial work to get the project repo going:

* **Utshab Niraula:** Uploaded the initial code for the project. This included the skeleton classes for `BankServer`, `AuctionHouse`, `Agent`, and `ItemManager` so we all had a base to work off of. Currently working on the code for the Bank.
* **Sushant Bogati:** Wrote the `NetworkServer` class. This is a helper class to handle the server socket stuff so we don't have to write it twice for the Bank and Auction House. He is working on adding this to the Auction House code now.
* **Priyash Chandara:** Set up the GitHub Classroom group and repo. Also pushed the first `README.md` file to get us started. Right now, he is working on the Agent code and how the user types in commands.
* **Aditya Chauhan:** He is handling the design part. He is writing the `design/cs351project5_design.pdf` and talking with the group to figure out exactly what messages we need to send back and forth.

## How It Works
We are using Java Sockets for everything. Since multiple people are going to be connecting at the same time, we have to handle threads carefully:
* **Bank:** Uses `synchronized` blocks so two people can't touch the same account at once.
* **Auction House:** Uses locks on items so bids don't conflict.

If you want to see the diagrams and exact protocols, check the design PDF in the design folder.

## Directory Structure
Here is what our project folder looks like right now:

```text
CS351-Auction/
├── README.md           # This file
├── .gitignore          
├── design/             
│   └── cs351project5_design.pdf
└── src/                
    ├── Agent.java
    ├── AuctionHouse.java
    ├── BankServer.java
    ├── BankClient.java
    ├── ItemManager.java
    ├── NetworkServer.java
    └── [Other helper classes...]

```
## Current Status
We have the main structure set up.

Done: Added NetworkServer.java and the main class files. Design doc is in progress.

Doing: Working on connecting the NetworkServer to the Bank and Auction House and making sure the threads work properly.