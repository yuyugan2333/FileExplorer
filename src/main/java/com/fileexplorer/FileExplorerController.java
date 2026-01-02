package com.fileexplorer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

/**
 * 文件资源管理器控制器，协调各种handler处理逻辑。
 */
public class FileExplorerController {
    private final MainView mainView;
    private final Stage primaryStage;
    private Path currentPath;
    private final ThreadPoolManager threadPool = ThreadPoolManager.getInstance();
    private final ClipboardManager clipboardManager = ClipboardManager.getInstance();
    private Task<List<FileItem>> currentLoadingTask = null;
    private Timer searchTimer;
    public FileOperationTask currentFileOperationTask = null;

    // 拆分的handler
    public final NavigationHandler navigationHandler;
    private final SearchHandler searchHandler;
    public final FileOperationHandler fileOperationHandler;
    public final TreeViewHandler treeViewHandler;

    public FileExplorerController(MainView mainView, Stage primaryStage) {
        this.mainView = mainView;
        this.primaryStage = primaryStage;
        this.navigationHandler = new NavigationHandler(this, mainView);
        this.searchHandler = new SearchHandler(this, mainView);
        this.fileOperationHandler = new FileOperationHandler(this, mainView);
        this.treeViewHandler = new TreeViewHandler(this, mainView);

        // 延迟到界面完全加载后初始化
        Platform.runLater(this::initialize);
        new KeyboardHandler(this, mainView);
    }

    private void initialize() {
        // 设置初始路径
        currentPath = Paths.get(System.getProperty("user.home"));
        navigationHandler.addToHistory(currentPath);

        // 加载首页
        loadHomePage();
        mainView.getPathField().setText("此电脑");

        // 绑定事件
        bindEvents();

        // 加载目录树
        treeViewHandler.loadDirectoryTree();

        // 初始化剪贴板监听
        fileOperationHandler.initializeClipboardListener();

        // 设置右键菜单
        ContextMenu contextMenu = fileOperationHandler.createContextMenu();
        mainView.getTableView().setContextMenu(contextMenu);
        mainView.getGridView().setOnContextMenuRequested(event -> contextMenu.show(mainView.getGridView(), event.getScreenX(), event.getScreenY()));

        ContextMenu treeContextMenu = treeViewHandler.createTreeContextMenu();
        mainView.getTreeView().setContextMenu(treeContextMenu);
    }

