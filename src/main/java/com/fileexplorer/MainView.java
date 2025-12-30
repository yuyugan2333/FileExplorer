package com.fileexplorer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主视图类，管理UI布局和组件。
 */
public class MainView {
    private BorderPane root;
    private ToolBar toolBar;
    private TreeView<Path> treeView;
    private TableView<FileItem> tableView;
    private FlowPane gridView;
    public boolean isGridMode = false;
    private Button backButton;
    private Button forwardButton;
    private Button upButton;
    private TextField pathField;
    private TextField searchField;
    private ComboBox<String> searchModeComboBox;
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

        createToolBar();
        createStatusBar();
        createTreeView();
        createTableView();
        createGridView();

        tableScroll = new ScrollPane(tableView);
        tableScroll.setFitToWidth(true);
        tableScroll.setFitToHeight(true);
        tableScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tableScroll.getStyleClass().add("table-scroll-pane");

        gridScroll = new ScrollPane();
        gridScroll.setContent(gridView);
        gridScroll.setFitToWidth(true);
        gridScroll.setFitToHeight(false);
        gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        gridScroll.getStyleClass().add("grid-scroll-pane");
        gridScroll.setVisible(false);

        StackPane viewContainer = new StackPane();
        viewContainer.getChildren().addAll(tableScroll, gridScroll);

        root.setTop(toolBar);
        root.setLeft(createTreeContainer());
        root.setCenter(viewContainer);
        root.setBottom(statusBar);
    }

    private void createToolBar() {
        toolBar = new ToolBar();
        toolBar.getStyleClass().add("tool-bar");

        backButton = createToolbarButton("←", "后退", "nav-button");
        forwardButton = createToolbarButton("→", "前进", "nav-button");
        upButton = createToolbarButton("↑", "上一级", "nav-button");

        Separator separator1 = new Separator();
        separator1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        pathField = new TextField();
        pathField.getStyleClass().add("path-field");
        pathField.setPromptText("输入路径...");
        pathField.setPrefWidth(400);
        pathField.setId("pathField");

        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        searchField = new TextField();
        searchField.getStyleClass().add("search-field");
        searchField.setPromptText("搜索文件...");
        searchField.setPrefWidth(200);
        searchField.setId("searchField");

        searchModeComboBox = new ComboBox<>();
        searchModeComboBox.getItems().addAll("通配符匹配", "字符串匹配", "文本文件内容通配符匹配", "搜索图片", "搜索音频", "搜索视频", "搜索文档", "搜索压缩文件", "检索大文件(100MB+,可能需要等待)");
        searchModeComboBox.setValue("字符串匹配");
        searchModeComboBox.setPrefWidth(150);
        searchModeComboBox.setId("searchModeComboBox");

        refreshButton = createToolbarButton("刷新", "刷新", "text-button");
        refreshButton.setId("refreshButton");

        modeButton = createToolbarButton("列表", "切换列表/网格视图", "text-button");
        modeButton.setId("modeButton");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolBar.getItems().addAll(backButton, forwardButton, upButton, separator1, pathField, separator2, searchField, searchModeComboBox, refreshButton, modeButton, spacer);
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

    private ScrollPane createTreeContainer() {
        ScrollPane treeScroll = new ScrollPane(treeView);
        treeScroll.setFitToWidth(true);
        treeScroll.setFitToHeight(true);
        treeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        treeScroll.setStyle("-fx-background: white; -fx-border-color: transparent;");

        treeScroll.setMinWidth(250);
        treeScroll.setPrefWidth(300);

        return treeScroll;
    }

    private void createTreeView() {
        treeView = new TreeView<>();
        treeView.getStyleClass().add("tree-view");

        TreeItem<Path> rootItem = new TreeItem<>(Paths.get("此电脑"));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

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
    }

    private void createTableView() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("table-view");

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
    }

    private void createGridView() {
        gridView = new FlowPane();
        gridView.getStyleClass().add("grid-pane");
        gridView.setPadding(new Insets(10));
        gridView.setHgap(10);
        gridView.setVgap(10);
    }

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

    public BorderPane getRoot() {
        return root;
    }

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
        return (TextField) toolBar.lookup("#pathField");
    }

    public TextField getSearchField() {
        return (TextField) toolBar.lookup("#searchField");
    }

    public ComboBox<String> getSearchModeComboBox() {
        return (ComboBox<String>) toolBar.lookup("#searchModeComboBox");
    }

    public Button getRefreshButton() {
        return (Button) toolBar.lookup("#refreshButton");
    }

    public Button getModeButton() {
        return (Button) toolBar.lookup("#modeButton");
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public ToolBar getToolBar() {
        return toolBar;
    }
}