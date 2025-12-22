package com.fileexplorer;

// package com.fileexplorer; // 如果使用包，添加此行；否则移除

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class FileUtils {

    /**
     * 计算文件夹的总大小（字节），递归遍历所有文件。
     * @param dir 文件夹路径
     * @return 总大小（字节），如果失败返回 -1
     */
    public static long calculateDirectorySize(Path dir) {
        if (!Files.isDirectory(dir)) {
            return -1;
        }
        try {
            AtomicLong size = new AtomicLong(0);
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    size.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }
            });
            return size.get();
        } catch (IOException e) {
            System.err.println("计算文件夹大小失败: " + dir + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * 统计文件夹中的文件数（不包括子文件夹中的）。
     * @param dir 文件夹路径
     * @return 文件数，如果失败返回 -1
     */
    public static long countFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return -1;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            System.err.println("统计文件数失败: " + dir + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * 统计文件夹中的子文件夹数（不包括自身，不递归）。
     * @param dir 文件夹路径
     * @return 子文件夹数，如果失败返回 -1
     */
    public static long countSubDirectories(Path dir) {
        if (!Files.isDirectory(dir)) {
            return -1;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).count();
        } catch (IOException e) {
            System.err.println("统计子文件夹数失败: " + dir + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * 格式化大小为人类可读格式（B, KB, MB, GB）。
     * @param size 大小（字节）
     * @return 格式化字符串
     */
    public static String formatSize(long size) {
        if (size < 0) return "--";
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024L * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    /**
     * 将glob通配符转换为正则表达式（用于文件名搜索）。
     * @param glob 通配符字符串，如 "*.txt"
     * @return 正则表达式字符串
     */
    public static String globToRegex(String glob) {
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
                case '\\':
                    regex.append("\\\\");
                    break;
                default:
                    regex.append(c);
            }
        }
        return regex.toString();
    }

    /**
     * 获取路径所在磁盘的可用空间。
     * @param path 任意路径
     * @return 可用空间（字节），如果失败返回 -1
     */
    public static long getUsableSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return store.getUsableSpace();
        } catch (IOException e) {
            System.err.println("获取可用空间失败: " + path + " - " + e.getMessage());
            return -1;
        }
    }

    /**
     * 获取路径所在磁盘的总空间。
     * @param path 任意路径
     * @return 总空间（字节），如果失败返回 -1
     */
    public static long getTotalSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return store.getTotalSpace();
        } catch (IOException e) {
            System.err.println("获取总空间失败: " + path + " - " + e.getMessage());
            return -1;
        }
    }
}