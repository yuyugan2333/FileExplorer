package com.fileexplorer;

import javafx.concurrent.Task;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SearchTask extends Task<List<FileItem>> {
    private final Path startDir;
    private final String pattern;
    private volatile boolean cancelled = false;

    public SearchTask(Path startDir, String pattern) {
        this.startDir = startDir;
        this.pattern = pattern;
    }

    @Override
    protected List<FileItem> call() throws Exception {
        List<FileItem> results = new ArrayList<>();

        // 如果搜索模式为空，返回空结果
        if (pattern == null || pattern.trim().isEmpty()) {
            return results;
        }

        try {
            // 转换glob通配符为正则表达式
            String regex = globToRegex(pattern);
            updateMessage("正在搜索: " + pattern);

            // 使用walk遍历目录树
            try (Stream<Path> stream = Files.walk(startDir)) {
                stream.filter(path -> {
                            // 检查任务是否被取消
                            if (isCancelled() || cancelled) {
                                return false;
                            }

                            // 过滤出文件（不包括目录）
                            return Files.isRegularFile(path);
                        })
                        .filter(path -> {
                            // 使用正则表达式匹配文件名
                            String fileName = path.getFileName().toString();
                            return fileName.toLowerCase().contains(pattern.toLowerCase());
                        })
                        .limit(1000) // 限制搜索结果数量，避免内存溢出
                        .forEach(path -> {
                            if (!isCancelled() && !cancelled) {
                                results.add(new FileItem(path));
                                // 更新进度消息
                                updateMessage("找到: " + path.getFileName());
                            }
                        });
            }

            updateMessage("搜索完成，找到 " + results.size() + " 个结果");
        } catch (Exception e) {
            if (!isCancelled() && !cancelled) {
                throw e;
            }
        }

        return results;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        return super.cancel(mayInterruptIfRunning);
    }

    private String globToRegex(String glob) {
        // 简单的通配符转换，支持 * 和 ?
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                default:
                    regex.append(c);
            }
        }
        return regex.toString();
    }
}