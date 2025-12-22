package com.fileexplorer;

import javafx.beans.property.*;
import java.io.IOException;
import java.nio.file.*;
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

    public FileItem(Path path) {
        this.path = path;
        this.name = new SimpleStringProperty(path.getFileName() != null ? path.getFileName().toString() : path.toString());
        this.isDirectory = Files.isDirectory(path);

        String tempType;
        long tempSize = 0;
        LocalDateTime tempModified = LocalDateTime.now();

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            tempModified = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

            if (isDirectory) {
                tempType = "文件夹";
                // 文件夹大小可稍后递归计算，这里暂设为 -1 表示未计算
                tempSize = -1;
            } else {
                tempType = getFileType(path);
                tempSize = attrs.size();
            }
        } catch (IOException e) {
            tempType = "未知";
            // 处理异常：权限问题等，显示默认值
            System.err.println("无法读取文件属性: " + path + " - " + e.getMessage());
        }

        this.type = new SimpleStringProperty(tempType);
        this.size = new SimpleLongProperty(tempSize);
        this.modifiedTime = new SimpleObjectProperty<>(tempModified);
    }

    private String getFileType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null ? contentType : "文件";
        } catch (IOException e) {
            return "未知";
        }
    }

    // Getters for properties (用于JavaFX绑定)
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