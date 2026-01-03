package com.fileexplorer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventTarget;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 文件资源管理器控制器，协调各种handler处理逻辑。
 */
public class Controller {
    @FXML
    private BorderPane root;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TreeView<Path> treeView;
    @FXML
    private TableView<FileItem> tableView;
    @FXML
    private FlowPane gridView;
    public boolean isGridMode = false;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button upButton;
    @FXML
    private TextField pathField;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> searchModeComboBox;
    @FXML
    private Button refreshButton;
    @FXML
    private Button modeButton;
    private final Set<FileItem> selectedItemsInGrid = new HashSet<>();
    @FXML
    private Label statusLabel;
    @FXML
    private HBox statusBar;
    @FXML
    private ScrollPane tableScroll;
    @FXML
    private ScrollPane gridScroll;

    private Stage primaryStage;
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

    public Controller() {
        this.navigationHandler = new NavigationHandler(this);
        this.searchHandler = new SearchHandler(this);
        this.fileOperationHandler = new FileOperationHandler(this);
        this.treeViewHandler = new TreeViewHandler(this);

    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void initialize() {
        // 设置样式类
        root.getStyleClass().add("root");
        toolBar.getStyleClass().add("tool-bar");
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setSpacing(20);
        statusLabel.getStyleClass().add("status-label");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        treeView.getStyleClass().add("tree-view");
        tableView.getStyleClass().add("table-view");
        gridView.getStyleClass().add("grid-pane");
        gridView.setPadding(new Insets(10));
        gridView.setHgap(10);
        gridView.setVgap(10);

        // 设置按钮样式和提示
        backButton.getStyleClass().add("nav-button");
        backButton.setTooltip(new Tooltip("后退"));
        forwardButton.getStyleClass().add("nav-button");
        forwardButton.setTooltip(new Tooltip("前进"));
        upButton.getStyleClass().add("nav-button");
        upButton.setTooltip(new Tooltip("上一级"));
        pathField.getStyleClass().add("path-field");
        searchField.getStyleClass().add("search-field");
        refreshButton.getStyleClass().add("text-button");
        modeButton.getStyleClass().add("text-button");
        modeButton.setText("网格");  // 初始为表格模式，所以按钮显示"网格"

        // 设置搜索模式下拉框
        searchModeComboBox.getItems().addAll("通配符匹配", "字符串匹配", "文本文件内容通配符匹配", "搜索图片", "搜索音频", "搜索视频", "搜索文档", "搜索压缩文件", "检索大文件(100MB+,可能需要等待)");
        searchModeComboBox.setValue("字符串匹配");

        // 设置树视图单元工厂
        treeView.setCellFactory(param -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (item.toString().equals("此电脑")) {
                        setText("此电脑");
                    } else {
                        setText(item.getFileName() != null ? item.getFileName().toString() : item.toString());
                    }

                    if (item.toString().equals("此电脑") || Files.isDirectory(item)) {
                        ImageView icon = IconManager.getInstance().createFolderIconView(16);
                        if (icon != null) {
                            setGraphic(icon);
                        }
                    }
                }
            }
        });
        TreeItem<Path> rootItem = new TreeItem<>(Paths.get("此电脑"));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        // 设置表格列
        TableColumn<FileItem, Image> iconColumn = new TableColumn<>("");
        iconColumn.setCellValueFactory(new PropertyValueFactory<>("icon"));
        iconColumn.setCellFactory(col -> new TableCell<FileItem, Image>() {
            private final ImageView imageView = new ImageView();

            @Override
            protected void updateItem(Image image, boolean empty) {
                super.updateItem(image, empty);
                if (empty || image == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(image);
                    imageView.setFitWidth(16);
                    imageView.setFitHeight(16);
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                }
            }
        });
        iconColumn.setPrefWidth(30);
        iconColumn.setResizable(false);
        iconColumn.setSortable(false);

        TableColumn<FileItem, String> nameColumn = new TableColumn<>("名称");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(300);

        TableColumn<FileItem, LocalDateTime> modifiedColumn = new TableColumn<>("修改日期");
        modifiedColumn.setCellValueFactory(new PropertyValueFactory<>("modifiedTime"));
        modifiedColumn.setCellFactory(col -> new TableCell<FileItem, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) {
                    setText(null);
                } else {
                    setText(FileUtils.formatDateTime(date));
                }
            }
        });
        modifiedColumn.setPrefWidth(150);

        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(120);

        TableColumn<FileItem, Long> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setCellFactory(col -> new TableCell<FileItem, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    FileItem item = getTableView().getItems().get(getIndex());
                    if (item.isDrive()) {
                        try {
                            FileStore store = Files.getFileStore(item.getPath());
                            long available = store.getUsableSpace();
                            long total = store.getTotalSpace();
                            setText(FileUtils.formatSize(available) + " / " + FileUtils.formatSize(total));
                        } catch (IOException e) {
                            setText(FileUtils.formatSize(size));
                        }
                    } else {
                        setText(FileUtils.formatSize(size));
                    }
                }
            }
        });
        sizeColumn.setPrefWidth(100);

        tableView.getColumns().addAll(iconColumn, nameColumn, modifiedColumn, typeColumn, sizeColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 设置滚动pane
        tableScroll.setFitToWidth(true);
        tableScroll.setFitToHeight(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.getStyleClass().add("table-scroll-pane");

        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(false);
        gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.getStyleClass().add("grid-scroll-pane");
        gridScroll.setVisible(false);

        new KeyboardHandler(this);
        Platform.runLater(this::postInitialize);
    }

    private void postInitialize() {
        // 设置初始路径
        currentPath = Paths.get(System.getProperty("user.home"));
        navigationHandler.addToHistory(currentPath);

        // 加载首页
        loadHomePage();
        pathField.setText("此电脑");

        // 绑定事件
        bindEvents();

        // 加载目录树
        treeViewHandler.loadDirectoryTree();

        // 初始化剪贴板监听
        fileOperationHandler.initializeClipboardListener();

        // 设置右键菜单
        ContextMenu contextMenu = fileOperationHandler.createContextMenu();
        tableView.setContextMenu(contextMenu);
        gridView.setOnContextMenuRequested(event -> contextMenu.show(gridView, event.getScreenX(), event.getScreenY()));

        ContextMenu treeContextMenu = treeViewHandler.createTreeContextMenu();
        treeView.setContextMenu(treeContextMenu);
    }

    private void bindEvents() {
        backButton.setOnAction(e -> navigationHandler.goBack());
        forwardButton.setOnAction(e -> navigationHandler.goForward());
        upButton.setOnAction(e -> {
            if (currentPath != null) {
                navigationHandler.goUp();
            } else {
                loadHomePage();
            }
        });

        pathField.setOnAction(e -> navigationHandler.handlePathInput());

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                navigationHandler.navigateTo(newVal.getValue());
            }
        });

        tableView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                FileItem selected = tableView.getSelectionModel().getSelectedItem();
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
                if (tableView.getSelectionModel().getSelectedItem() == null) {
                    tableView.getSelectionModel().clearSelection();
                }
            }
        });

        gridView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                if (event.getTarget() == gridView) {
                    fileOperationHandler.deselectAllInGrid();
                }
            }
        });

        searchHandler.bindSearchEvents();

        refreshButton.setOnAction(e -> refresh());

        modeButton.setOnAction(e -> {
            switchViewMode();
            if (gridView.isVisible()) {
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
        pathField.setText("此电脑");
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
            tableView.getItems().setAll(currentLoadingTask.getValue());
            if (gridView.isVisible()) {
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
        clearGridView();

        for (FileItem item : items) {
            Button button = addGridItem(item);
            button.setOnMouseClicked(event -> fileOperationHandler.handleGridItemClick(event, button, item));
            gridView.getChildren().add(button);
        }
    }

    /**
     * 加载目录文件列表。
     */
    public void loadFiles(Path dir) {
        if (currentLoadingTask != null && currentLoadingTask.isRunning()) {
            currentLoadingTask.cancel();
        }

        statusLabel.setText("正在加载: " + dir);

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
            tableView.getItems().setAll(currentLoadingTask.getValue());
            if (gridView.isVisible()) {
                loadGridView(dir);
            }
            statusLabel.setText("就绪 - 共 " + currentLoadingTask.getValue().size() + " 个项目");
        }));

        currentLoadingTask.setOnFailed(e -> Platform.runLater(() -> {
            UIUtils.showAlert("错误", "加载文件失败: " + currentLoadingTask.getException().getMessage());
            statusLabel.setText("加载失败");
        }));

        threadPool.submitBackgroundTask(currentLoadingTask);
    }

    private void loadGridView(Path dir) {
        clearGridView();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                FileItem item = new FileItem(entry);
                Button button = addGridItem(item);
                button.setOnMouseClicked(event -> fileOperationHandler.handleGridItemClick(event, button, item));
                gridView.getChildren().add(button);
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

    // 从MainView移动的方法
    public void switchViewMode() {
        isGridMode = !isGridMode;
        if (isGridMode) {
            selectedItemsInGrid.clear();
            tableScroll.setVisible(false);
            gridScroll.setVisible(true);
            modeButton.setText("列表");
        } else {
            tableScroll.setVisible(true);
            gridScroll.setVisible(false);
            modeButton.setText("网格");
        }
    }

    public void clearGridView() {
        gridView.getChildren().clear();
        selectedItemsInGrid.clear();
    }

    public Button addGridItem(FileItem item) {
        Button button = new Button();
        button.getStyleClass().add("grid-button");
        button.setPrefWidth(100);

        VBox content = new VBox();
        content.setSpacing(8);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(5));

        ImageView iconView = item.getIconView(48);
        if (iconView != null) {
            content.getChildren().add(iconView);
        }

        Label nameLabel = new Label(item.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(80);
        nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameLabel.setAlignment(Pos.CENTER);
        content.getChildren().add(nameLabel);

        button.setGraphic(content);
        button.setUserData(item);

        return button;
    }

    public List<FileItem> getSelectedFileItems() {
        if (isGridMode) {
            return new ArrayList<>(selectedItemsInGrid);
        } else {
            return new ArrayList<>(tableView.getSelectionModel().getSelectedItems());
        }
    }

    public Set<FileItem> getSelectedItemsInGrid() {
        return selectedItemsInGrid;
    }

    public FileItem getSelectedFileItem() {
        List<FileItem> selected = getSelectedFileItems();
        return selected.isEmpty() ? null : selected.get(0);
    }

    // 为handler提供getter，以最小化handler代码变更
    public TreeView<Path> getTreeView() {
        return treeView;
    }

    public TableView<FileItem> getTableView() {
        return tableView;
    }

    public FlowPane getGridView() {
        return gridView;
    }

    public Button getBackButton() {
        return backButton;
    }

    public Button getForwardButton() {
        return forwardButton;
    }

    public Button getUpButton() {
        return upButton;
    }

    public TextField getPathField() {
        return pathField;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public ComboBox<String> getSearchModeComboBox() {
        return searchModeComboBox;
    }

    public Button getRefreshButton() {
        return refreshButton;
    }

    public Button getModeButton() {
        return modeButton;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

    public BorderPane getRoot() {
        return root;
    }
}