package com.fileexplorer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 文件工具类，提供文件类型、格式化等方法。
 */
public class FileUtils {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Map<String, String> COMMON_EXTENSIONS = Map.ofEntries(
            // ... (所有扩展名映射，保持原样)
    );

    private static final Set<String> RAW_EXTENSIONS = Set.of(
            // ... (所有原始扩展名，保持原样)
    );

    public static String getFileTypeDescription(Path path) {
        if (Files.isDirectory(path)) {
            return "文件夹";
        }

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();

            if (COMMON_EXTENSIONS.containsKey(extension)) {
                return COMMON_EXTENSIONS.get(extension);
            }

            if (RAW_EXTENSIONS.contains(extension)) {
                return extension.toUpperCase() + " 文件";
            }

            try {
                String contentType = Files.probeContentType(path);
                if (contentType != null) {
                    return getTypeFromMimeType(contentType, extension);
                }
            } catch (IOException e) {
                // 忽略
            }

            return extension.toUpperCase() + " 文件";
        }

        return getFileTypeNoExtension(path);
    }

    private static String getTypeFromMimeType(String mimeType, String extension) {
        if (mimeType == null) {
            return extension.toUpperCase() + " 文件";
        }

        String[] parts = mimeType.split("/");
        if (parts.length >= 2) {
            String mainType = parts[0];
            String subType = parts[1];

            switch (mainType) {
                case "text":
                    if (subType.contains("plain")) {
                        return "文本文档";
                    }
                    return "文本文件";
                case "image":
                    if (subType.contains("svg")) {
                        return "SVG 图像";
                    }
                    return "图像文件";
                case "audio":
                    return "音频文件";
                case "video":
                    return "视频文件";
                case "application":
                    if (subType.contains("pdf")) {
                        return "PDF 文档";
                    } else if (subType.contains("zip") || subType.contains("x-7z") || subType.contains("x-rar")) {
                        return "压缩文件";
                    } else if (subType.contains("octet-stream")) {
                        return extension.toUpperCase() + " 文件";
                    } else if (subType.contains("json")) {
                        return "JSON 文件";
                    } else if (subType.contains("xml")) {
                        return "XML 文档";
                    }
                    break;
            }
        }

        return extension.toUpperCase() + " 文件";
    }

    private static String getFileTypeNoExtension(Path path) {
        try {
            if (Files.isExecutable(path)) {
                return "应用程序";
            }

            String contentType = Files.probeContentType(path);
            if (contentType != null) {
                if (contentType.startsWith("text/")) {
                    return "文本文档";
                } else if (contentType.startsWith("image/")) {
                    return "图像文件";
                }
            }

            String fileType = detectFileBySignature(path);
            if (fileType != null) {
                return fileType;
            }
        } catch (IOException e) {
            // 忽略
        }
        return "文件";
    }

    private static String detectFileBySignature(Path path) {
        try {
            byte[] header = Files.readAllBytes(path);
            if (header.length >= 4) {
                if (header[0] == 0x4D && header[1] == 0x5A) {
                    return "可执行文件";
                } else if (header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) {
                    return "ELF 可执行文件";
                } else if (header[0] == 0xCA && header[1] == 0xFE && header[2] == 0xBA && header[3] == 0xBE) {
                    return "Java 类文件";
                } else if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                    return "PDF 文档";
                } else if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
                    return "压缩文件";
                }
            }
        } catch (IOException e) {
            // 忽略
        }
        return null;
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

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

    public static String formatSize(long size) {
        if (size < 0) return "--";
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else if (size < 1024L * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        else return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

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

    public static long getUsableSpace(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return store.getUsableSpace();
        } catch (IOException e) {
            System.err.println("获取可用空间失败: " + path + " - " + e.getMessage());
            return -1;
        }
    }

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