module com.fileexplorer {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;

    opens com.fileexplorer to javafx.fxml;
    exports com.fileexplorer;
}