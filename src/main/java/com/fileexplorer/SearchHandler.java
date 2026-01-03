package com.fileexplorer;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 搜索处理类，管理搜索事件和任务。
 */
public class SearchHandler {
    private final Controller controller;
    private Timer searchTimer;

    public SearchHandler(Controller controller) {
        this.controller = controller;
    }

    public void bindSearchEvents() {
        TextField searchField = controller.getSearchField();

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (searchTimer != null) {
                searchTimer.cancel();
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> handleSearch(newValue));
                }
            }, 500);
        });

        searchField.setOnAction(e -> handleSearch(searchField.getText()));

        Button clearSearchButton = new Button("×");
        clearSearchButton.setTooltip(new Tooltip("清除搜索"));
        clearSearchButton.setOnAction(e -> {
            searchField.clear();
            controller.refresh();
        });

        ToolBar toolBar = controller.getToolBar();
        int searchIndex = toolBar.getItems().indexOf(searchField);
        if (searchIndex != -1) {
            toolBar.getItems().add(searchIndex + 1, clearSearchButton);
        } else {
            System.err.println("工具栏未找到搜索框");
        }
    }

    private void handleSearch(String pattern) {
        if (pattern.trim().isEmpty()) {
            controller.refresh();
            return;
        }

        String mode = controller.getSearchModeComboBox().getValue();

        List<Path> searchRoots = new ArrayList<>();
        if (controller.getCurrentPath() != null) {
            searchRoots.add(controller.getCurrentPath());
        } else {
            FileSystem fs = FileSystems.getDefault();
            for (Path root : fs.getRootDirectories()) {
                if (Files.exists(root) && Files.isReadable(root)) {
                    searchRoots.add(root);
                }
            }
        }

        SearchTask searchTask = new SearchTask(searchRoots, pattern, mode);
        searchTask.setOnSucceeded(e -> Platform.runLater(() -> {
            controller.getTableView().getItems().setAll(searchTask.getValue());
            if (controller.getGridView().isVisible()) {
                updateGridViewWithSearchResults(searchTask.getValue());
            }
        }));
        searchTask.setOnFailed(e -> UIUtils.showAlert("错误", "搜索失败: " + searchTask.getException().getMessage()));

        controller.getThreadPool().submitBackgroundTask(searchTask);
    }

    private void updateGridViewWithSearchResults(List<FileItem> searchResults) {
        if (!controller.getGridView().isVisible()) {
            return;
        }

        controller.getGridView().getChildren().clear();
        controller.getSelectedItemsInGrid().clear();

        for (FileItem item : searchResults) {
            Button button = controller.addGridItem(item);
            if (item.isDirectory()) {
                button.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #bbdefb;");
            }
            button.setOnMouseClicked(event -> controller.fileOperationHandler.handleGridItemClick(event, button, item));
            controller.getGridView().getChildren().add(button);
        }
    }
}