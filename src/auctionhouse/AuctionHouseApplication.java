package auctionhouse;

import common.AuctionItem;
import common.AuctionHouseInfo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;

/**
 * JavaFX GUI for Auction House with Activity Logging and Timer Display
 */
public class AuctionHouseApplication extends Application {
    private AuctionHouse auctionHouse;
    private AuctionHouseServer server;
    private TextArea logArea;
    private TextArea activityLog;
    private Label statusLabel;
    private Label auctionHouseIdLabel;
    private TableView<ItemDisplay> itemsTable;
    private ObservableList<ItemDisplay> itemsList;
    private TextField itemDescriptionField;
    private TextField minimumBidField;
    private Button addItemButton;
    private Button removeItemButton;
    private Button startServerButton;
    private TextField bankHostField;
    private TextField bankPortField;
    private TextField auctionPortField;

    // ⭐ NEW: Timer update thread
    private Thread timerUpdateThread;

    // ⭐ Enhanced ItemDisplay with timer
    public static class ItemDisplay {
        private final int itemId;
        private final String description;
        private final double minimumBid;
        private final double currentBid;
        private final int currentBidder;
        private final long auctionEndTime;
        private SimpleStringProperty timeRemaining;

        public ItemDisplay(AuctionItem item) {
            this.itemId = item.itemId;
            this.description = item.description;
            this.minimumBid = item.minimumBid;
            this.currentBid = item.currentBid;
            this.currentBidder = item.currentBidderAccountNumber;
            this.auctionEndTime = item.auctionEndTime;
            this.timeRemaining = new SimpleStringProperty(calculateTimeRemaining());
        }

        public int getItemId() { return itemId; }
        public String getDescription() { return description; }
        public double getMinimumBid() { return minimumBid; }
        public double getCurrentBid() { return currentBid; }
        public int getCurrentBidder() { return currentBidder; }
        public String getTimeRemaining() { return timeRemaining.get(); }
        public SimpleStringProperty timeRemainingProperty() { return timeRemaining; }

        // ⭐ Calculate time remaining
        public String calculateTimeRemaining() {
            if (auctionEndTime == 0) {
                return "No bids yet";
            }

            long now = System.currentTimeMillis();
            long remaining = auctionEndTime - now;

            if (remaining <= 0) {
                return "Ending...";
            }

            long seconds = remaining / 1000;
            return String.format("%02d:%02d", seconds / 60, seconds % 60);
        }

        // ⭐ Update timer display
        public void updateTimer() {
            Platform.runLater(() -> timeRemaining.set(calculateTimeRemaining()));
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Auction House Management");

        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        VBox topSection = createTopSection();
        mainLayout.setTop(topSection);

        SplitPane centerSection = createCenterSection();
        mainLayout.setCenter(centerSection);

        VBox bottomSection = createBottomSection();
        mainLayout.setBottom(bottomSection);

        Scene scene = new Scene(mainLayout, 1200, 800);
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(event -> {
            // ⭐ Stop timer thread
            if (timerUpdateThread != null && timerUpdateThread.isAlive()) {
                timerUpdateThread.interrupt();
            }

            if (auctionHouse != null && auctionHouse.hasActiveBids()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Active Bids");
                alert.setHeaderText("Cannot close auction house");
                alert.setContentText("There are still active bids. Please wait for all auctions to complete.");
                alert.showAndWait();
                event.consume();
                return;
            }

            if (server != null) {
                try {
                    BankMessages.DeregisterRequest request =
                            new BankMessages.DeregisterRequest(
                                    server.getAuctionHouseAccountNumber(), "AUCTION_HOUSE");
                    server.deregisterFromBank(request);
                    log("Deregistered from bank");
                } catch (Exception e) {
                    log("Error deregistering: " + e.getMessage());
                }
                server.stop();
            }
            Platform.exit();
        });

        primaryStage.show();
    }

    private VBox createTopSection() {
        VBox topSection = new VBox(10);
        topSection.setPadding(new Insets(10));
        topSection.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1;");

        Label titleLabel = new Label("Auction House Server");
        titleLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        auctionHouseIdLabel = new Label("Not connected");
        statusLabel = new Label("Status: Not started");

        GridPane connectionGrid = new GridPane();
        connectionGrid.setHgap(10);
        connectionGrid.setVgap(5);

        bankHostField = new TextField("localhost");
        bankPortField = new TextField("5000");
        auctionPortField = new TextField("0");

        connectionGrid.add(new Label("Bank Host:"), 0, 0);
        connectionGrid.add(bankHostField, 1, 0);
        connectionGrid.add(new Label("Bank Port:"), 2, 0);
        connectionGrid.add(bankPortField, 3, 0);
        connectionGrid.add(new Label("Auction Port:"), 4, 0);
        connectionGrid.add(auctionPortField, 5, 0);

        startServerButton = new Button("Start Server");
        startServerButton.setOnAction(e -> startServer());

        topSection.getChildren().addAll(titleLabel, auctionHouseIdLabel, statusLabel,
                connectionGrid, startServerButton);

        return topSection;
    }

