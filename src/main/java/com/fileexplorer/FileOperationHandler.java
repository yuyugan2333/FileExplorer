package com.fileexplorer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件操作处理类，管理复制、删除、重命名等操作。
 */
public class FileOperationHandler {
    private final FileExplorerController controller;
    private final MainView mainView;

    public FileOperationHandler(FileExplorerController controller, MainView mainView) {
        this.controller = controller;
        this.mainView = mainView;
    }

    public void initializeClipboardListener() {
        ClipboardManager clipboard = controller.getClipboardManager();
        clipboard.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> Platform.runLater(() -> {
            if (isEmpty) {
                mainView.getStatusLabel().setText("就绪");
            } else {
                String operation = clipboard.isCutOperation() ? "剪切" : "复制";
                mainView.getStatusLabel().setText(String.format("%s %d 个项目到剪贴板", operation, clipboard.getItemCount()));
            }
        }));

        clipboard.isCutOperationProperty().addListener((obs, oldValue, newValue) -> Platform.runLater(() -> {
            if (!clipboard.isEmpty()) {
                String operation = newValue ? "剪切" : "复制";
                mainView.getStatusLabel().setText(String.format("%s %d 个项目到剪贴板", operation, clipboard.getItemCount()));
            }
        }));

        clipboard.itemCountProperty().addListener((obs, oldCount, newCount) -> Platform.runLater(() -> {
            if (newCount.intValue() > 0) {
                String operation = clipboard.isCutOperation() ? "剪切" : "复制";
                mainView.getStatusLabel().setText(String.format("%s %d 个项目到剪贴板", operation, newCount));
            }
        }));
    }

    public void executeFileOperation(FileOperationTask.OperationType type, Path source, Path target) {
        if (controller.currentFileOperationTask != null && controller.currentFileOperationTask.isRunning()) {
            if (!UIUtils.showConfirmDialog("确认", "当前有文件操作正在进行，是否继续？")) {
                return;
            }
        }

        FileOperationTask task = new FileOperationTask(type, source, target);
        task.setOwnerStage(controller.getPrimaryStage());
        controller.currentFileOperationTask = task;

        Dialog<Void> progressDialog = task.createProgressDialog();

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressDialog.close();
            controller.loadFiles(controller.getCurrentPath());
        }));

        task.setOnCancelled(e -> Platform.runLater(progressDialog::close));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressDialog.close();
        }));

        controller.getThreadPool().submitFileOperation(task);

        Platform.runLater(progressDialog::show);
    }

    public void executeBatchOperation(BatchFileOperationTask.OperationType type, List<Path> sourcePaths, Path targetDir) {
        BatchFileOperationTask task = new BatchFileOperationTask(type, sourcePaths, targetDir, controller.getPrimaryStage());

        Dialog<Void> progressDialog = task.createProgressDialog();

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            progressDialog.close();
            controller.loadFiles(controller.getCurrentPath());
            if (type == BatchFileOperationTask.OperationType.DELETE && controller.getCurrentPath() != null && Files.exists(controller.getCurrentPath())) {
                controller.loadFiles(controller.getCurrentPath());
            }
        }));

        task.setOnCancelled(e -> Platform.runLater(progressDialog::close));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressDialog.close();
            controller.loadFiles(controller.getCurrentPath());
        }));

        controller.getThreadPool().submitBackgroundTask(task);

        Platform.runLater(progressDialog::show);
    }

    public ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem copy = new MenuItem("复制");
        copy.setOnAction(e -> copySelected());

        MenuItem cut = new MenuItem("剪切");
        cut.setOnAction(e -> cutSelected());

        MenuItem paste = new MenuItem("粘贴");
        paste.setOnAction(e -> paste());

        MenuItem delete = new MenuItem("删除");
        delete.setOnAction(e -> deleteSelected());

        MenuItem rename = new MenuItem("重命名");
        rename.setOnAction(e -> renameSelected());

        MenuItem newFolder = new MenuItem("新建文件夹");
        newFolder.setOnAction(e -> createNewFolder());

        MenuItem properties = new MenuItem("属性");
        properties.setOnAction(e -> showProperties());

        paste.disableProperty().bind(Bindings.createBooleanBinding(() -> controller.getClipboardManager().isEmpty() || controller.getCurrentPath() == null, mainView.getPathField().textProperty()));

        menu.getItems().addAll(copy, cut, paste, new SeparatorMenuItem(), delete, rename, newFolder, new SeparatorMenuItem(), properties);
        return menu;
    }

    public void handleGridItemClick(MouseEvent event, Button button, FileItem item) {
        if (event.getButton() == MouseButton.PRIMARY) {
            if (event.getClickCount() == 2) {
                if (Files.isDirectory(item.getPath())) {
                    controller.navigationHandler.navigateTo(item.getPath());
                } else {
                    openFile(item.getPath());
                }
            } else {
                Set<FileItem> selected = mainView.getSelectedItemsInGrid();
                if (event.isControlDown()) {
                    if (selected.contains(item)) {
                        selected.remove(item);
                        button.getStyleClass().remove("selected");
                    } else {
                        selected.add(item);
                        button.getStyleClass().add("selected");
                    }
                } else if (event.isShiftDown()) {
                    // 范围选择逻辑（可选实现）
                } else {
                    for (Node node : mainView.getGridView().getChildren()) {
                        if (node instanceof Button) {
                            node.getStyleClass().remove("selected");
                        }
                    }
                    selected.clear();
                    selected.add(item);
                    button.getStyleClass().add("selected");
                }
            }
        }
    }

    public void openFile(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            UIUtils.showAlert("错误", "无法打开文件: " + e.getMessage());
        }
    }

    public void showFileDetails(Path path) {
        if (isRootDrive(path)) {
            showDriveDetails(path);
        } else {
            DetailsDialog dialog = new DetailsDialog(path);
            dialog.showAndWait();
        }
    }

    private boolean isRootDrive(Path path) {
        try {
            return path.getParent() == null && Files.getFileStore(path) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private void showDriveDetails(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            long total = store.getTotalSpace();
            long used = total - store.getUsableSpace();
            long free = store.getUsableSpace();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("驱动器属性");
            alert.setHeaderText(path.toString() + " (" + store.name() + ")");
            alert.setContentText("类型: " + store.type() + "\n" +
                    "总容量: " + FileUtils.formatSize(total) + "\n" +
                    "已用: " + FileUtils.formatSize(used) + "\n" +
                    "可用: " + FileUtils.formatSize(free));
            alert.showAndWait();
        } catch (IOException e) {
            UIUtils.showAlert("错误", "无法获取驱动器信息: " + e.getMessage());
        }
    }

    public void copyFile(Path source, Path target) {
        executeFileOperation(FileOperationTask.OperationType.COPY, source, target);
    }

    public void moveFile(Path source, Path target) {
        executeFileOperation(FileOperationTask.OperationType.MOVE, source, target);
    }

    public void deleteFile(Path path) {
        executeFileOperation(FileOperationTask.OperationType.DELETE, path, null);
    }

    public void extractFile(Path source, Path target) {
        executeFileOperation(FileOperationTask.OperationType.EXTRACT, source, target);
    }

    public void renameFile(Path path, String newName) {
        try {
            if (newName == null || newName.trim().isEmpty() || newName.contains("/") || newName.contains("\\")) {
                UIUtils.showAlert("错误", "无效的文件名");
                return;
            }

            Path target = path.resolveSibling(newName);
            if (Files.exists(target)) {
                if (!UIUtils.showConfirmDialog("确认覆盖", "文件 " + newName + " 已存在，是否覆盖？")) {
                    return;
                }
            }

            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
            controller.loadFiles(controller.getCurrentPath());
        } catch (IOException e) {
            UIUtils.showAlert("错误", "重命名失败: " + e.getMessage());
        }
    }

    public void createFolder(String name) {
        if (controller.getCurrentPath() == null || name == null || name.trim().isEmpty()) return;

        try {
            Path newFolder = controller.getCurrentPath().resolve(name.trim());
            if (Files.exists(newFolder)) {
                UIUtils.showAlert("错误", "文件夹已存在: " + name);
                return;
            }
            Files.createDirectory(newFolder);
            controller.loadFiles(controller.getCurrentPath());
        } catch (IOException e) {
            UIUtils.showAlert("错误", "创建文件夹失败: " + e.getMessage());
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void copySelected() {
        List<FileItem> selectedItems = mainView.getSelectedFileItems();
        if (!selectedItems.isEmpty()) {
            List<Path> paths = selectedItems.stream().map(FileItem::getPath).collect(Collectors.toList());
            controller.getClipboardManager().setClipboard(paths, false);
            UIUtils.showInfo("已复制 " + paths.size() + " 个项目到剪贴板");
        }
    }

    public void cutSelected() {
        List<FileItem> selectedItems = mainView.getSelectedFileItems();
        if (!selectedItems.isEmpty()) {
            List<Path> paths = selectedItems.stream().map(FileItem::getPath).collect(Collectors.toList());
            controller.getClipboardManager().setClipboard(paths, true);
            UIUtils.showInfo("已剪切 " + paths.size() + " 个项目到剪贴板");
        }
    }

    public void paste() {
        ClipboardManager clipboard = controller.getClipboardManager();
        if (clipboard.isEmpty() || controller.getCurrentPath() == null) {
            return;
        }

        List<Path> clipboardItems = clipboard.getClipboardItems();
        boolean isCut = clipboard.isCutOperation();

        for (Path source : clipboardItems) {
            if (controller.getCurrentPath().startsWith(source)) {
                UIUtils.showAlert("错误", "不能将文件夹复制到自身或其子文件夹中");
                return;
            }
        }

        List<Path> conflicts = new ArrayList<>();
        for (Path source : clipboardItems) {
            Path target = controller.getCurrentPath().resolve(source.getFileName());
            if (Files.exists(target)) {
                conflicts.add(source);
            }
        }

        if (!conflicts.isEmpty()) {
            if (!UIUtils.showConflictDialog(conflicts)) {
                return;
            }
        }

        BatchFileOperationTask.OperationType operationType = isCut ? BatchFileOperationTask.OperationType.MOVE : BatchFileOperationTask.OperationType.COPY;
        executeBatchOperation(operationType, clipboardItems, controller.getCurrentPath());

        if (isCut) {
            clipboard.clearClipboard();
        }
    }

    public void deleteSelected() {
        List<FileItem> selectedItems = mainView.getSelectedFileItems();
        if (selectedItems.isEmpty()) {
            return;
        }

        String message = "确定要删除选中的 " + selectedItems.size() + " 个项目吗？";
        if (selectedItems.size() == 1) {
            message = "确定要删除 \"" + selectedItems.get(0).getName() + "\" 吗？";
        }

        if (UIUtils.showConfirmDialog("确认删除", message)) {
            List<Path> paths = selectedItems.stream().map(FileItem::getPath).collect(Collectors.toList());
            executeBatchOperation(BatchFileOperationTask.OperationType.DELETE, paths, null);
        }
    }

    public void renameSelected() {
        FileItem selected = mainView.getSelectedFileItem();
        if (selected == null) {
            return;
        }

        UIUtils.showTextInputDialog("重命名", "重命名文件", "请输入新名称:", selected.getName()).ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.equals(selected.getName())) {
                renameFile(selected.getPath(), newName.trim());
            }
        });
    }

    public void createNewFolder() {
        UIUtils.showTextInputDialog("新建文件夹", "创建新文件夹", "请输入文件夹名称:", "新建文件夹").ifPresent(folderName -> {
            if (!folderName.trim().isEmpty()) {
                createFolder(folderName.trim());
            }
        });
    }

    public void showProperties() {
        FileItem selected = mainView.getSelectedFileItem();
        if (selected != null) {
            showFileDetails(selected.getPath());
        }
    }

    public void selectAll() {
        if (mainView.isGridMode) {
            for (Node node : mainView.getGridView().getChildren()) {
                if (node instanceof Button) {
                    Button button = (Button) node;
                    FileItem item = (FileItem) button.getUserData();
                    mainView.getSelectedItemsInGrid().add(item);
                    button.getStyleClass().add("selected");
                }
            }
        } else {
            mainView.getTableView().getSelectionModel().selectAll();
        }
    }

    public void deselectAllInGrid() {
        for (Node node : mainView.getGridView().getChildren()) {
            if (node instanceof Button) {
                node.getStyleClass().remove("selected");
            }
        }
        mainView.getSelectedItemsInGrid().clear();
    }
}