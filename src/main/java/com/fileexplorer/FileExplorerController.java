package com.fileexplorer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FileExplorerController {
    private final MainView mainView;
    private final Stage primaryStage;
    private Path currentPath;
    private final ThreadPoolManager threadPool = ThreadPoolManager.getInstance(); // 使用统一的线程池管理器
    private Task<List<FileItem>> currentLoadingTask = null;
    private final List<Path> history = new ArrayList<>();
    private int currentIndex = -1;
    private Timer searchTimer;
    private ClipboardManager clipboardManager = ClipboardManager.getInstance();

    private FileOperationTask currentFileOperationTask = null;

    public FileExplorerController(MainView mainView, Stage primaryStage) {
        this.mainView = mainView;
        this.primaryStage = primaryStage;
        mainView.getRoot().getStylesheets().add(getClass().getResource("/com/fileexplorer/windows-style.css").toExternalForm());
        initialize();
        new KeyboardHandler(this, mainView);  // 初始化快捷键处理类
    }

    private void initialize() {
        // 设置初始路径为用户的home目录
        currentPath = Paths.get(System.getProperty("user.home"));
        addToHistory(currentPath);

        // 加载目录树
        loadDirectoryTree();

        // 初始加载文件 - 改为加载首页（所有驱动器）
        loadHomePage();

        // 更新路径显示
        mainView.getPathField().setText("此电脑");

        // 导航按钮事件
        mainView.getBackButton().setOnAction(e -> goBack());
        mainView.getForwardButton().setOnAction(e -> goForward());
        mainView.getUpButton().setOnAction(e -> {
            if (currentPath != null) {
                goUp();
            } else {
                // 如果在"此电脑"页面，点击上一级应该回到首页
                loadHomePage();
            }
        });

        // 路径输入框回车事件
        mainView.getPathField().setOnAction(e -> {
            String pathText = mainView.getPathField().getText().trim();
            if (pathText.equals("此电脑") || pathText.equalsIgnoreCase("Computer")) {
                loadHomePage();
            } else if (!pathText.isEmpty()) {
                Path newPath = Paths.get(pathText);
                if (Files.exists(newPath) && Files.isDirectory(newPath)) {
                    navigateTo(newPath);
                } else {
                    showAlert("错误", "路径不存在或不是目录: " + pathText);
                    updatePathField();
                }
            }
        });

        // 监听树选择变化，刷新文件列表
        mainView.getTreeView().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 处理特殊路径："此电脑"
                if (newVal.getValue().toString().equals("此电脑")) {
                    loadHomePage();
                } else {
                    navigateTo(newVal.getValue());
                }
            }
        });

        // 双击文件列表项打开文件
        mainView.getTableView().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                FileItem selected = mainView.getTableView().getSelectionModel().getSelectedItem();
                if (selected != null) {
                    if (currentPath == null) {
                        // 在首页双击
                        if (selected.getPath().toString().equals("此电脑")) {
                            // 已经在首页
                        } else if (selected.isDirectory()) {
                            navigateTo(selected.getPath());
                        } else {
                            openFile(selected.getPath());
                        }
                    } else {
                        // 在普通目录中双击
                        if (selected.isDirectory()) {
                            navigateTo(selected.getPath());
                        } else {
                            openFile(selected.getPath());
                        }
                    }
                }
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                // 点击空白处取消选择
                if (mainView.getTableView().getSelectionModel().getSelectedItem() == null) {
                    mainView.getTableView().getSelectionModel().clearSelection();
                }
            }
        });

        mainView.getGridView().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                // 点击空白处取消选择
                if (event.getTarget() == mainView.getGridView()) {
                    deselectAllInGrid();
                }
            }
        });

        // 搜索按钮/回车事件（修改为实时搜索）
        TextField searchField = mainView.getSearchField();

        // 监听搜索框文本变化（延迟搜索，避免频繁触发）
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            // 使用延迟搜索，避免每次按键都立即搜索
            if (searchTimer != null) {
                searchTimer.cancel();
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        handleSearch(newValue);
                    });
                }
            }, 500); // 延迟500毫秒
        });

        // 或者保持原有的回车搜索
        searchField.setOnAction(e -> handleSearch(searchField.getText()));

        // 添加清除搜索按钮
        Button clearSearchButton = new Button("×");
        clearSearchButton.setTooltip(new Tooltip("清除搜索"));
        clearSearchButton.setOnAction(e -> {
            searchField.clear();
            // 重新加载当前目录的文件
            if (currentPath != null) {
                loadFiles(currentPath);
            }
        });

        // 将清除按钮添加到工具栏
        Node topNode = mainView.getRoot().getTop();
        if (topNode instanceof ToolBar) {
            ToolBar toolBar = (ToolBar) topNode;
            // 找到搜索框的位置
            int searchIndex = -1;
            for (int i = 0; i < toolBar.getItems().size(); i++) {
                javafx.scene.Node node = toolBar.getItems().get(i);
                if (node == searchField) {
                    searchIndex = i;
                    break;
                }
            }

            if (searchIndex != -1) {
                toolBar.getItems().add(searchIndex + 1, clearSearchButton);
            }
        }else {
            // 处理错误情况
            System.err.println("工具栏未找到或类型不正确");
            return;
        }


        // 刷新按钮
        mainView.getRefreshButton().setOnAction(e -> {
            if (currentPath != null) {
                loadFiles(currentPath);
            }
        });

        // 模式切换按钮
        mainView.getModeButton().setOnAction(e -> {
            mainView.switchViewMode();
            if (mainView.getGridView().isVisible()) {  // 网格视图可见
                if (currentPath != null) {
                    loadGridView(currentPath);  // 使用已有的 loadGridView 方法
                } else {
                    loadHomePage();
                }
            } else {
                // 列表视图已经显示，不需要重新加载
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

        initializeClipboardListener();
    }

    private void deselectAllInGrid() {
        for (Node node : mainView.getGridView().getChildren()) {
            if (node instanceof Button) {
                node.getStyleClass().remove("selected");
            }
        }
        mainView.getSelectedItemsInGrid().clear();
    }

    /**
     * 加载首页（显示所有驱动器和特殊文件夹）
     */
    private void loadHomePage() {
        // 设置当前路径为null，表示在首页
        currentPath = null;

        // 更新历史记录
        if (currentIndex < 0 || history.isEmpty()) {
            history.add(null);
            currentIndex = 0;
        } else if (history.get(currentIndex) != null) {
            // 如果不是从首页跳转过来的，添加历史记录
            addToHistory(null);
        }

        // 更新UI状态
        updateNavigationButtons();
        mainView.getPathField().setText("此电脑");

        // 选中树视图中的"此电脑"节点
        Platform.runLater(() -> {
            TreeItem<Path> root = mainView.getTreeView().getRoot();
            if (root != null) {
                mainView.getTreeView().getSelectionModel().select(root);
            }
        });

        // 清除当前加载任务
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        // 创建首页加载任务
        currentLoadingTask = new Task<List<FileItem>>() {
            @Override
            protected List<FileItem> call() throws Exception {
                List<FileItem> items = new ArrayList<>();

                // 直接获取所有磁盘驱动器
                FileSystem fs = FileSystems.getDefault();
                for (Path root : fs.getRootDirectories()) {
                    try {
                        // 检查是否有访问权限
                        if (Files.exists(root) && Files.isReadable(root)) {
                            FileStore store = Files.getFileStore(root);
                            String displayName = root.toString();

                            FileItem driveItem = new FileItem(root) {
                                @Override
                                public String getName() {
                                    String name = super.getName();
                                    try {
                                        FileStore store = Files.getFileStore(this.getPath());
                                        String displayName = store.name();
                                        if (displayName != null && !displayName.isEmpty()) {
                                            return name + " (" + displayName + ")";
                                        }
                                    } catch (IOException e) {
                                        // 忽略异常，使用默认名称
                                    }
                                    return name;
                                }

                                @Override
                                public String getType() {
                                    try {
                                        FileStore store = Files.getFileStore(this.getPath());
                                        return store.type() + " 驱动器";
                                    } catch (IOException e) {
                                        return "本地磁盘";
                                    }
                                }

                                @Override
                                public long getSize() {
                                    try {
                                        FileStore store = Files.getFileStore(this.getPath());
                                        return store.getTotalSpace();
                                    } catch (IOException e) {
                                        return -1;
                                    }
                                }

                                @Override
                                public boolean isDirectory() {
                                    return true;
                                }

                                @Override
                                public boolean isDrive() {
                                    return true;  // 添加标识是驱动器
                                }
                            };
                            items.add(driveItem);
                        }
                    } catch (IOException e) {
                        // 无法访问的驱动器，跳过
                        System.err.println("无法访问驱动器: " + root + " - " + e.getMessage());
                    }
                }

                // 添加常用文件夹
                addSpecialFolders(items);

                return items;
            }
        };

        currentLoadingTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                mainView.getTableView().getItems().setAll(currentLoadingTask.getValue());
                if (mainView.getGridView().isVisible()) {
                    loadGridViewForHomePage(currentLoadingTask.getValue());
                }
            });
        });

        currentLoadingTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("错误", "加载首页失败: " + currentLoadingTask.getException().getMessage());
            });
        });

        // 使用线程池提交任务
        threadPool.submitBackgroundTask(currentLoadingTask);
    }

    /**
     * 添加特殊文件夹到首页
     */
    private void addSpecialFolders(List<FileItem> items) {
        // 桌面
        Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
        if (Files.exists(desktop)) {
            items.add(new FileItem(desktop) {
                @Override
                public String getName() {
                    return "桌面";
                }
            });
        }

        // 文档
        Path documents = Paths.get(System.getProperty("user.home"), "Documents");
        if (Files.exists(documents)) {
            items.add(new FileItem(documents) {
                @Override
                public String getName() {
                    return "文档";
                }
            });
        }

        // 下载
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.exists(downloads)) {
            items.add(new FileItem(downloads) {
                @Override
                public String getName() {
                    return "下载";
                }
            });
        }

        // 图片
        Path pictures = Paths.get(System.getProperty("user.home"), "Pictures");
        if (Files.exists(pictures)) {
            items.add(new FileItem(pictures) {
                @Override
                public String getName() {
                    return "图片";
                }
            });
        }
    }

    /**
     * 为首页加载网格视图
     */
    private void loadGridViewForHomePage(List<FileItem> items) {
        mainView.clearGridView();

        for (FileItem item : items) {
            Button button = mainView.addGridItem(item);  // 修改调用方式

            // 为按钮添加点击事件
            button.setOnMouseClicked(event -> handleGridItemClick(event, button, item));

            mainView.getGridView().getChildren().add(button);  // 添加到 FlowPane
        }
    }

    private void initializeClipboardListener() {
        // 监听剪贴板是否为空的变化
        clipboardManager.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
            Platform.runLater(() -> {
                if (isEmpty) {
                    mainView.getStatusLabel().setText("就绪");
                } else {
                    String operation = clipboardManager.isCutOperation() ? "剪切" : "复制";
                    mainView.getStatusLabel().setText(
                            String.format("%s %d 个项目到剪贴板",
                                    operation, clipboardManager.getItemCount())
                    );
                }
            });
        });

        // 监听剪贴板操作类型的变化
        clipboardManager.isCutOperationProperty().addListener((obs, oldValue, newValue) -> {
            Platform.runLater(() -> {
                if (!clipboardManager.isEmpty()) {
                    String operation = newValue ? "剪切" : "复制";
                    mainView.getStatusLabel().setText(
                            String.format("%s %d 个项目到剪贴板",
                                    operation, clipboardManager.getItemCount())
                    );
                }
            });
        });

        // 监听剪贴板项目数量的变化
        clipboardManager.itemCountProperty().addListener((obs, oldCount, newCount) -> {
            Platform.runLater(() -> {
                if (newCount.intValue() > 0) {
                    String operation = clipboardManager.isCutOperation() ? "剪切" : "复制";
                    mainView.getStatusLabel().setText(
                            String.format("%s %d 个项目到剪贴板",
                                    operation, newCount)
                    );
                }
            });
        });
    }

    public void executeFileOperation(FileOperationTask.OperationType type, Path source, Path target) {
        // 如果有正在进行的任务，先询问用户
        if (currentFileOperationTask != null && currentFileOperationTask.isRunning()) {
            boolean confirm = showConfirmDialog("确认", "当前有文件操作正在进行，是否继续？");
            if (!confirm) {
                return;
            }
        }

        FileOperationTask task = new FileOperationTask(type, source, target);
        task.setOwnerStage(primaryStage);
        currentFileOperationTask = task;

        // 创建进度对话框
        Dialog<Void> progressDialog = task.createProgressDialog();

        // 任务完成时关闭对话框
        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                loadFiles(currentPath); // 刷新文件列表
            });
        });

        task.setOnCancelled(e -> {
            Platform.runLater(progressDialog::close);
        });

        task.setOnFailed(e -> {
            Platform.runLater(progressDialog::close);
        });

        // 使用线程池提交任务
        threadPool.submitFileOperation(task);

        // 显示进度对话框
        Platform.runLater(() -> {
            progressDialog.show();
        });
    }

    private void loadDirectoryTree() {
        // 树视图已经在MainView中创建，这里只需要加载子节点
        TreeItem<Path> rootItem = mainView.getTreeView().getRoot();
        if (rootItem == null) {
            return;
        }

        // 清空现有子节点
        rootItem.getChildren().clear();

        // 异步加载磁盘驱动器
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
                            // 检查访问权限
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

        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("警告", "部分磁盘驱动器无法加载: " + e.getSource().getException().getMessage());
            });
        });

        threadPool.submitBackgroundTask(loadTask);
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

    // 修改 loadFiles 方法，使用线程池
    private void loadFiles(Path dir) {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        // 更新状态栏
        mainView.getStatusLabel().setText("正在加载: " + dir);

        currentLoadingTask = new Task<>() {
            @Override
            protected List<FileItem> call() throws Exception {
                if (isCancelled()) {
                    return new ArrayList<>();
                }

                List<FileItem> items = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        if (isCancelled()) {
                            return items;
                        }
                        items.add(new FileItem(entry));
                    }
                } catch (AccessDeniedException e) {
                    Platform.runLater(() -> {
                        showAlert("访问被拒绝", "无法访问目录: " + dir, Alert.AlertType.WARNING);
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        showAlert("错误", "无法读取目录: " + e.getMessage());
                    });
                }
                return items;
            }
        };

        currentLoadingTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                mainView.getTableView().getItems().setAll(currentLoadingTask.getValue());
                if (mainView.getGridView().isVisible()) {
                    loadGridView(dir);
                }
                mainView.getStatusLabel().setText("就绪 - 共 " + currentLoadingTask.getValue().size() + " 个项目");
            });
        });

        currentLoadingTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                showAlert("错误", "加载文件失败: " + currentLoadingTask.getException().getMessage());
                mainView.getStatusLabel().setText("加载失败");
            });
        });

        // 使用线程池提交任务
        threadPool.submitBackgroundTask(currentLoadingTask);
    }

    private void loadGridView(Path dir) {
        mainView.clearGridView();  // 清空网格视图

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                FileItem item = new FileItem(entry);
                Button button = mainView.addGridItem(item);  // 修改调用方式

                // 为按钮添加点击事件
                button.setOnMouseClicked(event -> handleGridItemClick(event, button, item));

                mainView.getGridView().getChildren().add(button);  // 添加到 FlowPane
            }
        } catch (IOException e) {
            showAlert("错误", "加载网格视图失败: " + e.getMessage());
        }
    }

    private void handleGridItemClick(MouseEvent event, Button button, FileItem item) {
        if (event.getButton() == MouseButton.PRIMARY) {
            if (event.getClickCount() == 2) {
                // 双击：打开文件或进入目录
                if (Files.isDirectory(item.getPath())) {
                    navigateTo(item.getPath());
                } else {
                    openFile(item.getPath());
                }
            } else {
                // 单击：处理选中
                Set<FileItem> selected = mainView.getSelectedItemsInGrid();  // 修改这里

                if (event.isControlDown()) {
                    // Ctrl + 点击：切换选中状态
                    if (selected.contains(item)) {
                        selected.remove(item);
                        button.getStyleClass().remove("selected");  // 通过 CSS 类控制选中状态
                    } else {
                        selected.add(item);
                        button.getStyleClass().add("selected");
                    }
                } else if (event.isShiftDown()) {
                    // Shift + 点击：范围选择
                    // 这里可以添加范围选择逻辑
                } else {
                    // 无修饰键：清空其他选中，只选中当前
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

    // 修改原有的 handleSearch 方法
    private void handleSearch(String pattern) {
        if (currentPath == null) return;

        // 创建搜索任务
        SearchTask searchTask = new SearchTask(currentPath, pattern);
        searchTask.setOnSucceeded(e -> Platform.runLater(() -> {
            mainView.getTableView().getItems().setAll(searchTask.getValue());
            if (mainView.getGridView().isVisible()) {
                // 如果当前是网格视图，也需要更新
                updateGridViewWithSearchResults(searchTask.getValue());
            }
        }));
        searchTask.setOnFailed(e -> showAlert("错误", "搜索失败: " + searchTask.getException().getMessage()));

        // 使用线程池管理器提交搜索任务
        threadPool.submitBackgroundTask(searchTask);
    }

    // 新增方法：使用搜索结果更新网格视图
    private void updateGridViewWithSearchResults(List<FileItem> searchResults) {
        if (!mainView.getGridView().isVisible()) {
            return;
        }

        mainView.getGridView().getChildren().clear();
        mainView.getSelectedItemsInGrid().clear();

        for (FileItem item : searchResults) {
            Button button = mainView.addGridItem(item);  // 修改调用方式

            // 设置按钮样式
            if (item.isDirectory()) {
                button.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #bbdefb;");
            }

            // 添加点击事件处理
            button.setOnMouseClicked(event -> handleGridItemClick(event, button, item));

            mainView.getGridView().getChildren().add(button);  // 添加到 FlowPane
        }
    }

    private void openFile(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            showAlert("错误", "无法打开文件: " + e.getMessage());
        }
    }

    private void showFileDetails(Path path) {
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
            showAlert("错误", "无法获取驱动器信息: " + e.getMessage());
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

    // 添加解压方法
    public void extractFile(Path source, Path target) {
        executeFileOperation(FileOperationTask.OperationType.EXTRACT, source, target);
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

        // 复制功能 - 只保存到剪贴板
        copy.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (!selectedItems.isEmpty()) {
                List<Path> paths = selectedItems.stream()
                        .map(FileItem::getPath)
                        .collect(Collectors.toList());
                clipboardManager.setClipboard(paths, false);
                showInfo("已复制 " + paths.size() + " 个项目到剪贴板");
            }
        });

        // 剪切功能 - 只保存到剪贴板
        cut.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (!selectedItems.isEmpty()) {
                List<Path> paths = selectedItems.stream()
                        .map(FileItem::getPath)
                        .collect(Collectors.toList());
                clipboardManager.setClipboard(paths, true);
                showInfo("已剪切 " + paths.size() + " 个项目到剪贴板");
            }
        });

        // 粘贴功能 - 从剪贴板读取并执行
        paste.setOnAction(e -> {
            if (clipboardManager.isEmpty() || currentPath == null) {
                return;
            }

            List<Path> clipboardItems = clipboardManager.getClipboardItems();
            boolean isCut = clipboardManager.isCutOperation();

            // 检查目标路径是否在源路径中（避免循环复制）
            for (Path source : clipboardItems) {
                if (currentPath.startsWith(source)) {
                    showAlert("错误", "不能将文件夹复制到自身或其子文件夹中");
                    return;
                }
            }

            // 检查重名文件
            List<Path> conflicts = new ArrayList<>();
            for (Path source : clipboardItems) {
                Path target = currentPath.resolve(source.getFileName());
                if (Files.exists(target)) {
                    conflicts.add(source);
                }
            }

            if (!conflicts.isEmpty()) {
                // 显示冲突确认对话框
                if (!showConflictDialog(conflicts)) {
                    return;
                }
            }

            // 执行批量操作
            BatchFileOperationTask.OperationType operationType =
                    isCut ? BatchFileOperationTask.OperationType.MOVE :
                            BatchFileOperationTask.OperationType.COPY;

            executeBatchOperation(operationType, clipboardItems, currentPath);

            // 如果是剪切操作，清空剪贴板
            if (isCut) {
                clipboardManager.clearClipboard();
            }
        });

        // 修改删除功能 - 使用批量删除
        delete.setOnAction(e -> {
            List<FileItem> selectedItems = mainView.getSelectedFileItems();
            if (selectedItems.isEmpty()) {
                return;
            }

            String message = "确定要删除选中的 " + selectedItems.size() + " 个项目吗？";
            if (selectedItems.size() == 1) {
                message = "确定要删除 \"" + selectedItems.get(0).getName() + "\" 吗？";
            }

            if (showConfirmDialog("确认删除", message)) {
                List<Path> paths = selectedItems.stream()
                        .map(FileItem::getPath)
                        .collect(Collectors.toList());
                executeBatchOperation(BatchFileOperationTask.OperationType.DELETE,
                        paths, null);
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

        // 动态更新粘贴菜单项状态
        paste.disableProperty().bind(
                Bindings.createBooleanBinding(() ->
                                clipboardManager.isEmpty() || currentPath == null,
                        mainView.getPathField().textProperty()
                )
        );

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

    private boolean showConflictDialog(List<Path> conflicts) {
        StringBuilder message = new StringBuilder();
        message.append("以下文件已存在，是否覆盖？\n\n");

        for (int i = 0; i < Math.min(5, conflicts.size()); i++) {
            message.append("• ").append(conflicts.get(i).getFileName()).append("\n");
        }

        if (conflicts.size() > 5) {
            message.append("... 还有 ").append(conflicts.size() - 5).append(" 个文件\n");
        }

        message.append("\n选择是覆盖所有，否跳过所有，取消中断操作");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("文件冲突");
        alert.setHeaderText("目标位置已存在同名文件");
        alert.setContentText(message.toString());

        ButtonType replaceAll = new ButtonType("覆盖所有");
        ButtonType skipAll = new ButtonType("跳过所有");
        ButtonType cancel = ButtonType.CANCEL;

        alert.getButtonTypes().setAll(replaceAll, skipAll, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == replaceAll) {
                return true;
            } else if (result.get() == skipAll) {
                // 这里可以过滤掉冲突文件，跳过它们
                // 为了简化，我们返回false让用户手动处理
                showInfo("已跳过所有冲突文件");
                return false;
            }
        }
        return false;
    }


    /**
     * 执行批量文件操作
     */
    private void executeBatchOperation(BatchFileOperationTask.OperationType type,
                                       List<Path> sourcePaths, Path targetDir) {
        BatchFileOperationTask task = new BatchFileOperationTask(
                type, sourcePaths, targetDir, primaryStage
        );

        // 创建进度对话框
        Dialog<Void> progressDialog = task.createProgressDialog();

        // 任务完成时关闭对话框并刷新文件列表
        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                loadFiles(currentPath); // 刷新文件列表

                // 如果是删除操作且目标目录为空，刷新父目录
                if (type == BatchFileOperationTask.OperationType.DELETE &&
                        currentPath != null && Files.exists(currentPath)) {
                    loadFiles(currentPath);
                }
            });
        });

        task.setOnCancelled(e -> {
            Platform.runLater(progressDialog::close);
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                loadFiles(currentPath); // 即使失败也刷新
            });
        });

        // 使用线程池提交任务
        threadPool.submitBackgroundTask(task);

        // 显示进度对话框
        Platform.runLater(() -> {
            progressDialog.show();
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

    /**
     * 添加程序退出时的清理代码
     */
    public void shutdown() {
        // 取消搜索计时器
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer = null;
        }

        // 取消正在进行的任务
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        if (currentFileOperationTask != null && currentFileOperationTask.isRunning()) {
            currentFileOperationTask.cancel(true);
        }

        // 关闭线程池
        threadPool.shutdown();
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
        if (newPath == null) {
            loadHomePage();
            return;
        }

        try {
            if (newPath.toString().equals("此电脑")) {
                loadHomePage();
                return;
            }

            if (!Files.exists(newPath)) {
                showAlert("错误", "路径不存在: " + newPath);
                return;
            }

            if (!Files.isDirectory(newPath)) {
                openFile(newPath);
                return;
            }

            if (!Files.isReadable(newPath)) {
                showAlert("错误", "没有权限访问: " + newPath);
                return;
            }

            if (!newPath.equals(currentPath)) {
                currentPath = newPath;
                addToHistory(currentPath);
                loadFiles(currentPath);
                updatePathField();
                selectInTreeView(currentPath);
            }
        } catch (Exception e) {
            showAlert("错误", "无法访问路径: " + newPath + " - " + e.getMessage());
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
        mainView.getUpButton().setDisable(currentPath == null);
    }

    private void updatePathField() {
        if (currentPath == null) {
            mainView.getPathField().setText("此电脑");
        } else {
            mainView.getPathField().setText(currentPath.toAbsolutePath().toString());
        }
    }

    // 在树视图中选择对应路径的节点
    // 在树视图中选择对应路径的节点
    private void selectInTreeView(Path path) {
        if (path == null) {
            // 如果是在首页，选择"此电脑"节点
            TreeItem<Path> root = mainView.getTreeView().getRoot();
            if (root != null) {
                mainView.getTreeView().getSelectionModel().select(root);
            }
            return;
        }

        // 查找树视图中对应的节点并选中
        TreeItem<Path> root = mainView.getTreeView().getRoot();
        if (root != null) {
            // 递归查找路径对应的节点
            TreeItem<Path> found = findTreeItem(root, path);
            if (found != null) {
                mainView.getTreeView().getSelectionModel().select(found);
                // 展开到该节点
                TreeItem<Path> parent = found.getParent();
                while (parent != null && parent != root) {
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

    public void copySelected() {
        List<FileItem> selectedItems = mainView.getSelectedFileItems();
        if (!selectedItems.isEmpty()) {
            List<Path> paths = selectedItems.stream()
                    .map(FileItem::getPath)
                    .collect(Collectors.toList());
            clipboardManager.setClipboard(paths, false);
            showInfo("已复制 " + paths.size() + " 个项目到剪贴板");
        }
    }

    public void cutSelected() {
        List<FileItem> selectedItems = mainView.getSelectedFileItems();
        if (!selectedItems.isEmpty()) {
            List<Path> paths = selectedItems.stream()
                    .map(FileItem::getPath)
                    .collect(Collectors.toList());
            clipboardManager.setClipboard(paths, true);
            showInfo("已剪切 " + paths.size() + " 个项目到剪贴板");
        }
    }

    public void paste() {
        if (clipboardManager.isEmpty() || currentPath == null) {
            return;
        }

        List<Path> clipboardItems = clipboardManager.getClipboardItems();
        boolean isCut = clipboardManager.isCutOperation();

        // 检查目标路径是否在源路径中（避免循环复制）
        for (Path source : clipboardItems) {
            if (currentPath.startsWith(source)) {
                showAlert("错误", "不能将文件夹复制到自身或其子文件夹中");
                return;
            }
        }

        // 检查重名文件
        List<Path> conflicts = new ArrayList<>();
        for (Path source : clipboardItems) {
            Path target = currentPath.resolve(source.getFileName());
            if (Files.exists(target)) {
                conflicts.add(source);
            }
        }

        if (!conflicts.isEmpty()) {
            // 显示冲突确认对话框
            if (!showConflictDialog(conflicts)) {
                return;
            }
        }

        // 执行批量操作
        BatchFileOperationTask.OperationType operationType =
                isCut ? BatchFileOperationTask.OperationType.MOVE :
                        BatchFileOperationTask.OperationType.COPY;

        executeBatchOperation(operationType, clipboardItems, currentPath);

        // 如果是剪切操作，清空剪贴板
        if (isCut) {
            clipboardManager.clearClipboard();
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

        if (showConfirmDialog("确认删除", message)) {
            List<Path> paths = selectedItems.stream()
                    .map(FileItem::getPath)
                    .collect(Collectors.toList());
            executeBatchOperation(BatchFileOperationTask.OperationType.DELETE,
                    paths, null);
        }
    }

    public void renameSelected() {
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
    }

    public void createNewFolder() {
        TextInputDialog dialog = new TextInputDialog("新建文件夹");
        dialog.setTitle("新建文件夹");
        dialog.setHeaderText("创建新文件夹");
        dialog.setContentText("请输入文件夹名称:");

        dialog.showAndWait().ifPresent(folderName -> {
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

    public void refresh() {
        if (currentPath != null) {
            loadFiles(currentPath);
        } else {
            loadHomePage();
        }
    }
}

class KeyboardHandler {
    private final FileExplorerController controller;
    private final MainView mainView;

    public KeyboardHandler(FileExplorerController controller, MainView mainView) {
        this.controller = controller;
        this.mainView = mainView;
        mainView.getRoot().addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case C: // Ctrl + C 复制
                    controller.copySelected();
                    event.consume();
                    break;
                case X: // Ctrl + X 剪切
                    controller.cutSelected();
                    event.consume();
                    break;
                case V: // Ctrl + V 粘贴
                    controller.paste();
                    event.consume();
                    break;
                case A: // Ctrl + A 全选
                    controller.selectAll();
                    event.consume();
                    break;
                default:
                    break;
            }
        } else {
            switch (event.getCode()) {
                case DELETE: // Delete 删除
                    controller.deleteSelected();
                    event.consume();
                    break;
                case F2: // F2 重命名
                    controller.renameSelected();
                    event.consume();
                    break;
                case F5: // F5 刷新
                    controller.refresh();
                    event.consume();
                    break;
                default:
                    break;
            }
        }
    }
}