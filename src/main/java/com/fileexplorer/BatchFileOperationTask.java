package com.fileexplorer;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.*;

/**
 * 批量文件操作任务，使用统一的线程池管理器
 */
public class BatchFileOperationTask extends Task<Void> {
    public enum OperationType {
        COPY,
        MOVE,
        DELETE
    }

    private final OperationType type;
    private final List<Path> sourcePaths;
    private final Path targetDir;
    private final Stage ownerStage;

    private final IntegerProperty completedFiles = new SimpleIntegerProperty(0);
    private final IntegerProperty failedFiles = new SimpleIntegerProperty(0);
    private final LongProperty processedBytes = new SimpleLongProperty(0);
    private final LongProperty totalBytes = new SimpleLongProperty(0);

    // 用于保存提交的任务Future，以便可以取消
    private final List<Future<?>> submittedTasks = new CopyOnWriteArrayList<>();

    public BatchFileOperationTask(OperationType type, List<Path> sourcePaths, Path targetDir, Stage ownerStage) {
        this.type = type;
        this.sourcePaths = sourcePaths;
        this.targetDir = targetDir;
        this.ownerStage = ownerStage;
    }

    // 添加getter方法用于属性访问
    public IntegerProperty completedFilesProperty() { return completedFiles; }
    public IntegerProperty failedFilesProperty() { return failedFiles; }
    public LongProperty processedBytesProperty() { return processedBytes; }
    public LongProperty totalBytesProperty() { return totalBytes; }

    @Override
    protected Void call() throws Exception {
        ThreadPoolManager threadPoolManager = ThreadPoolManager.getInstance();

        try {
            calculateTotalSize();

            if (isCancelled()) return null;

            executeBatchOperation(threadPoolManager);

            if (!isCancelled()) {
                updateMessage("操作完成");
                updateProgress(1.0, 1.0);
            }

        } catch (Exception e) {
            if (!isCancelled()) {
                throw e;
            }
        } finally {
            // 取消所有已提交但未完成的任务
            cancelSubmittedTasks();
        }

        return null;
    }

