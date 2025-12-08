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

/**
 * JavaFX GUI for Auction House with Manual Item Management
 * FIXED: Proper deregistration and active bid checking on exit
 */
public class AuctionHouseApplication extends Application {
    private AuctionHouse auctionHouse;
    private AuctionHouseServer server;
    private TextArea logArea;
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

    // Helper class for displaying items in table
    public static class ItemDisplay {
        private final int itemId;
        private final String description;
        private final double minimumBid;
        private final double currentBid;
        private final int currentBidder;

        public ItemDisplay(AuctionItem item) {
            this.itemId = item.itemId;
            this.description = item.description;
            this.minimumBid = item.minimumBid;
            this.currentBid = item.currentBid;
            this.currentBidder = item.currentBidderAccountNumber;
        }

        public int getItemId() { return itemId; }
        public String getDescription() { return description; }
        public double getMinimumBid() { return minimumBid; }
        public double getCurrentBid() { return currentBid; }
        public int getCurrentBidder() { return currentBidder; }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Auction House Management");

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // Top: Status and connection info
        VBox topSection = createTopSection();
        mainLayout.setTop(topSection);

        // Center: Items table
        VBox centerSection = createCenterSection();
        mainLayout.setCenter(centerSection);

        // Bottom: Log area
        VBox bottomSection = createBottomSection();
        mainLayout.setBottom(bottomSection);

        Scene scene = new Scene(mainLayout, 900, 700);
        primaryStage.setScene(scene);

        // FIXED: Proper shutdown with deregistration and active bid check
        primaryStage.setOnCloseRequest(event -> {
            if (auctionHouse != null && auctionHouse.hasActiveBids()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Active Bids");
                alert.setHeaderText("Cannot close auction house");
                alert.setContentText("There are still active bids. Please wait for all auctions to complete.");
                alert.showAndWait();
                event.consume(); // Cancel the close request
                return;
            }

            if (auctionHouse != null) {
                try {
                    BankMessages.DeregisterRequest request =
                            new BankMessages.DeregisterRequest(auctionHouse.getAuctionHouseId(),
                                    "AUCTION_HOUSE");
                    auctionHouse.sendToBankAndWait(request);
                    log("Deregistered from bank");
                } catch (Exception e) {
                    log("Error deregistering: " + e.getMessage());
                }
            }

            if (server != null) {
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

        // Connection settings
        GridPane connectionGrid = new GridPane();
        connectionGrid.setHgap(10);
        connectionGrid.setVgap(5);

        bankHostField = new TextField("localhost");
        bankPortField = new TextField("5000");
        auctionPortField = new TextField("0"); // 0 = auto-assign

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

    private VBox createCenterSection() {
        VBox centerSection = new VBox(10);
        centerSection.setPadding(new Insets(10));

        Label itemsLabel = new Label("Auction Items");
        itemsLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        // Items table
        itemsList = FXCollections.observableArrayList();
        itemsTable = new TableView<>(itemsList);

        TableColumn<ItemDisplay, Integer> itemIdCol = new TableColumn<>("Item ID");
        itemIdCol.setCellValueFactory(new PropertyValueFactory<>("itemId"));
        itemIdCol.setPrefWidth(80);

        TableColumn<ItemDisplay, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(250);

        TableColumn<ItemDisplay, Double> minBidCol = new TableColumn<>("Min Bid");
        minBidCol.setCellValueFactory(new PropertyValueFactory<>("minimumBid"));
        minBidCol.setPrefWidth(100);

        TableColumn<ItemDisplay, Double> currentBidCol = new TableColumn<>("Current Bid");
        currentBidCol.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        currentBidCol.setPrefWidth(120);

        TableColumn<ItemDisplay, Integer> bidderCol = new TableColumn<>("Current Bidder");
        bidderCol.setCellValueFactory(new PropertyValueFactory<>("currentBidder"));
        bidderCol.setPrefWidth(120);

        itemsTable.getColumns().addAll(itemIdCol, descCol, minBidCol, currentBidCol, bidderCol);

        // Item management controls
        HBox itemControls = new HBox(10);
        itemDescriptionField = new TextField();
        itemDescriptionField.setPromptText("Item description");
        itemDescriptionField.setPrefWidth(250);

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

        centerSection.getChildren().addAll(itemsLabel, itemsTable, itemControls);
        return centerSection;
    }

    private VBox createBottomSection() {
        VBox bottomSection = new VBox(5);
        bottomSection.setPadding(new Insets(10));

        Label logLabel = new Label("Server Log");
        logLabel.setStyle("-fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
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

            // Start auto-refresh
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

            auctionHouse.addNewItem();
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

        AuctionMessages.GetItemsResponse response = auctionHouse.getItems();
        if (response.success) {
            itemsList.clear();
            for (AuctionItem item : response.items) {
                itemsList.add(new ItemDisplay(item));
            }
        }
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
