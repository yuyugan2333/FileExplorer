package com.fileexplorer;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 剪贴板管理器，用于存储复制/剪切的文件列表，支持监听
 */
public class ClipboardManager {
    private static ClipboardManager instance;

    // 使用 ObservableList 支持监听
    private final ObservableList<Path> clipboardItems = FXCollections.observableArrayList();

    // 使用属性支持监听
    private final BooleanProperty isCutOperation = new SimpleBooleanProperty(false);
    private final IntegerProperty itemCount = new SimpleIntegerProperty(0);

    private ClipboardManager() {
        // 监听 clipboardItems 的变化，更新 itemCount
        clipboardItems.addListener((ListChangeListener<Path>) change -> {
            itemCount.set(clipboardItems.size());
        });

        // 初始化 itemCount
        itemCount.set(clipboardItems.size());
    }

    public static synchronized ClipboardManager getInstance() {
        if (instance == null) {
            instance = new ClipboardManager();
        }
        return instance;
    }

    public void setClipboard(List<Path> items, boolean isCut) {
        // 在JavaFX应用线程上更新
        javafx.application.Platform.runLater(() -> {
            this.clipboardItems.setAll(items);
            this.isCutOperation.set(isCut);
        });
    }

    public void clearClipboard() {
        javafx.application.Platform.runLater(() -> {
            this.clipboardItems.clear();
            this.isCutOperation.set(false);
        });
    }

    public List<Path> getClipboardItems() {
        return new ArrayList<>(clipboardItems);
    }

    // 新增：获取ObservableList的方法，用于监听
    public ObservableList<Path> getClipboardItemsObservable() {
        return clipboardItems;
    }

    public boolean isCutOperation() {
        return isCutOperation.get();
    }

    // 新增：获取BooleanProperty用于监听
    public BooleanProperty isCutOperationProperty() {
        return isCutOperation;
    }

    public boolean isEmpty() {
        return clipboardItems.isEmpty();
    }

    public int getItemCount() {
        return itemCount.get();
    }

    // 新增：获取IntegerProperty用于监听
    public ReadOnlyIntegerProperty itemCountProperty() {
        return itemCount;
    }

    public boolean contains(Path path) {
        return clipboardItems.contains(path);
    }

    // 新增：获取只读的布尔属性表示是否为空
    public ReadOnlyBooleanProperty emptyProperty() {
        SimpleBooleanProperty emptyProp = new SimpleBooleanProperty(clipboardItems.isEmpty());

        // 监听 clipboardItems 的变化，更新 emptyProp
        clipboardItems.addListener((ListChangeListener<Path>) change -> {
            emptyProp.set(clipboardItems.isEmpty());
        });

        return emptyProp;
    }
}