    private SplitPane createCenterSection() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);

        // Left: Items table
        VBox leftSection = new VBox(10);
        leftSection.setPadding(new Insets(10));

        Label itemsLabel = new Label("Auction Items");
        itemsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        itemsList = FXCollections.observableArrayList();
        itemsTable = new TableView<>(itemsList);

        TableColumn<ItemDisplay, Integer> itemIdCol = new TableColumn<>("Item ID");
        itemIdCol.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        itemIdCol.setPrefWidth(60);

        TableColumn<ItemDisplay, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(180);

        TableColumn<ItemDisplay, Double> minBidCol = new TableColumn<>("Min Bid");
        minBidCol.setCellValueFactory(new PropertyValueFactory<>("minimumBid"));
        minBidCol.setPrefWidth(80);
        minBidCol.setCellFactory(col -> new TableCell<ItemDisplay, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item));
            }
        });

        TableColumn<ItemDisplay, Double> currentBidCol = new TableColumn<>("Current Bid");
        currentBidCol.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        currentBidCol.setPrefWidth(100);
        currentBidCol.setCellFactory(col -> new TableCell<ItemDisplay, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item));
            }
        });

        TableColumn<ItemDisplay, Integer> bidderCol = new TableColumn<>("Bidder");
        bidderCol.setCellValueFactory(new PropertyValueFactory<>("currentBidder"));
        bidderCol.setPrefWidth(80);
        bidderCol.setCellFactory(col -> new TableCell<ItemDisplay, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item == -1) {
                    setText("-");
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-text-fill: blue; -fx-font-weight: bold;");
                }
            }
        });

        // ⭐ NEW: Timer column
        TableColumn<ItemDisplay, String> timerCol = new TableColumn<>("Time Left");
        timerCol.setCellValueFactory(cellData -> cellData.getValue().timeRemainingProperty());
        timerCol.setPrefWidth(90);
        timerCol.setCellFactory(col -> new TableCell<ItemDisplay, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Color coding
                    if (item.equals("No bids yet")) {
                        setStyle("-fx-text-fill: gray;");
                    } else if (item.equals("Ending...")) {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else if (item.startsWith("00:")) {
                        // Less than 1 minute - red
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    } else {
                        // More than 1 minute - green
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    }
                }
            }
        });

        itemsTable.getColumns().addAll(itemIdCol, descCol, minBidCol,
                currentBidCol, bidderCol, timerCol);

        HBox itemControls = new HBox(10);
        itemDescriptionField = new TextField();
        itemDescriptionField.setPromptText("Item description");
        itemDescriptionField.setPrefWidth(200);

        minimumBidField = new TextField();
        minimumBidField.setPromptText("Minimum bid");
        minimumBidField.setPrefWidth(100);

        addItemButton = new Button("Add Item");
        addItemButton.setDisable(true);
        addItemButton.setOnAction(e -> addItem());

        removeItemButton = new Button("Remove Selected");
        removeItemButton.setDisable(true);
        removeItemButton.setOnAction(e -> removeItem());

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshItems());

        itemControls.getChildren().addAll(itemDescriptionField, minimumBidField,
                addItemButton, removeItemButton, refreshButton);

        leftSection.getChildren().addAll(itemsLabel, itemsTable, itemControls);

        // Right: Activity Log
        VBox rightSection = new VBox(10);
        rightSection.setPadding(new Insets(10));

        Label activityLabel = new Label("Activity Log");
        activityLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        activityLog = new TextArea();
        activityLog.setEditable(false);
        activityLog.setWrapText(true);
        activityLog.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");

        rightSection.getChildren().addAll(activityLabel, activityLog);
        VBox.setVgrow(activityLog, Priority.ALWAYS);

        splitPane.getItems().addAll(leftSection, rightSection);
        return splitPane;
    }

    private VBox createBottomSection() {
        VBox bottomSection = new VBox(5);
        bottomSection.setPadding(new Insets(10));

        Label logLabel = new Label("Server Log");
        logLabel.setStyle("-fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(100);
        logArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");

        bottomSection.getChildren().addAll(logLabel, logArea);
        return bottomSection;
    }

    private void startServer() {
        try {
            String bankHost = bankHostField.getText();
            int bankPort = Integer.parseInt(bankPortField.getText());
            int auctionPort = Integer.parseInt(auctionPortField.getText());

            log("Connecting to bank at " + bankHost + ":" + bankPort + "...");
            server = new AuctionHouseServer(bankHost, bankPort, auctionPort, true);
            auctionHouse = server.getAuctionHouse();

            // Set callback for activity logging
            auctionHouse.setCallback(new AuctionHouse.AuctionHouseCallback() {
                @Override
                public void onBidPlaced(int itemId, String itemDesc, int agentAccount, double bidAmount) {
                    logActivity(String.format("✓ BID ACCEPTED: Agent %d bid $%.2f on Item %d (%s)",
                            agentAccount, bidAmount, itemId, itemDesc));
                }

                @Override
                public void onBidRejected(int itemId, String itemDesc, int agentAccount,
                                          double bidAmount, String reason) {
                    logActivity(String.format("✗ BID REJECTED: Agent %d bid $%.2f on Item %d - %s",
                            agentAccount, bidAmount, itemId, reason));
                }

                @Override
                public void onAgentOutbid(int itemId, String itemDesc, int previousBidder,
                                          int newBidder, double newBid) {
                    logActivity(String.format("⚡ OUTBID: Agent %d outbid Agent %d on Item %d (%s) with $%.2f",
                            newBidder, previousBidder, itemId, itemDesc, newBid));
                }

                @Override
                public void onItemSold(int itemId, String itemDesc, int winner, double finalPrice) {
                    logActivity(String.format("🏆 SOLD: Item %d (%s) sold to Agent %d for $%.2f",
                            itemId, itemDesc, winner, finalPrice));
                }
            });

            log("Server started on port " + server.getPort());
            log("Auction House ID: " + auctionHouse.getAuctionHouseId());

            auctionHouseIdLabel.setText("Auction House ID: " + auctionHouse.getAuctionHouseId());
            statusLabel.setText("Status: Running on port " + server.getPort());

            startServerButton.setDisable(true);
            addItemButton.setDisable(false);
            removeItemButton.setDisable(false);
            bankHostField.setDisable(true);
            bankPortField.setDisable(true);
            auctionPortField.setDisable(true);

            refreshItems();

            // ⭐ Start timer update thread
            startTimerUpdateThread();

            // Auto-refresh items
            Thread refreshThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(this::refreshItems);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            refreshThread.setDaemon(true);
            refreshThread.start();

        } catch (Exception e) {
            log("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ⭐ NEW: Timer update thread
    private void startTimerUpdateThread() {
        timerUpdateThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000); // Update every second

                    Platform.runLater(() -> {
                        for (ItemDisplay item : itemsList) {
                            item.updateTimer();
                        }
                    });

                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        timerUpdateThread.setDaemon(true);
        timerUpdateThread.start();
    }

    private void addItem() {
        try {
            String description = itemDescriptionField.getText().trim();
            String minBidText = minimumBidField.getText().trim();

            if (description.isEmpty() || minBidText.isEmpty()) {
                log("Error: Please enter item description and minimum bid");
                return;
            }

            double minBid = Double.parseDouble(minBidText);
            if (minBid <= 0) {
                log("Error: Minimum bid must be positive");
                return;
            }

            auctionHouse.addNewItem(description, minBid);
            log("Added new item: " + description + " (Min bid: $" + minBid + ")");

            itemDescriptionField.clear();
            minimumBidField.clear();
            refreshItems();

        } catch (NumberFormatException e) {
            log("Error: Invalid minimum bid amount");
        }
    }

    private void removeItem() {
        ItemDisplay selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log("Error: No item selected");
            return;
        }

        if (selected.getCurrentBidder() != -1) {
            log("Error: Cannot remove item with active bid");
            return;
        }

        boolean removed = auctionHouse.removeItem(selected.getItemId());
        if (removed) {
            log("Removed item " + selected.getItemId());
            refreshItems();
        } else {
            log("Failed to remove item " + selected.getItemId());
        }
    }

    private void refreshItems() {
        if (auctionHouse == null) return;

        ItemDisplay currentSelection = itemsTable.getSelectionModel().getSelectedItem();
        Integer selectedItemId = (currentSelection != null) ? currentSelection.getItemId() : null;

        AuctionMessages.GetItemsResponse response = auctionHouse.getItems();
        if (response.success) {
            itemsList.clear();
            ItemDisplay newSelection = null;

            for (AuctionItem item : response.items) {
                ItemDisplay display = new ItemDisplay(item);
                itemsList.add(display);

                if (selectedItemId != null && item.itemId == selectedItemId) {
                    newSelection = display;
                }
            }

            if (newSelection != null) {
                itemsTable.getSelectionModel().select(newSelection);
            }
        }
    }

    private void logActivity(String message) {
        String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            activityLog.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    private void log(String message) {
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
