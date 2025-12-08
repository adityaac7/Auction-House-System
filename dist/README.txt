================================================================================
                    DISTRIBUTED AUCTION SYSTEM
                         Running Instructions
================================================================================

PREREQUISITES:
- Java 17 or higher (JDK with JavaFX for GUI applications)
- For GUI apps (BankApplication, AuctionHouseApplication, AgentApplication):
  JavaFX must be available in your Java installation or specified via module path

--------------------------------------------------------------------------------
STARTING THE SYSTEM (Order matters!)
--------------------------------------------------------------------------------

1. START THE BANK SERVER FIRST:
   java -jar BankApplication.jar
   
   - Click "Start Server" button
   - Default port: 9999
   - Keep this running throughout the auction session

2. START ONE OR MORE AUCTION HOUSES:
   java -jar AuctionHouseApplication.jar
   
   - Enter Bank Host: localhost (or bank server IP)
   - Enter Bank Port: 9999
   - Click "Start Server"
   - Add items using the GUI

3. START AGENTS TO BID:
   
   Option A - GUI Agent:
   java -jar AgentApplication.jar
   
   - Enter your name, bank host (localhost), bank port (9999)
   - Click "Login"
   - Select auction house from dropdown
   - Select item and enter bid amount
   - Click "Place Bid"

   Option B - Automated Agent (command line):
   java -jar AutomatedAgent.jar [bankHost] [bankPort] [balance] [bidMultiplier] [maxBudgetRatio]
   
   Examples:
   java -jar AutomatedAgent.jar                           # Uses defaults
   java -jar AutomatedAgent.jar localhost 9999            # Custom host/port
   java -jar AutomatedAgent.jar localhost 9999 5000 1.1 0.25   # Full config

   Default values:
   - bankHost: localhost
   - bankPort: 9999
   - balance: 10000.0
   - bidMultiplier: 1.08 (bids 8% above current)
   - maxBudgetRatio: 0.3 (max 30% of balance per item)

--------------------------------------------------------------------------------
LINUX/MAC USERS - JAVAFX NOTE
--------------------------------------------------------------------------------

If you get "Error: JavaFX runtime components are missing", you need to 
specify the JavaFX module path:

java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml \
     -jar BankApplication.jar

Or install a JDK that includes JavaFX (like Liberica Full JDK or Azul Zulu FX).

--------------------------------------------------------------------------------
NETWORK SETUP FOR MULTIPLE MACHINES
--------------------------------------------------------------------------------

To run across multiple machines:

1. On Bank Server machine:
   java -jar BankApplication.jar
   Note the IP address of this machine (e.g., 192.168.1.100)

2. On Auction House machine:
   java -jar AuctionHouseApplication.jar
   Enter Bank Host: 192.168.1.100 (bank server IP)
   
3. On Agent machines:
   java -jar AgentApplication.jar
   Enter Bank Host: 192.168.1.100

Ensure firewall allows connections on:
- Bank port (default 9999)
- Auction house ports (dynamically assigned, or set to specific port)

================================================================================

