module com.coffeeshop.coffeeshopmanagement {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires kotlin.stdlib;

    exports com.coffeeshop.coffeeshopmanagement;
    exports org.example.controller;

    opens com.coffeeshop.coffeeshopmanagement to javafx.fxml;
    opens org.example.controller to javafx.fxml;
}