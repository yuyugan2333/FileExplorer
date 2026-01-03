package com.fileexplorer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeItem;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 目录树视图处理类，管理树加载和事件。
 */
public class TreeViewHandler {
    private final Controller controller;

    public TreeViewHandler(Controller controller) {
        this.controller = controller;
    }

    public void loadDirectoryTree() {
        TreeItem<Path> rootItem = controller.getTreeView().getRoot();
        if (rootItem == null) {
            return;
        }

        rootItem.getChildren().clear();

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                FileSystem fs = FileSystems.getDefault();
                List<Path> roots = new ArrayList<>();
                for (Path rootDir : fs.getRootDirectories()) {
                    roots.add(rootDir);
                }

                Platform.runLater(() -> {
                    for (Path rootDir : roots) {
                        try {
                            if (Files.exists(rootDir) && Files.isReadable(rootDir)) {
                                TreeItem<Path> item = createTreeItem(rootDir);
                                rootItem.getChildren().add(item);
                            }
                        } catch (SecurityException e) {
                            System.err.println("没有权限访问: " + rootDir);
                        }
                    }
                });
                return null;
            }
        };

        loadTask.setOnFailed(e -> UIUtils.showAlert("警告", "部分磁盘驱动器无法加载: " + e.getSource().getException().getMessage()));

        controller.getThreadPool().submitBackgroundTask(loadTask);
    }

    public ContextMenu createTreeContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem newFolder = new MenuItem("新建文件夹");
        newFolder.setOnAction(e -> {
            TreeItem<Path> selected = controller.getTreeView().getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null) {
                Path targetPath = selected.getValue();
                UIUtils.showTextInputDialog("新建文件夹", "在 " + targetPath.getFileName() + " 中创建新文件夹", "请输入文件夹名称:", "新建文件夹").ifPresent(folderName -> {
                    if (!folderName.trim().isEmpty()) {
                        try {
                            Path newFolderPath = targetPath.resolve(folderName.trim());
                            if (Files.exists(newFolderPath)) {
                                UIUtils.showAlert("错误", "文件夹已存在: " + folderName);
                                return;
                            }
                            Files.createDirectory(newFolderPath);
                            loadDirectoryTree();
                            if (targetPath.equals(controller.getCurrentPath())) {
                                controller.loadFiles(controller.getCurrentPath());
                            }
                        } catch (IOException ex) {
                            UIUtils.showAlert("错误", "创建文件夹失败: " + ex.getMessage());
                        }
                    }
                });
            }
        });

        MenuItem refresh = new MenuItem("刷新");
        refresh.setOnAction(e -> loadDirectoryTree());

        MenuItem properties = new MenuItem("属性");
        properties.setOnAction(e -> {
            TreeItem<Path> selected = controller.getTreeView().getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null) {
                controller.fileOperationHandler.showFileDetails(selected.getValue());
            }
        });

        menu.getItems().addAll(newFolder, refresh, new SeparatorMenuItem(), properties);
        return menu;
    }

    public TreeItem<Path> createTreeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        item.setExpanded(false);
        item.getChildren().add(new TreeItem<>(null));
        addExpansionListener(item);
        return item;
    }

    private void addExpansionListener(TreeItem<Path> item) {
        item.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
            if (isNowExpanded && !item.getChildren().isEmpty() && item.getChildren().get(0).getValue() == null) {
                item.getChildren().clear();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(item.getValue())) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            TreeItem<Path> child = createTreeItem(entry);
                            item.getChildren().add(child);
                        }
                    }
                } catch (IOException e) {
                    UIUtils.showAlert("错误", "无法加载目录: " + e.getMessage());
                }
            }
        });
    }

    public void selectInTreeView(Path path) {
        if (path == null) {
            TreeItem<Path> root = controller.getTreeView().getRoot();
            if (root != null) {
                controller.getTreeView().getSelectionModel().select(root);
            }
            return;
        }

        TreeItem<Path> root = controller.getTreeView().getRoot();
        if (root != null) {
            TreeItem<Path> found = findTreeItem(root, path);
            if (found != null) {
                controller.getTreeView().getSelectionModel().select(found);
                TreeItem<Path> parent = found.getParent();
                while (parent != null && parent != root) {
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
            }
        }
    }

    private TreeItem<Path> findTreeItem(TreeItem<Path> item, Path targetPath) {
        if (item.getValue() != null && item.getValue().equals(targetPath)) {
            return item;
        }

        for (TreeItem<Path> child : item.getChildren()) {
            TreeItem<Path> found = findTreeItem(child, targetPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public void selectRootInTree() {
        Platform.runLater(() -> {
            TreeItem<Path> root = controller.getTreeView().getRoot();
            if (root != null) {
                controller.getTreeView().getSelectionModel().select(root);
            }
        });
    }
}