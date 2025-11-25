package agent;

import java.util.Random;
/**
* Automated agent that randomly bids on items*/

public class AutomatedAgent {

    private Agent agent;
    private volatile boolean running = false;
    private Random random;
    private long bidInterval;      // Milliseconds between bids
    private double bidMultiplier;  // How much to increase bid (e.g., 1.1 = 10% increase)
}