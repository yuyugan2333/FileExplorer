package com.fileexpiorer;

import javafx.application.Application;
import javafx.scene.Scene;  // 需要导入Scene类
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        // 这里初始化UI
        MainView mainView = new MainView();
        FileExplorerController controller = new FileExplorerController(mainView, primaryStage);

        // 创建场景，将MainView的根布局设置为场景的根节点
        Scene scene = new Scene(mainView.getRoot(), 1200, 800);  // 设置窗口大小为1200x800

        primaryStage.setScene(scene);  // 这一步必不可少！
        primaryStage.setTitle("文件资源管理器");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}