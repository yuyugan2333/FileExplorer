package com.fileexplorer;

import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

public class SearchTask extends Task<List<FileItem>> {
    private final List<Path> searchRoots;
    private final String pattern;
    private final String mode;
    private volatile boolean cancelled = false;
    private int resultCount = 0;
    private static final int MAX_RESULTS = 1000;

    // 通配符模式
    private Pattern compiledPattern;

    public SearchTask(List<Path> searchRoots, String pattern, String mode) {
        this.searchRoots = searchRoots;
        this.pattern = pattern;
        this.mode = mode;

        // 如果使用通配符，编译正则表达式模式
        if (mode.equals("通配符匹配") && pattern != null && !pattern.trim().isEmpty()) {
            // 认为用户输入的两端都有*，匹配部分字符串
            String wildcard = "*" + pattern.trim() + "*";
            this.compiledPattern = compileWildcardPattern(wildcard);
        } else if (mode.equals("文本文件内容通配符匹配")) {
            // 对于内容搜索，也编译通配符
            String wildcard = "*" + pattern.trim() + "*";
            this.compiledPattern = compileWildcardPattern(wildcard);
        }
    }

    @Override
    protected List<FileItem> call() throws Exception {
        List<FileItem> results = new ArrayList<>();

        // 如果搜索模式为空，返回空结果
        if (pattern == null || pattern.trim().isEmpty()) {
            return results;
        }

        updateMessage("正在搜索: " + pattern + " (模式: " + mode + ")");

        for (Path startDir : searchRoots) {
            try {
                // 使用walkFileTree进行遍历，可以更好地控制异常处理
                Files.walkFileTree(startDir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE,
                        new SimpleFileVisitor<Path>() {

                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                // 检查任务是否被取消
                                if (isCancelled() || cancelled) {
                                    return FileVisitResult.TERMINATE;
                                }
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                                // 检查任务是否被取消
                                if (isCancelled() || cancelled) {
                                    return FileVisitResult.TERMINATE;
                                }

                                // 检查结果数量限制
                                if (resultCount >= MAX_RESULTS) {
                                    updateMessage("已达到最大结果限制 (" + MAX_RESULTS + " 个结果)，停止搜索");
                                    return FileVisitResult.TERMINATE;
                                }

                                // 检查是否是普通文件
                                if (attrs.isRegularFile()) {
                                    String fileName = path.getFileName().toString();
                                    boolean matches = false;

                                    switch (mode) {
                                        case "通配符匹配":
                                            // 使用通配符模式匹配文件名
                                            matches = compiledPattern.matcher(fileName).matches();
                                            break;
                                        case "字符串匹配":
                                            // 简单包含匹配（不区分大小写）
                                            String searchPattern = pattern.toLowerCase();
                                            matches = fileName.toLowerCase().contains(searchPattern);
                                            break;
                                        case "文本文件内容通配符匹配":
                                            // 只搜索文本文件内容
                                            if (isTextFile(path)) {
                                                matches = searchFileContent(path, compiledPattern);
                                            }
                                            break;
                                        case "搜索图片":
                                            matches = isImageFile(path);
                                            break;
                                        case "搜索音频":
                                            matches = isAudioFile(path);
                                            break;
                                        case "搜索视频":
                                            matches = isVideoFile(path);
                                            break;
                                        case "搜索文档":
                                            matches = isDocumentFile(path);
                                            break;
                                        case "搜索压缩文件":
                                            matches = isArchiveFile(path);
                                            break;
                                        case "检索大文件(100MB+,可能需要等待)":
                                            // 定义大文件为 > 100MB
                                            matches = attrs.size() > 100 * 1024 * 1024;
                                            break;
                                        default:
                                            break;
                                    }

                                    if (matches) {
                                        results.add(new FileItem(path));
                                        resultCount++;
                                        updateMessage("找到: " + path.getFileName() + " (已找到 " + resultCount + " 个结果)");
                                    }
                                }

                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFileFailed(Path path, IOException exc) throws IOException {
                                // 忽略访问被拒绝的异常，继续搜索其他文件
                                if (exc instanceof AccessDeniedException) {
                                    return FileVisitResult.CONTINUE;
                                }

                                // 对于其他异常，可以选择记录日志或继续
                                System.err.println("访问文件失败: " + path + ", 原因: " + exc.getMessage());

                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                                // 处理目录访问后的异常
                                if (exc != null && exc instanceof AccessDeniedException) {
                                    return FileVisitResult.CONTINUE;
                                }

                                if (exc != null) {
                                    System.err.println("访问目录后异常: " + dir + ", 原因: " + exc.getMessage());
                                }

                                return FileVisitResult.CONTINUE;
                            }
                        });

            } catch (Exception e) {
                // 如果任务没有被取消，抛出异常
                if (!isCancelled() && !cancelled) {
                    // 检查是否是访问被拒绝的异常
                    Throwable cause = e;
                    while (cause != null) {
                        if (cause instanceof AccessDeniedException) {
                            // 忽略访问被拒绝的异常，只是记录
                            System.err.println("访问被拒绝: " + cause.getMessage());
                            continue; // 继续下一个盘符
                        }
                        cause = cause.getCause();
                    }
                    // 对于其他异常，重新抛出
                    throw e;
                }
            }
        }

        updateMessage("搜索完成，找到 " + results.size() + " 个结果");
        return results;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        return super.cancel(mayInterruptIfRunning);
    }

