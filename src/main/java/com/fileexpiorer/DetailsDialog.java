package com.fileexpiorer;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

public class DetailsDialog extends Dialog<Void> {
    public DetailsDialog(Path path) {
        setTitle("文件属性");
        setHeaderText(path.getFileName() != null ? path.getFileName().toString() : path.toString());

        // 创建GridPane布局
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);

            // 通用属性
            addRow(grid, 0, "路径:", path.toAbsolutePath().toString());
            addRow(grid, 1, "类型:", Files.isDirectory(path) ? "文件夹" : getFileType(path));
            addRow(grid, 2, "创建时间:", LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault()).toString());
            addRow(grid, 3, "修改时间:", LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()).toString());
            addRow(grid, 4, "访问时间:", LocalDateTime.ofInstant(attrs.lastAccessTime().toInstant(), ZoneId.systemDefault()).toString());

            if (Files.isDirectory(path)) {
                // 文件夹特定属性
                AtomicLong size = new AtomicLong(0);
                AtomicLong fileCount = new AtomicLong(0);
                AtomicLong dirCount = new AtomicLong(0);

                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                        size.addAndGet(a.size());
                        fileCount.incrementAndGet();
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                        if (!dir.equals(path)) { // 不计入自身
                            dirCount.incrementAndGet();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                addRow(grid, 5, "大小:", formatSize(size.get()));
                addRow(grid, 6, "包含文件数:", fileCount.get() + " 个");
                addRow(grid, 7, "包含子文件夹数:", dirCount.get() + " 个");
            } else {
                // 文件特定属性
                addRow(grid, 5, "大小:", formatSize(attrs.size()));
            }

            // 磁盘空间（对于根路径或文件所在磁盘）
            FileStore store = Files.getFileStore(path);
            addRow(grid, 8, "磁盘总空间:", formatSize(store.getTotalSpace()));
            addRow(grid, 9, "磁盘可用空间:", formatSize(store.getUsableSpace()));

        } catch (IOException e) {
            addRow(grid, 0, "错误:", "无法读取属性: " + e.getMessage());
        }

        getDialogPane().setContent(grid);
        getDialogPane().getButtonTypes().add(ButtonType.OK);
    }

    private void addRow(GridPane grid, int row, String labelText, String valueText) {
        grid.add(new Label(labelText), 0, row);
        grid.add(new Label(valueText), 1, row);
    }

    private String getFileType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType != null ? contentType : "未知文件";
        } catch (IOException e) {
            return "未知";
        }
    }

    private String formatSize(long size) {
        if (size < 0) return "--";
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }
}