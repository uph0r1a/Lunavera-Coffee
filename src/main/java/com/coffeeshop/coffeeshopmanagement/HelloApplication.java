package com.coffeeshop.coffeeshopmanagement;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    @Override
    public void init() {
        // Runs on the JavaFX launcher thread before start(), so the login screen never shows
        // until the schema exists and (on a first run) the default admin account is seeded.
        DatabaseConfig.initialize();
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                HelloApplication.class.getResource("/fxml/dangnhap.fxml")
        );

        Scene scene = new Scene(loader.load());

        stage.setTitle("Lunavera Coffee");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}