package agent;

import common.AuctionHouseInfo;
import common.AuctionItem;

import java.io.IOException;
import java.util.List;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * JavaFX GUI for the Agent (Bidder)
 * Includes: proper error handling, type safety, auto-refresh,
 * exit prevention with unresolved bids, command-line support, and "My Purchases" view.
 *
 * Command-line usage:
 *   java -jar AgentApplication.jar [-n name] [-b balance] [-bh host] [-bp port]
 */
public class AgentApplication extends Application {

    private Agent agent;

    private TextArea logArea;
    private Label balanceLabel;
    private Label availableFundsLabel;
    private Label blockedFundsLabel;

    private ComboBox<AuctionHouseInfo> auctionHouseCombo;
    private TableView<AuctionItem> itemsTable;
    private TableView<Agent.Purchase> purchasesTable;

    private TextField bidAmountField;
    private Label statusLabel;

    // Command-line parameters
    private String cmdAgentName = null;
    private String cmdBalance = null;
    private String cmdBankHost = null;
    private String cmdBankPort = null;

    /**
     * Initialize method called before start()
     * Processes command-line arguments
     */
    @Override
    public void init() {
        Parameters params = getParameters();
        List<String> raw = params.getRaw();

        for (int i = 0; i < raw.size(); i++) {
            String arg = raw.get(i);

            if ((arg.equals("-n") || arg.equals("--name")) && i + 1 < raw.size()) {
                cmdAgentName = raw.get(++i);
            } else if ((arg.equals("-b") || arg.equals("--balance")) && i + 1 < raw.size()) {
                cmdBalance = raw.get(++i);
            } else if ((arg.equals("-bh") || arg.equals("--bank-host")) && i + 1 < raw.size()) {
                cmdBankHost = raw.get(++i);
            } else if ((arg.equals("-bp") || arg.equals("--bank-port")) && i + 1 < raw.size()) {
                cmdBankPort = raw.get(++i);
            } else if (arg.equals("-h") || arg.equals("--help")) {
                printUsage();
                Platform.exit();
                System.exit(0);
            }
        }

        // Log command-line parameters if provided
        if (cmdAgentName != null || cmdBalance != null || cmdBankHost != null || cmdBankPort != null) {
            System.out.println("[AGENT GUI] Command-line parameters detected:");
            if (cmdAgentName != null) System.out.println("  Name: " + cmdAgentName);
            if (cmdBalance != null) System.out.println("  Balance: " + cmdBalance);
            if (cmdBankHost != null) System.out.println("  Bank Host: " + cmdBankHost);
            if (cmdBankPort != null) System.out.println("  Bank Port: " + cmdBankPort);
        }
    }

    /**
     * Print usage information
     */
    private void printUsage() {
        System.out.println("Usage: java -jar AgentApplication.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -n, --name <name>        Agent name (default: Agent1)");
        System.out.println("  -b, --balance <amount>   Initial balance (default: 10000)");
        System.out.println("  -bh, --bank-host <host>  Bank hostname (default: localhost)");
        System.out.println("  -bp, --bank-port <port>  Bank port (default: 5000)");
        System.out.println("  -h, --help               Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar AgentApplication.jar");
        System.out.println("  java -jar AgentApplication.jar -n Alice -b 15000");
        System.out.println("  java -jar AgentApplication.jar -n Bob -bh 192.168.1.100 -bp 9999");
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Distributed Auction - Agent");
        primaryStage.setWidth(1000);
        primaryStage.setHeight(700);

        BorderPane root = new BorderPane();

        // Top panel with agent info
        VBox topPanel = createTopPanel();
        root.setTop(topPanel);

        // Center panel with items, purchases, and bidding
        HBox centerPanel = createCenterPanel();
        root.setCenter(centerPanel);

        // Bottom panel with log
        VBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);

        // Prevent exit while there are unresolved bids (blocked funds > 0)
        primaryStage.setOnCloseRequest(event -> {
            if (agent != null && agent.getBlockedFunds() > 0) {
                showError("Cannot exit",
                        "You still have unresolved bids (blocked funds). "
                                + "Wait for auctions to finish or be outbid.");
                event.consume();
                return;
            }

            if (agent != null) {
                agent.disconnect();
                log("Disconnected from auction system");
            }
            Platform.exit();
        });

