package bank;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
public class BankApplication extends Application {
  private TextArea logArea;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Distributed Auction - Bank Server");
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);

        BorderPane root = new BorderPane();

        // Top panel placeholder
        VBox topPanel = new VBox();
        topPanel.setPadding(new Insets(10));
        root.setTop(topPanel);

        // Center panel with log area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        root.setCenter(logArea);

        // Bottom panel placeholder
        VBox bottomPanel = new VBox();
        bottomPanel.setPadding(new Insets(10));
        statusLabel = new Label("Status: Not Running");
        bottomPanel.getChildren().add(statusLabel);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}