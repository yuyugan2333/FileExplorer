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

public class FileUtils {

    // 日期格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 日期格式化器（仅日期）
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 常见扩展名映射，Windows通常显示友好名称
    private static final Map<String, String> COMMON_EXTENSIONS = Map.ofEntries(
            // 可执行文件
            Map.entry("exe", "应用程序"),
            Map.entry("msi", "Windows Installer 包"),
            Map.entry("bat", "Windows 批处理文件"),
            Map.entry("cmd", "Windows 命令脚本"),
            Map.entry("ps1", "Windows PowerShell 脚本"),

            // 文档
            Map.entry("txt", "文本文档"),
            Map.entry("pdf", "Adobe Acrobat 文档"),
            Map.entry("doc", "Microsoft Word 文档"),
            Map.entry("docx", "Microsoft Word 文档"),
            Map.entry("xls", "Microsoft Excel 工作表"),
            Map.entry("xlsx", "Microsoft Excel 工作表"),
            Map.entry("ppt", "Microsoft PowerPoint 演示文稿"),
            Map.entry("pptx", "Microsoft PowerPoint 演示文稿"),
            Map.entry("rtf", "富文本格式文档"),

            // 图片
            Map.entry("jpg", "JPEG 图像"),
            Map.entry("jpeg", "JPEG 图像"),
            Map.entry("png", "PNG 图像"),
            Map.entry("gif", "GIF 图像"),
            Map.entry("bmp", "位图图像"),
            Map.entry("ico", "图标文件"),
            Map.entry("webp", "WebP 图像"),
            Map.entry("svg", "可缩放矢量图形"),

            // 音频
            Map.entry("mp3", "MP3 音频"),
            Map.entry("wav", "WAV 音频"),
            Map.entry("flac", "FLAC 音频"),
            Map.entry("m4a", "MPEG-4 音频"),

            // 视频
            Map.entry("mp4", "MPEG-4 视频"),
            Map.entry("avi", "AVI 视频"),
            Map.entry("mov", "QuickTime 影片"),
            Map.entry("wmv", "Windows Media 视频"),
            Map.entry("mkv", "Matroska 视频"),

            // 压缩文件
            Map.entry("zip", "压缩(zipped)文件夹"),
            Map.entry("rar", "WinRAR 压缩文件"),
            Map.entry("7z", "7-Zip 压缩文件"),
            Map.entry("tar", "TAR 压缩文件"),
            Map.entry("gz", "Gzip 压缩文件"),

            // 代码文件
            Map.entry("java", "Java 源文件"),
            Map.entry("py", "Python 文件"),
            Map.entry("js", "JavaScript 文件"),
            Map.entry("html", "HTML 文档"),
            Map.entry("htm", "HTML 文档"),
            Map.entry("css", "层叠样式表"),
            Map.entry("xml", "XML 文档"),
            Map.entry("json", "JSON 文件"),

            // 系统文件
            Map.entry("dll", "应用程序扩展"),
            Map.entry("sys", "系统文件"),
            Map.entry("ini", "配置设置"),
            Map.entry("log", "日志文件"),
            Map.entry("lnk", "快捷方式")
    );

    // 不显示友好名称的扩展名，Windows会直接显示扩展名
    private static final Set<String> RAW_EXTENSIONS = Set.of(
            "bin", "dat", "tmp", "cache", "db", "db3", "sqlite", "jar", "class",
            "pak", "pak2", "pak3", "pak4", "data", "index", "idx", "pack", "resources",
            "bundle", "asset", "config", "cfg", "properties", "yml", "yaml", "toml"
    );

    /**
     * 获取文件类型描述，仿照Windows资源管理器规则
     * @param path 文件路径
     * @return 友好的文件类型描述
     */
    public static String getFileTypeDescription(Path path) {
        if (Files.isDirectory(path)) {
            return "文件夹";
        }

        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            String extension = fileName.substring(dotIndex + 1).toLowerCase();

            // 1. 检查是否是常见扩展名（显示友好名称）
            if (COMMON_EXTENSIONS.containsKey(extension)) {
                return COMMON_EXTENSIONS.get(extension);
            }

            // 2. 检查是否需要直接显示扩展名
            if (RAW_EXTENSIONS.contains(extension)) {
                return extension.toUpperCase() + " 文件";
            }

            // 3. 尝试通过MIME类型判断
            try {
                String contentType = Files.probeContentType(path);
                if (contentType != null) {
                    return getTypeFromMimeType(contentType, extension);
                }
            } catch (IOException e) {
                // 忽略异常
            }

            // 4. 默认：扩展名 + 文件
            return extension.toUpperCase() + " 文件";
        }

        // 无扩展名的文件
        return getFileTypeNoExtension(path);
    }

    /**
     * 从MIME类型获取文件类型描述
     */
    private static String getTypeFromMimeType(String mimeType, String extension) {
        if (mimeType == null) {
            return extension.toUpperCase() + " 文件";
        }

        String[] parts = mimeType.split("/");
        if (parts.length >= 2) {
            String mainType = parts[0];
            String subType = parts[1];

            // 根据主类型分类
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
                    } else if (subType.contains("zip") ||
                            subType.contains("x-7z") ||
                            subType.contains("x-rar")) {
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

        // 无法识别则返回扩展名
        return extension.toUpperCase() + " 文件";
    }

    /**
     * 处理无扩展名的文件
     */
    private static String getFileTypeNoExtension(Path path) {
        try {
            // 尝试识别可执行文件
            if (Files.isExecutable(path)) {
                return "应用程序";
            }

            // 尝试通过MIME类型
            String contentType = Files.probeContentType(path);
            if (contentType != null) {
                if (contentType.startsWith("text/")) {
                    return "文本文档";
                } else if (contentType.startsWith("image/")) {
                    return "图像文件";
                }
            }

            // 尝试读取文件头判断文件类型
            String fileType = detectFileBySignature(path);
            if (fileType != null) {
                return fileType;
            }
        } catch (IOException e) {
            // 忽略异常
        }

        return "文件";
    }

    /**
     * 通过文件签名检测文件类型
     */
    private static String detectFileBySignature(Path path) {
        try {
            byte[] header = Files.readAllBytes(path);
            if (header.length >= 4) {
                // 检测常见文件类型
                if (header[0] == 0x4D && header[1] == 0x5A) { // MZ
                    return "可执行文件";
                } else if (header[0] == 0x7F && header[1] == 0x45 &&
                        header[2] == 0x4C && header[3] == 0x46) { // ELF
                    return "ELF 可执行文件";
                } else if (header[0] == 0xCA && header[1] == 0xFE &&
                        header[2] == 0xBA && header[3] == 0xBE) { // Java class
                    return "Java 类文件";
                } else if (header[0] == 0x25 && header[1] == 0x50 &&
                        header[2] == 0x44 && header[3] == 0x46) { // %PDF
                    return "PDF 文档";
                } else if (header[0] == 0x50 && header[1] == 0x4B &&
                        header[2] == 0x03 && header[3] == 0x04) { // ZIP/PK
                    return "压缩文件";
                }
            }
        } catch (IOException e) {
            // 忽略异常
        }
        return null;
    }

    /**
     * 格式化日期时间为可读字符串
     * @param dateTime 日期时间
     * @return 格式化后的字符串
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * 格式化日期为可读字符串（仅日期部分）
     * @param dateTime 日期时间
     * @return 格式化后的日期字符串
     */
    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }

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