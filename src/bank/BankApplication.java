package bank;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX application that provides a control panel for starting and stopping
 * a {@link BankServer}. It displays a log area for server messages and a
 * status bar for the current server state.
 */
public class BankApplication extends Application {

    /**
     * Underlying bank server instance that listens for client connections.
     */
    private BankServer server;

    /**
     * Text area used to display log messages from the server lifecycle.
     */
    private TextArea logArea;

    /**
     * Label showing the current server status (e.g., running, not running, error).
     */
    private Label statusLabel;

    /**
     * Entry point for the JavaFX application. Initializes the primary window,
     * sets up the layout, and wires up the close handler to cleanly stop the server.
     *
     * @param primaryStage the primary stage for this application, onto which
     *                     the application scene can be set
     * @throws Exception if an error occurs during initialization
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Distributed Auction - Bank Server");
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);

        BorderPane root = new BorderPane();

        // Top panel with controls
        VBox topPanel = createTopPanel();
        root.setTop(topPanel);

        // Center panel with log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        root.setCenter(logArea);

        // Bottom panel with status
        VBox bottomPanel = createBottomPanel();
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);

        // Removed auto-start - let user start manually
        // startServer();

        primaryStage.setOnCloseRequest(event -> {
            if (server != null) {
                server.stop();
            }
        });

        primaryStage.show();
    }

    /**
     * Creates the top control panel containing the port field and
     * buttons to start and stop the {@link BankServer}.
     *
     * @return a configured {@link VBox} containing the control widgets
     */
    private VBox createTopPanel() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label titleLabel = new Label("Bank Server Control Panel");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        HBox portPanel = new HBox(10);
        Label portLabel = new Label("Port:");
        TextField portField = new TextField("9999");
        portField.setPrefWidth(100);
        Button startButton = new Button("Start Server");
        Button stopButton = new Button("Stop Server");

        // Correct initial button states
        startButton.setDisable(false);
        stopButton.setDisable(true);
        portField.setDisable(false);

        startButton.setOnAction(event -> {
            try {
                int port = Integer.parseInt(portField.getText());
                startServerWithPort(port);
                startButton.setDisable(true);
                stopButton.setDisable(false);
                portField.setDisable(true);
            } catch (NumberFormatException e) {
                logArea.appendText("Invalid port number\n");
            }
        });

        stopButton.setOnAction(event -> {
            if (server != null) {
                server.stop();
                logArea.appendText("Server stopped\n");
            }
            startButton.setDisable(false);
            stopButton.setDisable(true);
            portField.setDisable(false);
        });

        portPanel.getChildren().addAll(portLabel, portField, startButton, stopButton);
        vbox.getChildren().addAll(titleLabel, portPanel);

        return vbox;
    }

    /**
     * Creates the bottom status panel showing the current server status.
     *
     * @return a {@link VBox} containing the status label
     */
    private VBox createBottomPanel() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));

        statusLabel = new Label("Status: Not Running");
        statusLabel.setStyle("-fx-font-size: 12;");

        vbox.getChildren().add(statusLabel);

        return vbox;
    }

    /**
     * Starts the {@link BankServer} on the given port in a background thread
     * so that the JavaFX application thread remains responsive.
     *
     * @param port the TCP port on which the server should listen
     */
    private void startServerWithPort(int port) {
        new Thread(() -> {
            try {
                server = new BankServer(port);
                logArea.appendText("Bank Server starting on port " + port + "\n");
                updateStatus("Running on port " + port);
                server.start();
            } catch (Exception e) {
                logArea.appendText("Error starting server: " + e.getMessage() + "\n");
                updateStatus("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Updates the status label text on the JavaFX Application Thread.
     *
     * @param status human-readable description of the current server state
     */
    private void updateStatus(String status) {
        javafx.application.Platform.runLater(() -> statusLabel.setText("Status: " + status));
    }

    /**
     * Main entry point when launching the application from the command line.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        launch(args);
    }
}
