// src/main/java/com/fileexplorer/Main.java
package com.fileexplorer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * 应用程序入口类，负责启动JavaFX界面。
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fileexplorer/main-view.fxml"));
        Parent root = loader.load();
        Controller controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        Scene scene = new Scene(root, 1200, 800);

        // 添加样式表
        scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/windows-style.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("文件资源管理器");

        // 设置窗口图标
        try {
            Image icon = new Image(getClass().getResourceAsStream("/com/fileexplorer/icons/folder.png"));
            if (icon != null) {
                primaryStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            System.err.println("图标加载失败: " + e.getMessage());
        }

        // 添加关闭事件
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
        });

        primaryStage.show();
    }
}