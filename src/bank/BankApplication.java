package bank;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
public class BankApplication extends Application {

    private BankServer server;
    private TextArea logArea;
    private Label statusLabel;

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

        // FIXED: Removed auto-start - let user start manually
        // startServer();

        primaryStage.setOnCloseRequest(event -> {
            if (server != null) {
                server.stop();
            }
        });

        primaryStage.show();
    }

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

        // FIXED: Correct initial button states
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

    private VBox createBottomPanel() {
        VBox vbox = new VBox(5);
        vbox.setPadding(new Insets(10));

        statusLabel = new Label("Status: Not Running");
        statusLabel.setStyle("-fx-font-size: 12;");

        vbox.getChildren().add(statusLabel);

        return vbox;
    }

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

    private void updateStatus(String status) {
        javafx.application.Platform.runLater(() -> statusLabel.setText("Status: " + status));
    }

    public static void main(String[] args) {
        launch(args);
    }
}