    /**
     * 计算所有文件的总大小
     */
    private void calculateTotalSize() throws IOException, InterruptedException {
        updateMessage("正在计算文件总大小...");

        // 使用CompletableFuture并行计算文件大小
        List<CompletableFuture<Long>> futures = new CopyOnWriteArrayList<>();
        ExecutorService executor = ThreadPoolManager.getInstance().getFileOperationExecutor();

        for (Path source : sourcePaths) {
            if (isCancelled()) break;

            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return calculateFileSize(source);
                } catch (IOException e) {
                    return 0L;
                }
            }, executor);

            submittedTasks.add(future);
            futures.add(future);
        }

        long totalSize = 0;
        for (CompletableFuture<Long> future : futures) {
            if (isCancelled()) break;
            try {
                totalSize += future.get();
            } catch (Exception e) {
                // 忽略错误
            }
        }

        // 在JavaFX应用线程上更新属性
        final long finalTotalSize = totalSize;
        Platform.runLater(() -> {
            totalBytes.set(finalTotalSize);
        });

        updateMessage(String.format("总大小: %s，准备开始操作...",
                FileUtils.formatSize(finalTotalSize)));
    }

    /**
     * 计算单个文件/文件夹大小
     */
    private long calculateFileSize(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            final long[] size = {0};
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    size[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
            return size[0];
        } else {
            return Files.size(path);
        }
    }

    /**
     * 执行批量操作
     */
    private void executeBatchOperation(ThreadPoolManager threadPoolManager) throws Exception {
        updateMessage("开始执行操作...");

        ExecutorService executor = threadPoolManager.getFileOperationExecutor();
        List<CompletableFuture<Void>> futures = new CopyOnWriteArrayList<>();

        for (Path source : sourcePaths) {
            if (isCancelled()) break;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    executeSingleOperation(source);
                    Platform.runLater(() -> {
                        completedFiles.set(completedFiles.get() + 1);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        failedFiles.set(failedFiles.get() + 1);
                    });
                    Platform.runLater(() -> {
                        showWarning("操作失败", "处理文件失败: " + source.getFileName() +
                                " - " + e.getMessage());
                    });
                    throw new CompletionException(e);
                }
            }, executor);

            submittedTasks.add(future);
            futures.add(future);
        }

        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get();
        } catch (CancellationException e) {
            // 任务被取消，正常退出
        } catch (CompletionException e) {
            // 有任务失败，继续处理其他任务
        }

        updateMessage(String.format("操作完成。成功: %d, 失败: %d",
                completedFiles.get(), failedFiles.get()));
    }

    /**
     * 执行单个文件操作
     */
    private void executeSingleOperation(Path source) throws IOException {
        String fileName = source.getFileName().toString();
        Path target = targetDir != null ? targetDir.resolve(fileName) : null;

        switch (type) {
            case COPY:
                updateMessage("正在复制: " + fileName);
                copyFileOrDirectory(source, target);
                break;

            case MOVE:
                updateMessage("正在移动: " + fileName);
                moveFileOrDirectory(source, target);
                break;

            case DELETE:
                updateMessage("正在删除: " + fileName);
                deleteFileOrDirectory(source);
                break;
        }
    }

    /**
     * 复制文件或目录
     */
    private void copyFileOrDirectory(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    Path targetDir = target.resolve(source.relativize(dir));
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

                    Path targetFile = target.resolve(source.relativize(file));
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);

                    // 更新进度
                    long fileSize = attrs.size();
                    Platform.runLater(() -> {
                        processedBytes.set(processedBytes.get() + fileSize);
                        updateProgressSafe();
                    });

                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            long fileSize = Files.size(source);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            // 更新进度
            Platform.runLater(() -> {
                processedBytes.set(processedBytes.get() + fileSize);
                updateProgressSafe();
            });
        }
    }

    /**
     * 移动文件或目录
     */
    private void moveFileOrDirectory(Path source, Path target) throws IOException {
        // 尝试直接移动（如果在同一磁盘上会更快）
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

            long fileSize = Files.isDirectory(source) ?
                    calculateFileSize(target) : Files.size(target);

            Platform.runLater(() -> {
                processedBytes.set(processedBytes.get() + fileSize);
                updateProgressSafe();
            });

        } catch (IOException e) {
            // 如果移动失败，回退到复制+删除
            updateMessage("跨磁盘移动，使用复制+删除方式: " + source.getFileName());
            copyFileOrDirectory(source, target);
            deleteFileOrDirectory(source);
        }
    }

    /**
     * 删除文件或目录
     */
    private void deleteFileOrDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    Files.delete(file);

                    // 更新进度
                    long fileSize = attrs.size();
                    Platform.runLater(() -> {
                        processedBytes.set(processedBytes.get() + fileSize);
                        updateProgressSafe();
                    });

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }

                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            long fileSize = Files.size(path);
            Files.delete(path);

            // 更新进度
            Platform.runLater(() -> {
                processedBytes.set(processedBytes.get() + fileSize);
                updateProgressSafe();
            });
        }
    }

    /**
     * 安全更新进度
     */
    private void updateProgressSafe() {
        long total = totalBytes.get();
        long processed = processedBytes.get();
        if (total > 0) {
            double progress = (double) processed / total;
            updateProgress(progress, 1.0);
        }
    }

    /**
     * 取消所有已提交的任务
     */
    private void cancelSubmittedTasks() {
        for (Future<?> future : submittedTasks) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
        submittedTasks.clear();
    }

    @Override
    protected void cancelled() {
        updateMessage("操作已取消");
        cancelSubmittedTasks();
    }

    @Override
    protected void failed() {
        Platform.runLater(() -> {
            Throwable ex = getException();
            String message = "操作失败: " + (ex != null ? ex.getMessage() : "未知错误");
            showAlert("操作失败", message);
        });
    }

    @Override
    protected void succeeded() {
        Platform.runLater(() -> {
            if (failedFiles.get() > 0) {
                showAlert("操作完成", String.format(
                        "操作部分完成。成功: %d, 失败: %d",
                        completedFiles.get(), failedFiles.get()));
            } else {
                showAlert("操作成功", "文件操作已成功完成");
            }
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

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
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
        dialog.setTitle(getOperationTitle());
        dialog.setHeaderText("正在处理 " + sourcePaths.size() + " 个项目");

        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(300);
        progressBar.progressProperty().bind(this.progressProperty());

        Label messageLabel = new Label();
        messageLabel.textProperty().bind(this.messageProperty());

        // 文件计数标签
        Label fileCountLabel = new Label();
        // 创建自定义绑定来更新文件计数
        completedFiles.addListener((observable, oldValue, newValue) -> {
            fileCountLabel.setText(String.format("已完成: %d / %d", newValue.intValue(), sourcePaths.size()));
        });
        fileCountLabel.setText("已完成: 0 / " + sourcePaths.size());

        // 大小进度标签
        Label sizeLabel = new Label();
        // 创建自定义绑定来更新大小进度
        processedBytes.addListener((observable, oldValue, newValue) -> {
            long total = totalBytes.get();
            if (total > 0) {
                double percentage = (double) newValue.longValue() / total * 100;
                sizeLabel.setText(String.format("%.1f%% (%s / %s)",
                        percentage,
                        FileUtils.formatSize(newValue.longValue()),
                        FileUtils.formatSize(total)));
            } else {
                sizeLabel.setText("计算大小中...");
            }
        });
        sizeLabel.setText("计算大小中...");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        int row = 0;
        grid.add(new Label("进度:"), 0, row);
        grid.add(progressBar, 1, row++);
        grid.add(new Label("状态:"), 0, row);
        grid.add(messageLabel, 1, row++);
        grid.add(new Label("文件:"), 0, row);
        grid.add(fileCountLabel, 1, row++);
        grid.add(new Label("大小:"), 0, row);
        grid.add(sizeLabel, 1, row);

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

    private String getOperationTitle() {
        switch (type) {
            case COPY: return "复制文件";
            case MOVE: return "移动文件";
            case DELETE: return "删除文件";
            default: return "文件操作";
        }
    }
}