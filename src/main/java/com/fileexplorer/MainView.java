package com.fileexplorer;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainView {
    private BorderPane root;
    private ToolBar toolBar;
    private TreeView<Path> treeView;
    private TableView<FileItem> tableView;
    private GridPane gridView; // 用于网格模式
    private boolean isGridMode = false; // 默认列表模式
    private Button backButton;
    private Button forwardButton;
    private Button upButton; // 上一级目录
    private TextField pathField;

    public MainView() {
        initializeLayout();
    }

    private void initializeLayout() {
        root = new BorderPane();

        // 创建工具栏
        createToolBar();

        // 创建左侧目录树
        createTreeView();

        // 创建右侧文件列表（默认TableView）
        createTableView();

        // 创建网格视图（初始隐藏）
        gridView = new GridPane();
        gridView.setHgap(10);
        gridView.setVgap(10);
        gridView.setVisible(false);

        // 设置布局
        root.setTop(toolBar);
        root.setLeft(treeView);
        root.setCenter(tableView);
    }

    private void createToolBar() {
        toolBar = new ToolBar();

        // 导航按钮
        backButton = new Button("←");
        backButton.setTooltip(new Tooltip("后退"));

        forwardButton = new Button("→");
        forwardButton.setTooltip(new Tooltip("前进"));

        upButton = new Button("↑");
        upButton.setTooltip(new Tooltip("上一级"));

        // 当前路径显示和输入
        pathField = new TextField();
        pathField.setPromptText("输入路径...");
        pathField.setPrefWidth(400);

        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("搜索文件...");
        searchField.setPrefWidth(200);

        // 刷新按钮
        Button refreshButton = new Button("刷新");

        // 模式切换按钮
        Button modeButton = new Button("切换模式");

        // 添加分隔符
        Separator separator1 = new Separator();
        separator1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        toolBar.getItems().addAll(
                backButton, forwardButton, upButton,
                separator1,
                pathField,
                separator2,
                searchField, refreshButton, modeButton
        );
    }

    private void createTreeView() {
        treeView = new TreeView<>();
        treeView.setMinWidth(200);
        // 自定义TreeCell以显示文件夹图标（可选）
//        treeView.setCellFactory(new Callback<>() {
//            @Override
//            public TreeCell<Path> call(TreeView<Path> param) {
//                return new TreeCell<>() {
//                    @Override
//                    protected void updateItem(Path item, boolean empty) {
//                        super.updateItem(item, empty);
//                        if (empty || item == null) {
//                            setText(null);
//                            setGraphic(null);
//                        } else {
//                            setText(item.getFileName() == null ? item.toString() : item.getFileName().toString());
//                            // 添加图标（示例，使用本地图标或系统图标）
//                            ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/folder_icon.png"))); // 如果有图标资源
//                            setGraphic(icon);
//                        }
//                    }
//                };
//            }
//        });
    }

    private void createTableView() {
        tableView = new TableView<>();

        // 文件名列
        TableColumn<FileItem, String> nameColumn = new TableColumn<>("文件名");
        nameColumn.setCellValueFactory(p -> p.getValue().nameProperty());

        // 大小列
        TableColumn<FileItem, Long> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getSize()));
        sizeColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long size, boolean empty) {
                super.updateItem(size, empty);
                if (empty || size == null) {
                    setText(null);
                } else {
                    setText(formatSize(size)); // 使用FileUtils.formatSize
                }
            }
        });

        // 修改时间列
        TableColumn<FileItem, String> modifiedColumn = new TableColumn<>("修改时间");
        modifiedColumn.setCellValueFactory(p -> new SimpleObjectProperty<>(p.getValue().getModifiedTime().toString()));

        tableView.getColumns().addAll(nameColumn, sizeColumn, modifiedColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    // 切换视图模式（列表 <-> 网格）
    public void switchViewMode() {
        isGridMode = !isGridMode;
        if (isGridMode) {
            root.setCenter(gridView);
            tableView.setVisible(false);
            gridView.setVisible(true);
            // 在Controller中填充gridView
        } else {
            root.setCenter(tableView);
            gridView.setVisible(false);
            tableView.setVisible(true);
        }
    }

    public List<FileItem> getSelectedFileItems() {
        return new ArrayList<>(getTableView().getSelectionModel().getSelectedItems());
    }

    public FileItem getSelectedFileItem() {
        return getTableView().getSelectionModel().getSelectedItem();
    }

    // 示例大小格式化（可移到FileUtils）
    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return (size / 1024) + " KB";
        else return (size / (1024 * 1024)) + " MB";
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
        // 现在是第5个元素（索引4），因为前面添加了控件
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof TextField && ((TextField) node).getPromptText().equals("搜索文件...")) {
                return (TextField) node;
            }
        }
        return null;
    }

    public Button getRefreshButton() {
        // 现在是第6个元素（索引5）
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof Button && ((Button) node).getText().equals("刷新")) {
                return (Button) node;
            }
        }
        return null;
    }

    public Button getModeButton() {
        // 现在是第7个元素（索引6）
        for (javafx.scene.Node node : toolBar.getItems()) {
            if (node instanceof Button && ((Button) node).getText().equals("切换模式")) {
                return (Button) node;
            }
        }
        return null;
    }

}