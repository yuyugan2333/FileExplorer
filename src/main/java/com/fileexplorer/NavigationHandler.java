package com.fileexplorer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 导航处理类，管理历史记录和路径导航。
 */
public class NavigationHandler {
    private final Controller controller;
    private final List<Path> history = new ArrayList<>();
    private int currentIndex = -1;

    public NavigationHandler(Controller controller) {
        this.controller = controller;
    }

    public void addToHistory(Path path) {
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }
        history.add(path);
        currentIndex = history.size() - 1;
        updateNavigationButtons();
    }

    public void navigateTo(Path newPath) {
        if (newPath == null || newPath.toString().equals("此电脑")) {
            controller.loadHomePage();
            return;
        }

        try {
            if (!Files.exists(newPath)) {
                UIUtils.showAlert("错误", "路径不存在: " + newPath);
                return;
            }

            if (!Files.isDirectory(newPath)) {
                controller.fileOperationHandler.openFile(newPath);
                return;
            }

            if (!Files.isReadable(newPath)) {
                UIUtils.showAlert("错误", "没有权限访问: " + newPath);
                return;
            }

            if (!newPath.equals(controller.getCurrentPath())) {
                controller.setCurrentPath(newPath);
                addToHistory(newPath);
                controller.loadFiles(newPath);
                updatePathField();
                controller.treeViewHandler.selectInTreeView(newPath);
            }
        } catch (Exception e) {
            UIUtils.showAlert("错误", "无法访问路径: " + newPath + " - " + e.getMessage());
        }
    }

    public void goBack() {
        if (currentIndex > 0) {
            currentIndex--;
            Path path = history.get(currentIndex);
            if (path == null) {
                controller.loadHomePage();
            } else {
                controller.setCurrentPath(path);
                controller.loadFiles(path);
            }
            updatePathField();
            updateNavigationButtons();
            controller.treeViewHandler.selectInTreeView(path);
        }
    }

    public void goForward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            Path path = history.get(currentIndex);
            if (path == null) {
                controller.loadHomePage();
            } else {
                controller.setCurrentPath(path);
                controller.loadFiles(path);
            }
            updatePathField();
            updateNavigationButtons();
            controller.treeViewHandler.selectInTreeView(path);
        }
    }

    public void goUp() {
        Path current = controller.getCurrentPath();
        if (current != null && current.getParent() != null) {
            navigateTo(current.getParent());
        }
    }

    public void updateNavigationButtons() {
        controller.getBackButton().setDisable(currentIndex <= 0);
        controller.getForwardButton().setDisable(currentIndex >= history.size() - 1);
        controller.getUpButton().setDisable(controller.getCurrentPath() == null);
    }

    public void updatePathField() {
        controller.getPathField().setText(controller.getCurrentPath() == null ? "此电脑" : controller.getCurrentPath().toAbsolutePath().toString());
    }

    public void handlePathInput() {
        String pathText = controller.getPathField().getText().trim();
        if (pathText.equals("此电脑") || pathText.equalsIgnoreCase("Computer")) {
            controller.loadHomePage();
        } else if (!pathText.isEmpty()) {
            Path newPath = Paths.get(pathText);
            if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                navigateTo(newPath);
            } else {
                UIUtils.showAlert("错误", "路径不存在或不是目录: " + pathText);
                updatePathField();
            }
        }
    }

    public List<Path> getHistory() {
        return history;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }
}