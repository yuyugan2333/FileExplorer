package com.fileexplorer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 独立的后台文件操作任务类，以字节为单位计算进度
 */
public class FileOperationTask extends Task<Void> {
    public enum OperationType {
        COPY,
        MOVE,
        DELETE,
        EXTRACT
    }

    private final OperationType type;
    private final Path source;
    private final Path target;
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicLong bytesProcessed = new AtomicLong(0);
    private Consumer<String> messageUpdater;
    private Stage ownerStage;

    // 用于取消操作的标志
    private volatile boolean cancelledByUser = false;

    public FileOperationTask(OperationType type, Path source, Path target) {
        this.type = type;
        this.source = source;
        this.target = target;
    }

    public void setMessageUpdater(Consumer<String> updater) {
        this.messageUpdater = updater;
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    @Override
    protected Void call() throws Exception {
        try {
            // 计算总字节数
            calculateTotalBytes();

            switch (type) {
                case COPY:
                    copyOperation();
                    break;
                case MOVE:
                    moveOperation();
                    break;
                case DELETE:
                    deleteOperation();
                    break;
                case EXTRACT:
                    extractOperation();
                    break;
            }

            if (!cancelledByUser) {
                updateMessageSafe("操作完成");
                updateProgress(1.0, 1.0);
            }

        } catch (Exception e) {
            if (!cancelledByUser) {
                throw e;
            }
        }

        return null;
    }

    /**
     * 计算总字节数
     */
    private void calculateTotalBytes() throws IOException {
        updateMessageSafe("正在计算文件大小...");

        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    totalBytes.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        } else {
            totalBytes.set(Files.size(source));
        }

        updateMessageSafe("准备开始操作...");
    }

    private void updateMessageSafe(String message) {
        if (messageUpdater != null) {
            Platform.runLater(() -> messageUpdater.accept(message));
        }
        updateMessage(message);
    }

    /**
     * 更新进度（基于字节数）
     */
    private void updateProgressSafe(long bytes) {
        bytesProcessed.addAndGet(bytes);
        if (totalBytes.get() > 0) {
            double progress = (double) bytesProcessed.get() / totalBytes.get();
            updateProgress(progress, 1.0);
        }
    }

    /**
     * 复制操作
     */
    private void copyOperation() throws IOException {
        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            copyFile(source, target);
        }
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }

                Path relative = sourceDir.relativize(dir);
                Path targetPath = targetDir.resolve(relative);

                if (!Files.exists(targetPath)) {
                    Files.createDirectories(targetPath);
                }

                updateMessageSafe("创建目录: " + targetPath.getFileName());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }

                Path relative = sourceDir.relativize(file);
                Path targetFile = targetDir.resolve(relative);

                copyFileWithProgress(file, targetFile);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void copyFileWithProgress(Path sourceFile, Path targetFile) throws IOException {
        updateMessageSafe("复制文件: " + sourceFile.getFileName());

        long fileSize = Files.size(sourceFile);
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        updateProgressSafe(fileSize);
    }

    private void copyFile(Path sourceFile, Path targetFile) throws IOException {
        copyFileWithProgress(sourceFile, targetFile);
    }

    /**
     * 移动操作（先复制后删除）
     */
    private void moveOperation() throws IOException {
        updateMessageSafe("开始移动文件...");

        // 先复制
        copyOperation();

        if (!isCancelled()) {
            // 然后删除源文件
            deleteOperation();
        }
    }

    /**
     * 删除操作
     */
    private void deleteOperation() throws IOException {
        if (Files.isDirectory(source)) {
            deleteDirectory(source);
        } else {
            deleteFile(source);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }

                deleteFile(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }

                if (exc == null) {
                    Files.delete(dir);
                    updateMessageSafe("删除目录: " + dir.getFileName());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteFile(Path file) throws IOException {
        long fileSize = Files.size(file);
        Files.delete(file);
        updateMessageSafe("删除文件: " + file.getFileName());
        updateProgressSafe(fileSize);
    }

    /**
     * 解压操作
     */
    private void extractOperation() throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        // 使用URI创建文件系统（避免方法重载歧义）
        URI zipUri = URI.create("jar:" + source.toUri());

        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Collections.emptyMap())) {
            Path root = zipFs.getPath("/");

            // 先计算压缩包内总大小
            long totalSize = calculateZipSize(zipUri);
            if (totalSize > 0) {
                totalBytes.set(totalSize);
            }

            // 解压文件
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    Path relative = root.relativize(dir);
                    Path targetDir = target.resolve(relative.toString());

                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    Path relative = root.relativize(file);
                    Path targetFile = target.resolve(relative.toString());

                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);

                    long fileSize = attrs.size();
                    updateProgressSafe(fileSize);
                    updateMessageSafe("解压文件: " + file.getFileName());

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IOException("无法解压文件: " + source + " - " + e.getMessage(), e);
        }
    }

    /**
     * 计算压缩包内文件总大小
     */
    private long calculateZipSize(URI zipUri) throws IOException {
        final AtomicLong size = new AtomicLong(0);

        try (FileSystem zipFs = FileSystems.newFileSystem(zipUri, Collections.emptyMap())) {
            Path root = zipFs.getPath("/");

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return size.get();
    }

    @Override
    protected void cancelled() {
        cancelledByUser = true;
        Platform.runLater(() -> {
            showAlert("操作取消", "用户取消了操作");
        });
    }

    @Override
    protected void failed() {
        Platform.runLater(() -> {
            Throwable ex = getException();
            showAlert("操作失败", "操作失败: " + (ex != null ? ex.getMessage() : "未知错误"));
        });
    }

    @Override
    protected void succeeded() {
        Platform.runLater(() -> {
            showAlert("操作成功", "文件操作已成功完成");
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (ownerStage != null) {
            alert.initOwner(ownerStage);
        }
        alert.showAndWait();
    }

    /**
     * 创建进度对话框
     */
    public Dialog<Void> createProgressDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(type.toString() + " 操作");
        dialog.setHeaderText("正在处理: " + source.getFileName());

        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.progressProperty().bind(this.progressProperty());

        Label messageLabel = new Label();
        messageLabel.textProperty().bind(this.messageProperty());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));
        grid.add(new Label("进度:"), 0, 0);
        grid.add(progressBar, 1, 0);
        grid.add(new Label("状态:"), 0, 1);
        grid.add(messageLabel, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);

        // 取消按钮事件
        dialog.setOnCloseRequest(event -> {
            if (this.isRunning()) {
                this.cancel(true);
            }
        });

        return dialog;
    }
}