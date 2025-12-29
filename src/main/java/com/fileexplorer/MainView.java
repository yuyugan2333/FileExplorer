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

    public MainView() {
        initializeLayout();
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

        // 设置布局
        root.setTop(toolBar);
        root.setLeft(createSplitPane());
        root.setBottom(statusBar);

        // 初始显示表格视图
        root.setCenter(tableView);
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

    private SplitPane createSplitPane() {
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.2);

        // 左侧：目录树
        VBox treeBox = new VBox();
        treeBox.setPadding(new Insets(8));

        Label treeLabel = new Label("导航");
        treeLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 0 8px 0;");

        ScrollPane treeScroll = new ScrollPane(treeView);
        treeScroll.setFitToWidth(true);
        treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setStyle("-fx-background: white; -fx-border-color: transparent;");

        treeBox.getChildren().addAll(treeLabel, treeScroll);
        VBox.setVgrow(treeScroll, Priority.ALWAYS);

        // 右侧：文件视图区域
        VBox viewBox = new VBox();
        viewBox.setPadding(new Insets(8));

        // 创建标签页容器
        TabPane tabPane = new TabPane();

        // 主标签页
        Tab mainTab = new Tab("主视图");
        mainTab.setClosable(false);

        // 这里可以添加更多标签页...

        tabPane.getTabs().add(mainTab);

        viewBox.getChildren().add(tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        splitPane.getItems().addAll(treeBox, viewBox);
        return splitPane;
    }

    private void createTreeView() {
        treeView = new TreeView<>();
        treeView.getStyleClass().add("tree-view");

        // 设置TreeCell工厂以显示图标
        treeView.setCellFactory(param -> new TreeCell<Path>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getFileName() != null ?
                            item.getFileName().toString() : item.toString());

                    // 为目录添加图标
                    if (Files.isDirectory(item)) {
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
        nameColumn.setCellFactory(col -> new TableCell<FileItem, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);

                    // 获取对应的FileItem
                    FileItem fileItem = getTableView().getItems().get(getIndex());
                    if (fileItem != null) {
                        ImageView icon = fileItem.getIconView(16);
                        if (icon != null) {
                            setGraphic(icon);
                        }
                    }
                }
            }
        });
        nameColumn.setPrefWidth(300);

        // 修改时间列
        TableColumn<FileItem, String> modifiedColumn = new TableColumn<>("修改日期");
        modifiedColumn.setCellValueFactory(new PropertyValueFactory<>("modifiedTime"));
        modifiedColumn.setPrefWidth(150);

        // 类型列
        TableColumn<FileItem, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(120);

        // 大小列
        TableColumn<FileItem, Long> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("size"));
        sizeColumn.setCellFactory(col -> new TableCell<FileItem, Long>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    setText(formatSize(size));
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

    public void addGridItem(FileItem item, int col, int row) {
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

        // 添加选中状态监听
        button.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                selectedItemsInGrid.add(item);
                button.getStyleClass().add("selected");
            } else {
                selectedItemsInGrid.remove(item);
                button.getStyleClass().remove("selected");
            }
        });

        gridView.add(button, col, row);
    }

    public List<FileItem> getSelectedFileItems() {
        if (isGridMode) {
            return new ArrayList<>(selectedItemsInGrid);
        } else {
            return new ArrayList<>(getTableView().getSelectionModel().getSelectedItems());
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
                if ("切换模式".equals(button.getText())) {
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