    private void bindEvents() {
        mainView.getBackButton().setOnAction(e -> navigationHandler.goBack());
        mainView.getForwardButton().setOnAction(e -> navigationHandler.goForward());
        mainView.getUpButton().setOnAction(e -> {
            if (currentPath != null) {
                navigationHandler.goUp();
            } else {
                loadHomePage();
            }
        });

        mainView.getPathField().setOnAction(e -> navigationHandler.handlePathInput());

        mainView.getTreeView().getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                navigationHandler.navigateTo(newVal.getValue());
            }
        });

        mainView.getTableView().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                FileItem selected = mainView.getTableView().getSelectionModel().getSelectedItem();
                if (selected != null) {
                    if (currentPath == null) {
                        if (selected.isDirectory()) {
                            navigationHandler.navigateTo(selected.getPath());
                        } else {
                            fileOperationHandler.openFile(selected.getPath());
                        }
                    } else {
                        if (selected.isDirectory()) {
                            navigationHandler.navigateTo(selected.getPath());
                        } else {
                            fileOperationHandler.openFile(selected.getPath());
                        }
                    }
                }
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                if (mainView.getTableView().getSelectionModel().getSelectedItem() == null) {
                    mainView.getTableView().getSelectionModel().clearSelection();
                }
            }
        });

        mainView.getGridView().setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                if (event.getTarget() == mainView.getGridView()) {
                    fileOperationHandler.deselectAllInGrid();
                }
            }
        });

        searchHandler.bindSearchEvents();

        mainView.getRefreshButton().setOnAction(e -> refresh());

        mainView.getModeButton().setOnAction(e -> {
            mainView.switchViewMode();
            if (mainView.getGridView().isVisible()) {
                if (currentPath != null) {
                    loadGridView(currentPath);
                } else {
                    loadHomePage();
                }
            }
        });
    }

    /**
     * 加载首页（所有驱动器和特殊文件夹）。
     */
    public void loadHomePage() {
        currentPath = null;
        if (navigationHandler.getCurrentIndex() < 0 || navigationHandler.getHistory().isEmpty()) {
            navigationHandler.addToHistory(null);
        } else if (navigationHandler.getHistory().get(navigationHandler.getCurrentIndex()) != null) {
            navigationHandler.addToHistory(null);
        }

        navigationHandler.updateNavigationButtons();
        mainView.getPathField().setText("此电脑");
        treeViewHandler.selectRootInTree();

        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        currentLoadingTask = new Task<List<FileItem>>() {
            @Override
            protected List<FileItem> call() throws Exception {
                List<FileItem> items = new ArrayList<>();

                FileSystem fs = FileSystems.getDefault();
                for (Path root : fs.getRootDirectories()) {
                    try {
                        if (Files.exists(root) && Files.isReadable(root)) {
                            FileStore store = Files.getFileStore(root);
                            FileItem driveItem = new FileItem(root) {
                                @Override
                                public String getName() {
                                    String name = super.getName();
                                    String displayName = store.name();
                                    if (displayName != null && !displayName.isEmpty()) {
                                        return name + " (" + displayName + ")";
                                    }
                                    return name;
                                }

                                @Override
                                public String getType() {
                                    return store.type() + " 驱动器";
                                }

                                @Override
                                public long getSize() {
                                    try {
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
                                    return true;
                                }
                            };
                            items.add(driveItem);
                        }
                    } catch (IOException e) {
                        System.err.println("无法访问驱动器: " + root + " - " + e.getMessage());
                    }
                }

                addSpecialFolders(items);
                return items;
            }
        };

        currentLoadingTask.setOnSucceeded(e -> Platform.runLater(() -> {
            mainView.getTableView().getItems().setAll(currentLoadingTask.getValue());
            if (mainView.getGridView().isVisible()) {
                loadGridViewForHomePage(currentLoadingTask.getValue());
            }
        }));

        currentLoadingTask.setOnFailed(e -> UIUtils.showAlert("错误", "加载首页失败: " + currentLoadingTask.getException().getMessage()));

        threadPool.submitBackgroundTask(currentLoadingTask);
    }

    private void addSpecialFolders(List<FileItem> items) {
        // 添加桌面
        Path desktop = Paths.get(System.getProperty("user.home"), "Desktop");
        if (Files.exists(desktop)) {
            items.add(new FileItem(desktop) {
                @Override
                public String getName() {
                    return "桌面";
                }
            });
        }

//        // 添加文档
//        Path documents = Paths.get(System.getProperty("user.home"), "Documents");
//        if (Files.exists(documents)) {
//            items.add(new FileItem(documents) {
//                @Override
//                public String getName() {
//                    return "文档";
//                }
//            });
//        }

        // 添加下载
        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (Files.exists(downloads)) {
            items.add(new FileItem(downloads) {
                @Override
                public String getName() {
                    return "下载";
                }
            });
        }

        // 添加图片
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

    private void loadGridViewForHomePage(List<FileItem> items) {
        mainView.clearGridView();

        for (FileItem item : items) {
            Button button = mainView.addGridItem(item);
            button.setOnMouseClicked(event -> fileOperationHandler.handleGridItemClick(event, button, item));
            mainView.getGridView().getChildren().add(button);
        }
    }

    /**
     * 加载目录文件列表。
     */
    public void loadFiles(Path dir) {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        mainView.getStatusLabel().setText("正在加载: " + dir);

        currentLoadingTask = new Task<List<FileItem>>() {
            @Override
            protected List<FileItem> call() throws Exception {
                List<FileItem> items = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        if (isCancelled()) return items;
                        items.add(new FileItem(entry));
                    }
                } catch (AccessDeniedException e) {
                    Platform.runLater(() -> UIUtils.showAlert("访问被拒绝", "无法访问目录: " + dir, Alert.AlertType.WARNING));
                } catch (IOException e) {
                    Platform.runLater(() -> UIUtils.showAlert("错误", "无法读取目录: " + e.getMessage()));
                }
                return items;
            }
        };

        currentLoadingTask.setOnSucceeded(e -> Platform.runLater(() -> {
            mainView.getTableView().getItems().setAll(currentLoadingTask.getValue());
            if (mainView.getGridView().isVisible()) {
                loadGridView(dir);
            }
            mainView.getStatusLabel().setText("就绪 - 共 " + currentLoadingTask.getValue().size() + " 个项目");
        }));

        currentLoadingTask.setOnFailed(e -> Platform.runLater(() -> {
            UIUtils.showAlert("错误", "加载文件失败: " + currentLoadingTask.getException().getMessage());
            mainView.getStatusLabel().setText("加载失败");
        }));

        threadPool.submitBackgroundTask(currentLoadingTask);
    }

    private void loadGridView(Path dir) {
        mainView.clearGridView();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                FileItem item = new FileItem(entry);
                Button button = mainView.addGridItem(item);
                button.setOnMouseClicked(event -> fileOperationHandler.handleGridItemClick(event, button, item));
                mainView.getGridView().getChildren().add(button);
            }
        } catch (IOException e) {
            UIUtils.showAlert("错误", "加载网格视图失败: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (searchTimer != null) {
            searchTimer.cancel();
            searchTimer = null;
        }
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }
        if (currentFileOperationTask != null && currentFileOperationTask.isRunning()) {
            currentFileOperationTask.cancel(true);
        }
        threadPool.shutdown();
    }

    public Path getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(Path currentPath) {
        this.currentPath = currentPath;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public MainView getMainView() {
        return mainView;
    }

    public ThreadPoolManager getThreadPool() {
        return threadPool;
    }

    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    public void refresh() {
        if (currentPath != null) {
            loadFiles(currentPath);
        } else {
            loadHomePage();
        }
    }

    public NavigationHandler getNavigationHandler() {
        return navigationHandler;
    }
}