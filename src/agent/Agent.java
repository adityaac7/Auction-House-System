package agent;

import bank.BankClient;
import common.AuctionItem; // If using items

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Agent {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java Agent [Name] [AH_Host] [AH_Port]");
            return;
        }

        String name = args[0];
        String host = args[1];
        int bankPort = Integer.parseInt(args[2]);
        double initialBalance = 1000.0;


        Agent agent = new Agent(name);
        agent.run(host, bankPort);
    }

    private String name;

    public Agent(String name) {
        this.name = name;
    }

    public void run(String ahHost, int ahPort) {
        // 1. Connect to Bank
        // TODO: Create Bank Account

        // 2. Connect to Auction House
        // TODO: Connect and Listen for Item List

        // 3. User Interface Loop
        // TODO: Scanner loop for 'bid', 'balance', 'exit'
        System.out.println("Agent " + name + " started.");
    }
}