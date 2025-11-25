package agent;

import java.io.IOException;
import java.util.Random;
/**
* Automated agent that randomly bids on items*/

public class AutomatedAgent {

    private Agent agent;
    private volatile boolean running = false;
    private Random random;
    private long bidInterval;      // Milliseconds between bids
    private double bidMultiplier;// How much to increase bid (e.g., 1.1 = 10% increase)

    public AutomatedAgent(String agentName, double initialBalance, String bankHost, int bankPort, long bidInterval,
                          double bidMultiplier) throws IOException, ClassNotFoundException {
        this.agent = new Agent(agentName, initialBalance, bankHost, bankPort);
        this.random = new Random();
        this.bidInterval = bidInterval;
        this.bidMultiplier = bidMultiplier;
        System.out.println("[AUTO AGENT] Created: " + agentName);
        System.out.println("[AUTO AGENT] Bid Interval: " + bidInterval + "ms");
        System.out.println("[AUTO AGENT] Bid Multiplier: " + bidMultiplier);
    }
}