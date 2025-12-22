package com.fileexplorer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FileExplorerController {
    private final MainView mainView;
    private final Stage primaryStage;
    private Path currentPath;
    private final ExecutorService executor = Executors.newFixedThreadPool(4); // 多线程池
    private Task<List<FileItem>> currentLoadingTask = null;
    private final List<Path> history = new ArrayList<>();
    private int currentIndex = -1;

    public FileExplorerController(MainView mainView, Stage primaryStage) {
        this.mainView = mainView;
        this.primaryStage = primaryStage;
        initialize();
    }

    private void initialize() {
        // 设置初始路径为用户的home目录
        currentPath = Paths.get(System.getProperty("user.home"));
        addToHistory(currentPath);

        // 加载目录树
        loadDirectoryTree();

        // 初始加载文件
        loadFiles(currentPath);

        // 更新路径显示
        updatePathField();

        // 导航按钮事件
        mainView.getBackButton().setOnAction(e -> goBack());
        mainView.getForwardButton().setOnAction(e -> goForward());
        mainView.getUpButton().setOnAction(e -> goUp());

        // 路径输入框回车事件
        mainView.getPathField().setOnAction(e -> {
            String pathText = mainView.getPathField().getText().trim();
            if (!pathText.isEmpty()) {
                Path newPath = Paths.get(pathText);
                if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                    navigateTo(newPath);
                } else {
                    showAlert("错误", "路径不存在或不是目录: " + pathText);
                    updatePathField(); // 恢复显示当前路径
                }
            }
        });

        // 监听树选择变化，刷新文件列表
        mainView.getTreeView().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                navigateTo(newVal.getValue());
            }
        });

        // 双击文件列表项打开文件
        mainView.getTableView().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                FileItem selected = mainView.getTableView().getSelectionModel().getSelectedItem();
                if (selected != null) {
                    if (selected.isDirectory()) {
                        // 如果是文件夹，切换到该目录
                        navigateTo(selected.getPath());
                    } else {
                        openFile(selected.getPath());
                    }
                }
            }
        });

        // 搜索按钮/回车事件（假设搜索框回车触发）
        mainView.getSearchField().setOnAction(e -> handleSearch(mainView.getSearchField().getText()));

        // 刷新按钮
        mainView.getRefreshButton().setOnAction(e -> {
            if (currentPath != null) {
                loadFiles(currentPath);
            }
        });

        // 模式切换按钮
        mainView.getModeButton().setOnAction(e -> {
            mainView.switchViewMode();
            if (mainView.getGridView().isVisible()) {
                loadGridView(currentPath); // 填充网格视图
            }
        });

        // 右键菜单 for 操作
        ContextMenu contextMenu = createContextMenu();
        mainView.getTableView().setContextMenu(contextMenu);

        // 同时添加网格视图的右键菜单支持
        mainView.getGridView().setOnContextMenuRequested(event -> {
            // 可以在这里实现网格视图的右键菜单
            contextMenu.show(mainView.getGridView(), event.getScreenX(), event.getScreenY());
        });

        // 为目录树也添加右键菜单
        ContextMenu treeContextMenu = createTreeContextMenu();
        mainView.getTreeView().setContextMenu(treeContextMenu);
    }

    private void loadDirectoryTree() {
        TreeItem<Path> rootItem = new TreeItem<>(Paths.get("文件系统"));  // 改为有意义的显示名称
        rootItem.setExpanded(true);

        // 获取所有根目录
        FileSystem fs = FileSystems.getDefault();
        for (Path rootDir : fs.getRootDirectories()) {
            TreeItem<Path> item = createTreeItem(rootDir);
            rootItem.getChildren().add(item);
        }

        mainView.getTreeView().setRoot(rootItem);

        // 懒加载：展开时加载子目录
        mainView.getTreeView().setCellFactory(new Callback<>() {
            @Override
            public TreeCell<Path> call(TreeView<Path> param) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(Path item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                        } else {
                            setText(item.getFileName() != null ? item.getFileName().toString() : item.toString());
                        }
                    }
                };
            }
        });

        // 展开事件加载子项
        rootItem.getChildren().forEach(this::addExpansionListener);
    }

    private ContextMenu createTreeContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem newFolder = new MenuItem("新建文件夹");
        MenuItem refresh = new MenuItem("刷新");
        MenuItem properties = new MenuItem("属性");

        newFolder.setOnAction(e -> {
            TreeItem<Path> selected = mainView.getTreeView().getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null) {
                // 获取选中的目录路径
                Path targetPath = selected.getValue();

                TextInputDialog dialog = new TextInputDialog("新建文件夹");
                dialog.setTitle("新建文件夹");
                dialog.setHeaderText("在 " + targetPath.getFileName() + " 中创建新文件夹");
                dialog.setContentText("请输入文件夹名称:");

                dialog.showAndWait().ifPresent(folderName -> {
                    if (!folderName.trim().isEmpty()) {
                        try {
                            Path newFolderPath = targetPath.resolve(folderName.trim());
                            if (Files.exists(newFolderPath)) {
                                showAlert("错误", "文件夹已存在: " + folderName);
                                return;
                            }
                            Files.createDirectory(newFolderPath);
                            // 刷新树视图
                            loadDirectoryTree();
                            // 如果当前在此目录，也刷新文件列表
                            if (targetPath.equals(currentPath)) {
                                loadFiles(currentPath);
                            }
                        } catch (IOException ex) {
                            showAlert("错误", "创建文件夹失败: " + ex.getMessage());
                        }
                    }
                });
            }
        });

        refresh.setOnAction(e -> {
            // 重新加载目录树
            loadDirectoryTree();
        });

        properties.setOnAction(e -> {
            TreeItem<Path> selected = mainView.getTreeView().getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null) {
                showFileDetails(selected.getValue());
            }
        });

        menu.getItems().addAll(newFolder, refresh, new SeparatorMenuItem(), properties);
        return menu;
    }

    private TreeItem<Path> createTreeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        item.setExpanded(false);
        // 添加 dummy 子项 for 懒加载
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
                    showAlert("错误", "无法加载目录: " + e.getMessage());
                }
            }
        });
    }

    private void loadFiles(Path dir) {
        // 取消上一个未完成的任务
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        currentLoadingTask = new Task<>() {
            @Override
            protected List<FileItem> call() throws Exception {
                // 检查是否被取消
                if (isCancelled()) {
                    return new ArrayList<>();
                }

                List<FileItem> items = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        // 再次检查是否被取消
                        if (isCancelled()) {
                            return items;
                        }
                        items.add(new FileItem(entry));
                    }
                }
                return items;
            }
        };

        currentLoadingTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                // 只有当任务成功完成时才更新UI
                mainView.getTableView().getItems().setAll(currentLoadingTask.getValue());
                if (mainView.getGridView().isVisible()) {
                    loadGridView(dir);
                }
            });
        });

        currentLoadingTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("错误", "加载文件失败: " + currentLoadingTask.getException().getMessage());
            });
        });

        currentLoadingTask.setOnCancelled(e -> {
            // 任务被取消，不更新UI
            System.out.println("文件加载任务被取消");
        });

        executor.submit(currentLoadingTask);
    }

    private void loadGridView(Path dir) {
        mainView.getGridView().getChildren().clear();
        int col = 0, row = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                // 创建局部final变量，确保每个lambda捕获不同的值
                final Path currentEntry = entry;
                Button iconButton = new Button(entry.getFileName().toString());

                // 添加图标等（可选，后续可以优化）
                // iconButton.setGraphic(...);

                // 关键修复：使用局部final变量
                iconButton.setOnAction(e -> {
                    if (Files.isDirectory(currentEntry)) {
                        // 如果是文件夹，导航进入
                        navigateTo(currentEntry);
                    } else {
                        // 如果是文件，用系统程序打开
                        openFile(currentEntry);
                    }
                });

                mainView.getGridView().add(iconButton, col++, row);
                if (col > 4) { // 每行5个
                    col = 0;
                    row++;
                }
            }
        } catch (IOException e) {
            showAlert("错误", "加载网格视图失败: " + e.getMessage());
        }
    }

    private void handleSearch(String pattern) {
        if (currentPath == null) return;

        Task<List<FileItem>> searchTask = new SearchTask(currentPath, pattern);
        searchTask.setOnSucceeded(e -> Platform.runLater(() -> mainView.getTableView().getItems().setAll(searchTask.getValue())));
        searchTask.setOnFailed(e -> showAlert("错误", "搜索失败: " + searchTask.getException().getMessage()));
        executor.submit(searchTask);
    }

    private void openFile(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            showAlert("错误", "无法打开文件: " + e.getMessage());
        }
    }

    private void showFileDetails(Path path) {
        DetailsDialog dialog = new DetailsDialog(path);
        dialog.showAndWait();
    }

    // 文件操作方法
    public void copyFile(Path source, Path target) {
        try {
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            loadFiles(currentPath); // 刷新
        } catch (IOException e) {
            showAlert("错误", "复制失败: " + e.getMessage());
        }
    }

    public void moveFile(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "移动失败: " + e.getMessage());
        }
    }

    public void deleteFile(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(path);
            }
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "删除失败: " + e.getMessage());
        }
    }

    public void renameFile(Path path, String newName) {
        try {
            // 检查新名称是否有效
            if (newName == null || newName.trim().isEmpty() || newName.contains("/") || newName.contains("\\")) {
                showAlert("错误", "无效的文件名");
                return;
            }

            Path target = path.resolveSibling(newName);
            if (Files.exists(target)) {
                if (!showConfirmDialog("确认覆盖", "文件 " + newName + " 已存在，是否覆盖？")) {
                    return;
                }
            }

            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "重命名失败: " + e.getMessage());
        }
    }

    public void createFolder(String name) {
        if (currentPath == null || name == null || name.trim().isEmpty()) return;

        try {
            Path newFolder = currentPath.resolve(name.trim());
            if (Files.exists(newFolder)) {
                showAlert("错误", "文件夹已存在: " + name);
                return;
            }
            Files.createDirectory(newFolder);
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "创建文件夹失败: " + e.getMessage());
        }
    }

    // 右键菜单
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();

        // 创建菜单项
        MenuItem copy = new MenuItem("复制");
        MenuItem cut = new MenuItem("剪切");
        MenuItem paste = new MenuItem("粘贴");
        MenuItem delete = new MenuItem("删除");
        MenuItem rename = new MenuItem("重命名");
        MenuItem newFolder = new MenuItem("新建文件夹");
        MenuItem properties = new MenuItem("属性");
        SeparatorMenuItem separator1 = new SeparatorMenuItem();
        SeparatorMenuItem separator2 = new SeparatorMenuItem();

        // 定义剪贴板（用于复制/移动操作）
        class Clipboard {
            List<Path> files = new ArrayList<>();
            boolean isCutOperation = false; // true表示剪切，false表示复制
        }
        Clipboard clipboard = new Clipboard();

        // 复制功能
        copy.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (!selectedItems.isEmpty()) {
                clipboard.files.clear();
                selectedItems.forEach(item -> clipboard.files.add(item.getPath()));
                clipboard.isCutOperation = false;
                showInfo("已复制 " + selectedItems.size() + " 个项目");
            }
        });

        // 剪切功能
        cut.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (!selectedItems.isEmpty()) {
                clipboard.files.clear();
                selectedItems.forEach(item -> clipboard.files.add(item.getPath()));
                clipboard.isCutOperation = true;
                showInfo("已剪切 " + selectedItems.size() + " 个项目");
            }
        });

        // 粘贴功能
        paste.setOnAction(e -> {
            if (clipboard.files.isEmpty() || currentPath == null) {
                return;
            }

            for (Path source : clipboard.files) {
                Path target = currentPath.resolve(source.getFileName());

                // 处理同名文件
                if (Files.exists(target)) {
                    if (!showConfirmDialog("文件已存在", "文件 " + target.getFileName() + " 已存在，是否覆盖？")) {
                        continue;
                    }
                }

                try {
                    if (clipboard.isCutOperation) {
                        // 移动文件
                        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // 复制文件
                        if (Files.isDirectory(source)) {
                            copyDirectory(source, target);
                        } else {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException ex) {
                    showAlert("错误", "操作失败: " + ex.getMessage());
                }
            }

            // 如果是剪切操作，清空剪贴板
            if (clipboard.isCutOperation) {
                clipboard.files.clear();
            }

            // 刷新文件列表
            loadFiles(currentPath);
        });

        // 删除功能
        delete.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (selectedItems.isEmpty()) {
                return;
            }

            // 确认对话框
            String message = "确定要删除选中的 " + selectedItems.size() + " 个项目吗？";
            if (selectedItems.size() == 1) {
                message = "确定要删除 \"" + selectedItems.get(0).getName() + "\" 吗？";
            }

            if (showConfirmDialog("确认删除", message)) {
                for (FileItem item : selectedItems) {
                    deleteFile(item.getPath());
                }
                showInfo("已删除 " + selectedItems.size() + " 个项目");
            }
        });

        // 重命名功能
        rename.setOnAction(e -> {
            FileItem selected = mainView.getSelectedFileItem();
            if (selected == null) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(selected.getName());
            dialog.setTitle("重命名");
            dialog.setHeaderText("重命名文件");
            dialog.setContentText("请输入新名称:");

            dialog.showAndWait().ifPresent(newName -> {
                if (!newName.trim().isEmpty() && !newName.equals(selected.getName())) {
                    renameFile(selected.getPath(), newName.trim());
                }
            });
        });

        // 新建文件夹功能
        newFolder.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("新建文件夹");
            dialog.setTitle("新建文件夹");
            dialog.setHeaderText("创建新文件夹");
            dialog.setContentText("请输入文件夹名称:");

            dialog.showAndWait().ifPresent(folderName -> {
                if (!folderName.trim().isEmpty()) {
                    createFolder(folderName.trim());
                }
            });
        });

        // 属性功能
        properties.setOnAction(e -> {
            FileItem selected = mainView.getSelectedFileItem();
            if (selected != null) {
                showFileDetails(selected.getPath());
            }
        });

        // 将菜单项添加到右键菜单
        menu.getItems().addAll(copy, cut, paste, separator1, delete, rename, newFolder, separator2, properties);

        return menu;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // 显示信息对话框
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("信息");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // 显示确认对话框
    private boolean showConfirmDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showAlert(String title, String message) {
        showAlert(title, message, Alert.AlertType.ERROR);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // 关闭时 shutdown executor
    public void shutdown() {
        executor.shutdown();
    }

    // 添加导航相关方法
    private void addToHistory(Path path) {
        // 如果当前不是历史记录的最后一项，则移除后面的所有项
        if (currentIndex < history.size() - 1) {
            history.subList(currentIndex + 1, history.size()).clear();
        }
        history.add(path);
        currentIndex = history.size() - 1;
        updateNavigationButtons();
    }

    private void navigateTo(Path newPath) {
        if (!newPath.equals(currentPath)) {
            currentPath = newPath;
            addToHistory(currentPath);
            loadFiles(currentPath);
            updatePathField();

            // 更新树视图选择
            selectInTreeView(currentPath);
        }
    }

    private void goBack() {
        if (currentIndex > 0) {
            currentIndex--;
            currentPath = history.get(currentIndex);
            loadFiles(currentPath);
            updatePathField();
            updateNavigationButtons();
            selectInTreeView(currentPath);
        }
    }

    private void goForward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            currentPath = history.get(currentIndex);
            loadFiles(currentPath);
            updatePathField();
            updateNavigationButtons();
            selectInTreeView(currentPath);
        }
    }

    private void goUp() {
        if (currentPath != null && currentPath.getParent() != null) {
            navigateTo(currentPath.getParent());
        }
    }

    private void updateNavigationButtons() {
        mainView.getBackButton().setDisable(currentIndex <= 0);
        mainView.getForwardButton().setDisable(currentIndex >= history.size() - 1);
        mainView.getUpButton().setDisable(currentPath == null || currentPath.getParent() == null);
    }

    private void updatePathField() {
        if (currentPath != null) {
            mainView.getPathField().setText(currentPath.toAbsolutePath().toString());
        }
    }

    // 在树视图中选择对应路径的节点
    private void selectInTreeView(Path path) {
        // 查找树视图中对应的节点并选中
        // 这是一个简化版本，实际实现可能需要递归查找
        TreeItem<Path> root = mainView.getTreeView().getRoot();
        if (root != null) {
            // 递归查找路径对应的节点
            TreeItem<Path> found = findTreeItem(root, path);
            if (found != null) {
                mainView.getTreeView().getSelectionModel().select(found);
                // 展开到该节点
                TreeItem<Path> parent = found.getParent();
                while (parent != null) {
                    parent.setExpanded(true);
                    parent = parent.getParent();
                }
            }
        }
    }

    // 递归查找树节点
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
}

// SearchTask **暂时**
class SearchTask extends Task<List<FileItem>> {
    private final Path startDir;
    private final String pattern;

    public SearchTask(Path startDir, String pattern) {
        this.startDir = startDir;
        this.pattern = pattern;
    }

    @Override
    protected List<FileItem> call() throws Exception {
        List<FileItem> results = new ArrayList<>();
        Pattern regex = Pattern.compile(globToRegex(pattern));
        try (Stream<Path> stream = Files.walk(startDir)) {
            stream.filter(path -> regex.matcher(path.getFileName().toString()).matches())
                    .map(FileItem::new)
                    .forEach(results::add);
        }
        return results;
    }

    private String globToRegex(String glob) {
        return glob.replace("*", ".*").replace("?", ".");
    }
}