        primaryStage.show();

        // Show login dialog with command-line defaults
        showLoginDialog();
    }

    private VBox createTopPanel() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        vbox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label("Agent Control Panel");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        HBox infoPanel = new HBox(30);
        infoPanel.setPadding(new Insets(10));

        VBox balanceBox = new VBox(5);
        Label balanceTitleLabel = new Label("Total Balance:");
        balanceLabel = new Label("$0.00");
        balanceLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        balanceBox.getChildren().addAll(balanceTitleLabel, balanceLabel);

        VBox availableBox = new VBox(5);
        Label availableTitleLabel = new Label("Available Funds:");
        availableFundsLabel = new Label("$0.00");
        availableFundsLabel.setStyle(
                "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: green;");
        availableBox.getChildren().addAll(availableTitleLabel, availableFundsLabel);

        VBox blockedBox = new VBox(5);
        Label blockedTitleLabel = new Label("Blocked Funds:");
        blockedFundsLabel = new Label("$0.00");
        blockedFundsLabel.setStyle(
                "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: red;");
        blockedBox.getChildren().addAll(blockedTitleLabel, blockedFundsLabel);

        infoPanel.getChildren().addAll(balanceBox, availableBox, blockedBox);
        vbox.getChildren().addAll(titleLabel, infoPanel);
        return vbox;
    }

    private HBox createCenterPanel() {
        HBox hbox = new HBox(10);
        hbox.setPadding(new Insets(10));

        // Left panel: Auction houses, items, and purchases
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(600);

        Label auctionHouseLabel = new Label("Select Auction House:");
        auctionHouseCombo = new ComboBox<>();
        auctionHouseCombo.setPrefWidth(300);

        // Custom cell factory for proper display
        auctionHouseCombo.setCellFactory(param -> new ListCell<AuctionHouseInfo>() {
            @Override
            protected void updateItem(AuctionHouseInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        auctionHouseCombo.setButtonCell(new ListCell<AuctionHouseInfo>() {
            @Override
            protected void updateItem(AuctionHouseInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        auctionHouseCombo.setOnAction(event -> loadItems());

        HBox auctionPanel = new HBox(10);
        auctionPanel.getChildren().addAll(auctionHouseLabel, auctionHouseCombo);

        Label itemsLabel = new Label("Available Items:");
        itemsTable = new TableView<>();
        itemsTable.setPrefHeight(250);

        // Item table columns
        TableColumn<AuctionItem, Number> itemIdCol = new TableColumn<>("Item ID");
        itemIdCol.setCellValueFactory(cd ->
                new SimpleIntegerProperty(cd.getValue().itemId));
        itemIdCol.setPrefWidth(80);

        TableColumn<AuctionItem, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().description));
        descCol.setPrefWidth(200);

        TableColumn<AuctionItem, Number> minBidCol = new TableColumn<>("Min Bid");
        minBidCol.setCellValueFactory(cd ->
                new SimpleDoubleProperty(cd.getValue().minimumBid));
        minBidCol.setPrefWidth(100);
        minBidCol.setCellFactory(col -> new TableCell<AuctionItem, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("$%.2f", item.doubleValue()));
            }
        });

        TableColumn<AuctionItem, Number> currentBidCol = new TableColumn<>("Current Bid");
        currentBidCol.setCellValueFactory(cd ->
                new SimpleDoubleProperty(cd.getValue().currentBid));
        currentBidCol.setPrefWidth(100);
        currentBidCol.setCellFactory(col -> new TableCell<AuctionItem, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("$%.2f", item.doubleValue()));
            }
        });

        itemsTable.getColumns().addAll(itemIdCol, descCol, minBidCol, currentBidCol);

        // My Purchases table
        Label purchasesLabel = new Label("My Purchases:");
        purchasesTable = new TableView<>();
        purchasesTable.setPrefHeight(150);

        TableColumn<Agent.Purchase, Number> pHouseCol = new TableColumn<>("House");
        pHouseCol.setCellValueFactory(cd ->
                new SimpleIntegerProperty(cd.getValue().auctionHouseId));

        TableColumn<Agent.Purchase, Number> pItemCol = new TableColumn<>("Item ID");
        pItemCol.setCellValueFactory(cd ->
                new SimpleIntegerProperty(cd.getValue().itemId));

        TableColumn<Agent.Purchase, String> pDescCol = new TableColumn<>("Description");
        pDescCol.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().description));

        TableColumn<Agent.Purchase, Number> pPriceCol = new TableColumn<>("Price");
        pPriceCol.setCellValueFactory(cd ->
                new SimpleDoubleProperty(cd.getValue().price));
        pPriceCol.setCellFactory(col -> new TableCell<Agent.Purchase, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : String.format("$%.2f", item.doubleValue()));
            }
        });

        purchasesTable.getColumns().addAll(pHouseCol, pItemCol, pDescCol, pPriceCol);

        leftPanel.getChildren().addAll(
                auctionPanel,
                itemsLabel,
                itemsTable,
                purchasesLabel,
                purchasesTable
        );

        // Right panel: Bidding controls
        VBox rightPanel = new VBox(10);
        rightPanel.setPrefWidth(300);
        rightPanel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 1;");
        rightPanel.setPadding(new Insets(10));

        Label bidLabel = new Label("Place Bid");
        bidLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        Label amountLabel = new Label("Bid Amount:");
        bidAmountField = new TextField();
        bidAmountField.setPromptText("Enter bid amount");

        Button placeBidButton = new Button("Place Bid");
        placeBidButton.setPrefWidth(200);
        placeBidButton.setOnAction(event -> placeBid());

        Button refreshButton = new Button("Refresh Items");
        refreshButton.setPrefWidth(200);
        refreshButton.setOnAction(event -> loadItems());

        Button refreshAuctionsButton = new Button("Refresh Auction Houses");
        refreshAuctionsButton.setPrefWidth(200);
        refreshAuctionsButton.setOnAction(event -> refreshAuctionHouses());

        statusLabel = new Label("Ready");
        statusLabel.setWrapText(true);
        setStatusMessage("Ready", "blue");

        rightPanel.getChildren().addAll(
                bidLabel, amountLabel, bidAmountField,
                placeBidButton, refreshButton, refreshAuctionsButton,
                new Separator(), statusLabel
        );

        hbox.getChildren().addAll(leftPanel, rightPanel);
        return hbox;
    }

    private VBox createBottomPanel() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));
        vbox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");
        vbox.setPrefHeight(150);

        Label logLabel = new Label("Activity Log:");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(150);
        logArea.setStyle("-fx-font-family: monospace;");

        vbox.getChildren().addAll(logLabel, logArea);
        return vbox;
    }

    /**
     * Show login dialog with command-line parameters pre-filled
     */
    private void showLoginDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Agent Login");
        dialog.setHeaderText("Enter Agent Information");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("Agent Name");
        // Use command-line parameter if provided, otherwise default
        nameField.setText(cmdAgentName != null ? cmdAgentName : "Agent1");

        TextField balanceField = new TextField();
        balanceField.setPromptText("Initial Balance");
        // Use command-line parameter if provided, otherwise default
        balanceField.setText(cmdBalance != null ? cmdBalance : "10000");

        TextField bankHostField = new TextField();
        bankHostField.setPromptText("Bank Host");
        // Use command-line parameter if provided, otherwise default
        bankHostField.setText(cmdBankHost != null ? cmdBankHost : "localhost");

        TextField bankPortField = new TextField();
        bankPortField.setPromptText("Bank Port");
        // Use command-line parameter if provided, otherwise default
        bankPortField.setText(cmdBankPort != null ? cmdBankPort : "5000");

        grid.add(new Label("Agent Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Initial Balance:"), 0, 1);
        grid.add(balanceField, 1, 1);
        grid.add(new Label("Bank Host:"), 0, 2);
        grid.add(bankHostField, 1, 2);
        grid.add(new Label("Bank Port:"), 0, 3);
        grid.add(bankPortField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new String[] {
                        nameField.getText(),
                        balanceField.getText(),
                        bankHostField.getText(),
                        bankPortField.getText()
                };
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            try {
                String agentName = result[0];
                double initialBalance = Double.parseDouble(result[1]);
                String bankHost = result[2];
                int bankPort = Integer.parseInt(result[3]);

                agent = new Agent(agentName, initialBalance, bankHost, bankPort);
                agent.setUICallback(new Agent.AgentUICallback() {
                    @Override
                    public void onBalanceUpdated(double total,
                                                 double available,
                                                 double blocked) {
                        Platform.runLater(() -> {
                            balanceLabel.setText(String.format("$%.2f", total));
                            availableFundsLabel.setText(String.format("$%.2f", available));
                            blockedFundsLabel.setText(String.format("$%.2f", blocked));
                        });
                    }

                    @Override
                    public void onItemsUpdated(AuctionItem[] items) {
                        Platform.runLater(() -> {
                            itemsTable.getItems().clear();
                            if (items != null) {
                                for (AuctionItem item : items) {
                                    itemsTable.getItems().add(item);
                                }
                            }
                        });
                    }

                    @Override
                    public void onBidStatusChanged(int itemId,
                                                   String status,
                                                   String message) {
                        Platform.runLater(() -> {
                            log("[" + status + "] Item " + itemId + ": " + message);
                            String color = "blue";
                            if ("WINNER".equals(status)) {
                                color = "green";
                            } else if ("OUTBID".equals(status)
                                    || "REJECTED".equals(status)) {
                                color = "red";
                            }
                            setStatusMessage(status + ": " + message, color);
                            // Auto-refresh items after status change
                            loadItems();
                        });
                    }

                    @Override
                    public void onPurchasesUpdated(java.util.List<Agent.Purchase> purchases) {
                        Platform.runLater(() -> {
                            purchasesTable.getItems().setAll(purchases);
                        });
                    }
                });

                // Load auction houses
                AuctionHouseInfo[] auctionHouses = agent.getAuctionHouses();
                auctionHouseCombo.getItems().clear();
                for (AuctionHouseInfo info : auctionHouses) {
                    auctionHouseCombo.getItems().add(info);
                }

                if (auctionHouses.length > 0) {
                    auctionHouseCombo.getSelectionModel().selectFirst();
                    loadItems();
                } else {
                    log("No auction houses available. Waiting for auction houses to register...");
                }

                log("Connected as " + agentName);
                log("Initial Balance: $" + String.format("%.2f", initialBalance));
                agent.updateBalance();
                setStatusMessage("Connected successfully", "green");
            } catch (NumberFormatException e) {
                showError("Invalid input",
                        "Please enter valid numbers for balance and port.");
            } catch (IOException e) {
                showError("Connection Error",
                        "Failed to connect to bank: " + e.getMessage());
            } catch (ClassNotFoundException e) {
                showError("System Error",
                        "Communication error: " + e.getMessage());
            }
        });
    }

    private void loadItems() {
        if (agent == null) {
            setStatusMessage("Please login first", "red");
            return;
        }

        AuctionHouseInfo selectedHouse = auctionHouseCombo.getValue();
        if (selectedHouse == null) {
            setStatusMessage("Please select an auction house", "orange");
            return;
        }

        setStatusMessage("Loading items...", "blue");

        new Thread(() -> {
            try {
                // Connect to auction house if not already connected
                agent.connectToAuctionHouse(selectedHouse.auctionHouseId);

                // Start listening for notifications
                agent.startListeningForNotifications(selectedHouse.auctionHouseId);

                // Get items with timeout handling
                AuctionItem[] items =
                        agent.getItemsFromAuctionHouse(selectedHouse.auctionHouseId);

                Platform.runLater(() -> {
                    itemsTable.getItems().clear();
                    if (items != null && items.length > 0) {
                        for (AuctionItem item : items) {
                            itemsTable.getItems().add(item);
                        }
                        log("Loaded " + items.length + " items from Auction House "
                                + selectedHouse.auctionHouseId);
                        setStatusMessage("Items loaded successfully", "green");
                    } else {
                        log("No items available at Auction House "
                                + selectedHouse.auctionHouseId);
                        setStatusMessage("No items available", "orange");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    log("Error loading items: " + e.getMessage());
                    setStatusMessage("Failed to load items - " + e.getMessage(), "red");

                    // Clear loading state
                    itemsTable.getItems().clear();
                });
            } catch (ClassNotFoundException e) {
                Platform.runLater(() -> {
                    log("Communication error: " + e.getMessage());
                    setStatusMessage("Communication error", "red");

                    // Clear loading state
                    itemsTable.getItems().clear();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("Unexpected error: " + e.getMessage());
                    setStatusMessage("Unexpected error", "red");

                    // Clear loading state
                    itemsTable.getItems().clear();
                });
            }
        }).start();
    }

    // Refresh auction houses dynamically
    private void refreshAuctionHouses() {
        if (agent == null) {
            setStatusMessage("Please login first", "red");
            return;
        }

        new Thread(() -> {
            agent.refreshAuctionHouses();
            AuctionHouseInfo[] auctionHouses = agent.getAuctionHouses();
            Platform.runLater(() -> {
                AuctionHouseInfo currentSelection = auctionHouseCombo.getValue();
                auctionHouseCombo.getItems().clear();
                for (AuctionHouseInfo info : auctionHouses) {
                    auctionHouseCombo.getItems().add(info);
                }

                // Restore previous selection if still available
                if (currentSelection != null) {
                    for (AuctionHouseInfo info : auctionHouses) {
                        if (info.auctionHouseId == currentSelection.auctionHouseId) {
                            auctionHouseCombo.setValue(info);
                            break;
                        }
                    }
                }

                log("Refreshed auction house list: "
                        + auctionHouses.length + " available");
                setStatusMessage("Auction houses refreshed", "green");
            });
        }).start();
    }

    // Place bid with validation and error handling
    private void placeBid() {
        if (agent == null) {
            setStatusMessage("Please login first", "red");
            return;
        }

        AuctionHouseInfo selectedHouse = auctionHouseCombo.getValue();
        if (selectedHouse == null) {
            setStatusMessage("Please select an auction house", "red");
            return;
        }

        AuctionItem selectedItem =
                itemsTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            setStatusMessage("Please select an item", "red");
            return;
        }

        String bidText = bidAmountField.getText().trim();
        if (bidText.isEmpty()) {
            setStatusMessage("Please enter a bid amount", "red");
            return;
        }

        try {
            double bidAmount = Double.parseDouble(bidText);

            if (bidAmount <= 0) {
                setStatusMessage("Bid amount must be positive", "red");
                return;
            }

            if (bidAmount < selectedItem.minimumBid) {
                setStatusMessage(
                        "Bid must be at least $"
                                + String.format("%.2f", selectedItem.minimumBid),
                        "red");
                return;
            }

            if (bidAmount <= selectedItem.currentBid) {
                setStatusMessage(
                        "Bid must be higher than current bid of $"
                                + String.format("%.2f", selectedItem.currentBid),
                        "red");
                return;
            }

            if (bidAmount > agent.getAvailableFunds()) {
                setStatusMessage(
                        "Insufficient funds (Available: $"
                                + String.format("%.2f", agent.getAvailableFunds())
                                + ")",
                        "red");
                return;
            }

            setStatusMessage("Placing bid...", "blue");

            new Thread(() -> {
                try {
                    boolean success = agent.placeBid(
                            selectedHouse.auctionHouseId,
                            selectedItem.itemId,
                            bidAmount);
                    Platform.runLater(() -> {
                        if (success) {
                            log("Bid placed: $"
                                    + String.format("%.2f", bidAmount)
                                    + " on item " + selectedItem.itemId);
                            setStatusMessage("Bid placed successfully", "green");
                            bidAmountField.clear();
                            loadItems(); // Refresh after bid
                        } else {
                            log("Bid rejected for item " + selectedItem.itemId);
                            setStatusMessage("Bid rejected", "red");
                        }
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        log("Error placing bid: " + e.getMessage());
                        setStatusMessage("Connection error", "red");
                    });
                } catch (ClassNotFoundException e) {
                    Platform.runLater(() -> {
                        log("Communication error: " + e.getMessage());
                        setStatusMessage("Communication error", "red");
                    });
                }
            }).start();
        } catch (NumberFormatException e) {
            setStatusMessage("Invalid bid amount", "red");
        }
    }

    // Helper method for logging
    private void log(String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    // Helper method for status updates
    private void setStatusMessage(String message, String color) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    // Helper method for error dialogs
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}