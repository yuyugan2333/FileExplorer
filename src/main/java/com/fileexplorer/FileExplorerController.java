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
                Button iconButton = new Button(entry.getFileName().toString());
                // 添加图标等（可选，后续可以优化）
                // iconButton.setGraphic(...);

                // 关键修改：判断是文件夹还是文件
                iconButton.setOnAction(e -> {
                    if (Files.isDirectory(entry)) {
                        // 如果是文件夹，导航进入
                        navigateTo(entry);
                    } else {
                        // 如果是文件，用系统程序打开
                        openFile(entry);
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
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
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
            Files.move(path, path.resolveSibling(newName));
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "重命名失败: " + e.getMessage());
        }
    }

    public void createFolder(String name) {
        if (currentPath == null) return;
        try {
            Files.createDirectory(currentPath.resolve(name));
            loadFiles(currentPath);
        } catch (IOException e) {
            showAlert("错误", "创建文件夹失败: " + e.getMessage());
        }
    }

    // 右键菜单
    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem copy = new MenuItem("复制");
        MenuItem move = new MenuItem("移动");
        MenuItem delete = new MenuItem("删除");
        MenuItem rename = new MenuItem("重命名");
        MenuItem details = new MenuItem("属性");

        copy.setOnAction(e -> {
            FileItem selected = mainView.getTableView().getSelectionModel().getSelectedItem();
            if (selected != null) {
                // 简化：假设目标为当前目录子文件夹，实际需选择目标
                copyFile(selected.getPath(), currentPath.resolve("copy_" + selected.getName()));
            }
        });

        // 类似为其他项添加逻辑（move, delete, rename, details）

        menu.getItems().addAll(copy, move, delete, rename, details);
        return menu;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
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