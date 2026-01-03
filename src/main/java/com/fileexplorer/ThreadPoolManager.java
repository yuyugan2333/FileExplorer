package com.fileexplorer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池管理器，统一管理应用程序中的所有线程池
 */
public class ThreadPoolManager {
    private static ThreadPoolManager instance;

    // 文件操作线程池（固定大小，用于IO密集型操作）
    private final ExecutorService fileOperationExecutor;

    // UI更新线程池（单线程，用于UI相关操作）
    private final ExecutorService uiUpdateExecutor;

    // 后台任务线程池（用于计算密集型任务）
    private final ExecutorService backgroundTaskExecutor;

    // 定时任务线程池
    private final ScheduledExecutorService scheduledExecutor;

    // 统计活跃任务数
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    private ThreadPoolManager() {
        // 文件操作线程池 - IO密集型，线程数可以多一些
        int fileThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        fileOperationExecutor = Executors.newFixedThreadPool(fileThreads, new NamedThreadFactory("FileOp-"));

        // UI更新线程池 - 单线程确保UI操作顺序执行
        uiUpdateExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("UI-"));

        // 后台任务线程池 - CPU密集型，线程数与CPU核心数相同
        int cpuThreads = Runtime.getRuntime().availableProcessors();
        backgroundTaskExecutor = Executors.newFixedThreadPool(cpuThreads, new NamedThreadFactory("Background-"));

        // 定时任务线程池
        scheduledExecutor = Executors.newScheduledThreadPool(2, new NamedThreadFactory("Scheduled-"));
    }

    public static synchronized ThreadPoolManager getInstance() {
        if (instance == null) {
            instance = new ThreadPoolManager();
        }
        return instance;
    }

    /**
     * 提交文件操作任务
     */
    public void submitFileOperation(Runnable task) {
        activeTasks.incrementAndGet();
        fileOperationExecutor.submit(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    /**
     * 提交文件操作任务并返回Future
     */
    public Future<?> submitFileOperationFuture(Runnable task) {
        activeTasks.incrementAndGet();
        return fileOperationExecutor.submit(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    /**
     * 提交UI更新任务（在UI线程中执行）
     */
    public void submitUIUpdate(Runnable task) {
        uiUpdateExecutor.submit(() -> {
            try {
                activeTasks.incrementAndGet();
                javafx.application.Platform.runLater(() -> {
                    try {
                        task.run();
                    } finally {
                        activeTasks.decrementAndGet();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 提交后台计算任务
     */
    public void submitBackgroundTask(Runnable task) {
        activeTasks.incrementAndGet();
        backgroundTaskExecutor.submit(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    /**
     * 获取文件操作线程池（供高级使用）
     */
    public ExecutorService getFileOperationExecutor() {
        return fileOperationExecutor;
    }

    /**
     * 获取UI更新线程池
     */
    public ExecutorService getUiUpdateExecutor() {
        return uiUpdateExecutor;
    }

    /**
     * 获取后台任务线程池
     */
    public ExecutorService getBackgroundTaskExecutor() {
        return backgroundTaskExecutor;
    }

    /**
     * 获取定时任务线程池
     */
    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    /**
     * 获取活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    /**
     * 获取文件操作线程池的活跃线程数
     */
    public int getFileOperationPoolActiveCount() {
        if (fileOperationExecutor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) fileOperationExecutor).getActiveCount();
        }
        return 0;
    }

    /**
     * 获取文件操作线程池的任务队列大小
     */
    public int getFileOperationPoolQueueSize() {
        if (fileOperationExecutor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) fileOperationExecutor).getQueue().size();
        }
        return 0;
    }

    /**
     * 优雅关闭所有线程池
     */
    public void shutdown() {
        fileOperationExecutor.shutdown();
        uiUpdateExecutor.shutdown();
        backgroundTaskExecutor.shutdown();
        scheduledExecutor.shutdown();
    }

    /**
     * 立即关闭所有线程池
     */
    public void shutdownNow() {
        fileOperationExecutor.shutdownNow();
        uiUpdateExecutor.shutdownNow();
        backgroundTaskExecutor.shutdownNow();
        scheduledExecutor.shutdownNow();
    }

    /**
     * 自定义线程工厂，为线程命名以便调试
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            thread.setDaemon(true); // 设置为守护线程
            return thread;
        }
    }
}