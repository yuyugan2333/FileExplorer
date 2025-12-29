package com.fileexplorer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class MainView {
    private BorderPane root;
    private ToolBar toolBar;
    private TreeView<Path> treeView;
    private TableView<FileItem> tableView;
    private GridPane gridView;
    private boolean isGridMode = false;
    private Button backButton;
    private Button forwardButton;
    private Button upButton;
    private TextField pathField;
    private TextField searchField;
    private Button refreshButton;
    private Button modeButton;
    private final Set<FileItem> selectedItemsInGrid = new HashSet<>();
    private Label statusLabel;
    private HBox statusBar;
    private ScrollPane tableScroll;
    private ScrollPane gridScroll;

    public MainView() {
        initializeLayout();
        root.getStylesheets().add(getClass().getResource("/com/fileexplorer/windows-style.css").toExternalForm());
    }

    private void initializeLayout() {
        root = new BorderPane();
        root.getStyleClass().add("root");

        // 创建工具栏
        createToolBar();

        // 创建状态栏
        createStatusBar();

        // 创建左侧目录树
        createTreeView();

        // 创建右侧文件列表
        createTableView();

        // 创建网格视图
        createGridView();

        // 创建包装用的ScrollPane
        tableScroll = new ScrollPane(tableView);
        tableScroll.setFitToWidth(true);
        tableScroll.setFitToHeight(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.getStyleClass().add("table-scroll-pane");

        // 包装网格视图
        gridScroll = new ScrollPane(gridView);
        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(true);
        gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.getStyleClass().add("grid-scroll-pane");
        gridScroll.setVisible(false);  // 初始时隐藏

        // 将两个ScrollPane放入StackPane，方便切换
        StackPane viewContainer = new StackPane();
        viewContainer.getChildren().addAll(tableScroll, gridScroll);

        // 设置布局
        root.setTop(toolBar);
        root.setLeft(createTreeContainer());
        root.setCenter(viewContainer);  // 将StackPane设置到center
        root.setBottom(statusBar);
    }

    private void createToolBar() {
        toolBar = new ToolBar();
        toolBar.getStyleClass().add("tool-bar");

        // 导航按钮
        backButton = createToolbarButton("←", "后退", "nav-button");
        forwardButton = createToolbarButton("→", "前进", "nav-button");
        upButton = createToolbarButton("↑", "上一级", "nav-button");

        // 添加分隔符
        Separator separator1 = new Separator();
        separator1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // 路径输入框
        pathField = new TextField();
        pathField.getStyleClass().add("path-field");
        pathField.setPromptText("输入路径...");
        pathField.setPrefWidth(400);

        // 添加分隔符
        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // 搜索框
        searchField = new TextField();
        searchField.getStyleClass().add("search-field");
        searchField.setPromptText("搜索文件...");
        searchField.setPrefWidth(200);

        // 刷新按钮
        refreshButton = createToolbarButton("刷新", "刷新", "text-button");

        // 模式切换按钮
        modeButton = createToolbarButton("列表", "切换列表/网格视图", "text-button");

        // 创建一个区域来填充空间
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolBar.getItems().addAll(
                backButton, forwardButton, upButton,
                separator1,
                pathField,
                separator2,
                searchField, refreshButton, modeButton,
                spacer
        );
    }
    private Button createToolbarButton(String text, String tooltip, String styleClass) {
        Button button = new Button(text);
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add(styleClass);
        return button;
    }

    private void createStatusBar() {
        statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setSpacing(20);

        statusLabel = new Label("就绪");
        statusLabel.getStyleClass().add("status-label");

        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusBar.getChildren().add(statusLabel);
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    private ScrollPane createTreeContainer() {
        // 创建目录树的容器
        ScrollPane treeScroll = new ScrollPane(treeView);
        treeScroll.setFitToWidth(true);
        treeScroll.setFitToHeight(true);
        treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setStyle("-fx-background: white; -fx-border-color: transparent;");

        // 设置最小宽度，确保不会被压缩
        treeScroll.setMinWidth(250);
        treeScroll.setPrefWidth(300);

        return treeScroll;
    }

    private void createTreeView() {
        treeView = new TreeView<>();
        treeView.getStyleClass().add("tree-view");

        // 创建根节点："此电脑"
        TreeItem<Path> rootItem = new TreeItem<>(Paths.get("此电脑"));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        // 设置TreeCell工厂以显示图标
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
                        setText(item.getFileName() != null ?
                                item.getFileName().toString() : item.toString());
                    }

                    // 为目录添加图标
                    if (item.toString().equals("此电脑") || Files.isDirectory(item)) {
                        ImageView icon = IconManager.getInstance().createFolderIconView(16);
                        if (icon != null) {
                            setGraphic(icon);
                        }
                    }
                }
            }
        });
    }

    private void createTableView() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("table-view");

        // 创建图标列
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

        // 文件名列
        TableColumn<FileItem, String> nameColumn = new TableColumn<>("名称");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(300);

        // 修改时间列 - 使用 FileUtils 中的格式化方法
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

        // 类型列
        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(120);

        // 大小列 - 使用 FileUtils 中的格式化方法
        TableColumn<FileItem, Long> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setCellFactory(col -> new TableCell<FileItem, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    setText(FileUtils.formatSize(size));
                }
            }
        });
        sizeColumn.setPrefWidth(100);

        tableView.getColumns().addAll(iconColumn, nameColumn, modifiedColumn, typeColumn, sizeColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void createGridView() {
        gridView = new GridPane();
        gridView.getStyleClass().add("grid-pane");
        gridView.setPadding(new Insets(10));
        gridView.setHgap(10);
        gridView.setVgap(10);
        gridView.setVisible(false);

        // 设置网格对齐
        for (int i = 0; i < 6; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setMinWidth(100);
            col.setPrefWidth(100);
            col.setMaxWidth(100);
            col.setHalignment(javafx.geometry.HPos.CENTER);
            gridView.getColumnConstraints().add(col);
        }

        for (int i = 0; i < 20; i++) {
            RowConstraints row = new RowConstraints();
            row.setMinHeight(100);
            row.setPrefHeight(100);
            row.setMaxHeight(100);
            row.setValignment(VPos.TOP);
            gridView.getRowConstraints().add(row);
        }
    }

    // 切换视图模式（列表 <-> 网格）
    public void switchViewMode() {
        isGridMode = !isGridMode;
        if (isGridMode) {
            selectedItemsInGrid.clear();
            root.setCenter(gridView);
            tableView.setVisible(false);
            gridView.setVisible(true);
            modeButton.setText("列表");
        } else {
            root.setCenter(tableView);
            gridView.setVisible(false);
            tableView.setVisible(true);
            modeButton.setText("网格");
        }
    }

    public void clearGridView() {
        gridView.getChildren().clear();
        selectedItemsInGrid.clear();
    }

    public Button addGridItem(FileItem item, int col, int row) {
        Button button = new Button();
        button.getStyleClass().add("grid-button");

        // 设置按钮内容
        VBox content = new VBox();
        content.setSpacing(8);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(5));

        // 添加图标
        ImageView iconView = item.getIconView(48);
        if (iconView != null) {
            content.getChildren().add(iconView);
        }

        // 添加文件名
        Label nameLabel = new Label(item.getName());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(80);
        nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setContentDisplay(ContentDisplay.CENTER);
        content.getChildren().add(nameLabel);

        button.setGraphic(content);
        button.setUserData(item);

        gridView.add(button, col, row);
        return button;  // 返回按钮引用
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

    // 示例大小格式化（可移到FileUtils）
    private String formatSize(long size) {
        if (size < 0) return "--";
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return (size / 1024) + " KB";
        else if (size < 1024 * 1024 * 1024) return (size / (1024 * 1024)) + " MB";
        else return (size / (1024 * 1024 * 1024)) + " GB";
    }

    // Getters
    public BorderPane getRoot() {
        return root;
    }

    public TreeView<Path> getTreeView() {
        return treeView;
    }

    public TableView<FileItem> getTableView() {
        return tableView;
    }

    public GridPane getGridView() {
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

    // 修改现有的getter方法，通过控件ID或类型获取
    public TextField getSearchField() {
        // 给控件添加ID，通过ID查找
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof TextField) {
                TextField field = (TextField) node;
                if (field.getPromptText() != null && field.getPromptText().equals("搜索文件...")) {
                    return field;
                }
            }
        }
        return null;
    }

    public Button getRefreshButton() {
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                if ("刷新".equals(button.getText())) {
                    return button;
                }
            }
        }
        return null;
    }

    public Button getModeButton() {
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof Button) {
                Button button = (Button) node;
                Tooltip tooltip = button.getTooltip();
                if (tooltip != null && "切换列表/网格视图".equals(tooltip.getText())) {
                    return button;
                }
            }
        }
        return null;
    }

    public ToolBar getToolBar() {
        return toolBar;
    }

}