    // 判断是否是文本文件
    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".log") || fileName.endsWith(".ini") ||
                fileName.endsWith(".java") || fileName.endsWith(".py") || fileName.endsWith(".js") ||
                fileName.endsWith(".html") || fileName.endsWith(".css") || fileName.endsWith(".xml") ||
                fileName.endsWith(".json");
    }

    // 搜索文件内容
    private boolean searchFileContent(Path path, Pattern contentPattern) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (contentPattern.matcher(line).find()) { // 使用find()匹配部分
                    return true;
                }
            }
        } catch (IOException e) {
            // 忽略读取错误
        }
        return false;
    }

    // 判断是否是图片文件
    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") ||
                fileName.endsWith(".gif") || fileName.endsWith(".bmp") || fileName.endsWith(".webp") ||
                fileName.endsWith(".svg");
    }

    // 判断是否是音频文件
    private boolean isAudioFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".flac") ||
                fileName.endsWith(".m4a");
    }

    // 判断是否是视频文件
    private boolean isVideoFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mov") ||
                fileName.endsWith(".wmv") || fileName.endsWith(".mkv");
    }

    // 判断是否是文档文件
    private boolean isDocumentFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".pdf") || fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") || fileName.endsWith(".xls") || fileName.endsWith(".xlsx") ||
                fileName.endsWith(".ppt") || fileName.endsWith(".pptx");
    }

    // 判断是否是压缩文件
    private boolean isArchiveFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".zip") || fileName.endsWith(".rar") || fileName.endsWith(".7z") ||
                fileName.endsWith(".tar") || fileName.endsWith(".gz");
    }

    // 可选：添加一个简单的方法来检查文件是否可读
    private boolean isFileReadable(Path path) {
        try {
            return Files.isReadable(path) && Files.isRegularFile(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * 将通配符模式编译为正则表达式
     */
    private Pattern compileWildcardPattern(String wildcard) {
        // 转义正则表达式中的特殊字符
        String regex = wildcard
                .replace("\\", "\\\\")
                .replace(".", "\\.")
                .replace("+", "\\+")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("|", "\\|")
                .replace("^", "\\^")
                .replace("$", "\\$");

        regex = regex.replace("?", ".");
        regex = regex.replace("*", ".*");

        regex = restoreCharacterClasses(regex);

        regex = "^" + regex + "$";

        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * 恢复正则表达式字符类语法
     * 将转义后的字符类恢复为原始形式
     */
    private String restoreCharacterClasses(String regex) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);

            if (c == '\\' && i + 1 < regex.length()) {
                char next = regex.charAt(i + 1);

                // 如果后面是[或]，并且是字符类的一部分，则恢复它们
                if (next == '[' || next == ']') {
                    // 检查是否是字符类的开始
                    if (next == '[') {
                        // 向前查找字符类的结束
                        boolean isCharClass = false;
                        for (int j = i + 2; j < regex.length(); j++) {
                            if (regex.charAt(j) == '\\' && j + 1 < regex.length() && regex.charAt(j + 1) == ']') {
                                isCharClass = true;
                                break;
                            }
                        }

                        if (isCharClass) {
                            result.append('[');
                            i++; // 跳过反斜杠
                            continue;
                        }
                    }
                }
            }

            result.append(c);
        }

        // 将字符类中的转义字符恢复
        String restored = result.toString();
        // 将字符类中的转义方括号恢复
        restored = restored.replace("\\[", "[");
        restored = restored.replace("\\]", "]");

        return restored;
    }

    /**
     * 简单的通配符匹配方法（不编译正则表达式，适合少量匹配）
     */
    public static boolean wildcardMatch(String text, String pattern) {
        return wildcardMatch(text, pattern, 0, 0);
    }

    private static boolean wildcardMatch(String text, String pattern, int textIndex, int patternIndex) {
        int textLen = text.length();
        int patternLen = pattern.length();

        while (patternIndex < patternLen) {
            char p = pattern.charAt(patternIndex);

            if (p == '*') {
                // 跳过连续的*
                while (patternIndex < patternLen && pattern.charAt(patternIndex) == '*') {
                    patternIndex++;
                }

                // 如果*是模式中的最后一个字符
                if (patternIndex == patternLen) {
                    return true;
                }

                // 尝试匹配*后面的部分
                for (int i = textIndex; i < textLen; i++) {
                    if (wildcardMatch(text, pattern, i, patternIndex)) {
                        return true;
                    }
                }
                return false;
            } else if (p == '?') {
                if (textIndex >= textLen) {
                    return false;
                }
                textIndex++;
                patternIndex++;
            } else if (p == '[') {
                // 处理字符类
                patternIndex++; // 跳过[
                boolean negate = false;

                // 检查是否有否定符
                if (patternIndex < patternLen && pattern.charAt(patternIndex) == '!') {
                    negate = true;
                    patternIndex++;
                } else if (patternIndex < patternLen && pattern.charAt(patternIndex) == '^') {
                    negate = true;
                    patternIndex++;
                }

                if (textIndex >= textLen) {
                    return false;
                }

                char textChar = Character.toLowerCase(text.charAt(textIndex));
                boolean matched = false;
                boolean range = false;
                char lastChar = 0;

                while (patternIndex < patternLen && pattern.charAt(patternIndex) != ']') {
                    char c = Character.toLowerCase(pattern.charAt(patternIndex));

                    if (c == '-' && lastChar != 0 && patternIndex + 1 < patternLen && pattern.charAt(patternIndex + 1) != ']') {
                        // 处理范围匹配，如[a-z]
                        range = true;
                        patternIndex++;
                        c = Character.toLowerCase(pattern.charAt(patternIndex));

                        if (lastChar <= textChar && textChar <= c) {
                            matched = true;
                        }
                        range = false;
                        lastChar = 0;
                    } else {
                        if (range) {
                            // 如果是范围匹配的一部分
                            if (lastChar <= textChar && textChar <= c) {
                                matched = true;
                            }
                            range = false;
                        } else {
                            if (c == textChar) {
                                matched = true;
                            }
                            lastChar = c;
                        }
                    }

                    patternIndex++;
                }

                if (patternIndex >= patternLen || pattern.charAt(patternIndex) != ']') {
                    return false; // 字符类没有正确结束
                }

                patternIndex++; // 跳过]

                if (negate) {
                    if (matched) {
                        return false;
                    }
                } else {
                    if (!matched) {
                        return false;
                    }
                }

                textIndex++;
            } else {
                if (textIndex >= textLen || Character.toLowerCase(text.charAt(textIndex)) != Character.toLowerCase(p)) {
                    return false;
                }
                textIndex++;
                patternIndex++;
            }
        }

        return textIndex == textLen;
    }
}