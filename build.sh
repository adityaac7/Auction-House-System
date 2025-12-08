#!/bin/bash
# Build script for Distributed Auction System
# Run this script to compile and create JAR files

set -e  # Exit on error

echo "=== Building Distributed Auction System ==="

# Create output directories
mkdir -p out/production/classes
mkdir -p dist
mkdir -p manifests

# Create manifest files if they don't exist
echo "Creating manifest files..."
cat > manifests/BankApplication.mf << 'EOF'
Manifest-Version: 1.0
Main-Class: bank.BankApplication

EOF

cat > manifests/AuctionHouseApplication.mf << 'EOF'
Manifest-Version: 1.0
Main-Class: auctionhouse.AuctionHouseApplication

EOF

cat > manifests/AgentApplication.mf << 'EOF'
Manifest-Version: 1.0
Main-Class: agent.AgentApplication

EOF

cat > manifests/AutomatedAgent.mf << 'EOF'
Manifest-Version: 1.0
Main-Class: agent.AutomatedAgent

EOF

# Compile all Java files
echo "Compiling Java source files..."
javac -d out/production/classes -sourcepath src \
    src/common/*.java \
    src/messages/*.java \
    src/bank/*.java \
    src/auctionhouse/*.java \
    src/agent/*.java

echo "Compilation successful."

# Create JAR files
echo "Creating JAR files..."

jar cfm dist/BankApplication.jar manifests/BankApplication.mf -C out/production/classes .
jar cfm dist/AuctionHouseApplication.jar manifests/AuctionHouseApplication.mf -C out/production/classes .
jar cfm dist/AgentApplication.jar manifests/AgentApplication.mf -C out/production/classes .
jar cfm dist/AutomatedAgent.jar manifests/AutomatedAgent.mf -C out/production/classes .

echo ""
echo "=== Build Complete ==="
echo "JAR files created in dist/ directory:"
ls -la dist/*.jar
echo ""
echo "=================================================================================="
echo "                           RUN INSTRUCTIONS"
echo "=================================================================================="
echo ""
echo "Start in this order:"
echo ""
echo "1. Bank Server (GUI):"
echo "   java -jar dist/BankApplication.jar"
echo ""
echo "2. Auction House (GUI):"
echo "   java -jar dist/AuctionHouseApplication.jar"
echo ""
echo "3. Agent - Choose one:"
echo "   GUI Agent:        java -jar dist/AgentApplication.jar"
echo "   Automated Agent:  java -jar dist/AutomatedAgent.jar [bankHost] [bankPort] [balance] [bidMultiplier] [maxBudgetRatio]"
echo ""
echo "Example (AutomatedAgent with defaults):"
echo "   java -jar dist/AutomatedAgent.jar"
echo ""
echo "Example (AutomatedAgent custom config):"
echo "   java -jar dist/AutomatedAgent.jar localhost 9999 5000 1.1 0.25"
echo ""
echo "=================================================================================="

