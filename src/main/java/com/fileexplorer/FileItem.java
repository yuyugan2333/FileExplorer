package com.fileexplorer;

import javafx.beans.property.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class FileItem {
    private final StringProperty name;
    private final StringProperty type;
    private final LongProperty size;
    private final ObjectProperty<LocalDateTime> modifiedTime;
    private final Path path;
    private final boolean isDirectory;
    private final ObjectProperty<Image> icon;

    public FileItem(Path path) {
        this.path = path;
        // 特殊处理"此电脑"路径
        if (path.toString().equals("此电脑")) {
            this.name = new SimpleStringProperty("此电脑");
            this.isDirectory = true;
            this.type = new SimpleStringProperty("系统文件夹");
            this.size = new SimpleLongProperty(-1);
            this.modifiedTime = new SimpleObjectProperty<>(LocalDateTime.now());
        } else {
            this.name = new SimpleStringProperty(path.getFileName() != null ?
                    path.getFileName().toString() : path.toString());
            this.isDirectory = Files.isDirectory(path);

            String tempType;
            long tempSize = 0;
            LocalDateTime tempModified = LocalDateTime.now();

            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                tempModified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(),
                        ZoneId.systemDefault());

                if (isDirectory) {
                    tempType = "文件夹";
                    tempSize = -1;
                } else {
                    // 使用FileUtils中的方法获取文件类型描述
                    tempType = FileUtils.getFileTypeDescription(path);
                    tempSize = attrs.size();
                }
            } catch (IOException e) {
                tempType = "未知";
                System.err.println("无法读取文件属性: " + path + " - " + e.getMessage());
            }

            this.type = new SimpleStringProperty(tempType);
            this.size = new SimpleLongProperty(tempSize);
            this.modifiedTime = new SimpleObjectProperty<>(tempModified);
        }

        // 初始化图标
        Image tempIcon = IconManager.getInstance().getIconForFile(path);
        this.icon = new SimpleObjectProperty<>(tempIcon);
    }

    public void setIcon(Image icon) {
        this.icon.set(icon);
    }

    public ObjectProperty<Image> iconProperty() {
        return icon;
    }

    public Image getIcon() {
        return icon.get();
    }

    public ImageView getIconView(int size) {
        Image icon = getIcon();
        if (icon != null) {
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return null;
    }

    // Getters for properties (用于JavaFX绑定)
    public boolean isDrive() {
        return false; // 默认不是驱动器，在首页创建时覆盖
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty typeProperty() {
        return type;
    }

    public LongProperty sizeProperty() {
        return size;
    }

    public ObjectProperty<LocalDateTime> modifiedTimeProperty() {
        return modifiedTime;
    }

    // Simple getters (如果不需要Property)
    public String getName() {
        return name.get();
    }

    public String getType() {
        return type.get();
    }

    public long getSize() {
        return size.get();
    }

    public LocalDateTime getModifiedTime() {
        return modifiedTime.get();
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    // Setter 示例（如果需要更新）
    public void setSize(long newSize) {
        size.set(newSize);
    }
}