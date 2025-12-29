package com.fileexplorer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    private FileExplorerController controller;

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();
        controller = new FileExplorerController(mainView, primaryStage);

        Scene scene = new Scene(mainView.getRoot(), 1200, 800);

        // 加载Windows风格样式表
        scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/windows-style.css").toExternalForm());

        // 也可以保留原来的样式表作为备用
        // scene.getStylesheets().add(getClass().getResource("/com/fileexplorer/styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("文件资源管理器");

        // 设置窗口图标
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/com/fileexplorer/icons/folder.png")));
        } catch (Exception e) {
            // 忽略图标加载失败
        }

        // 添加窗口关闭事件处理
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
        });

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}