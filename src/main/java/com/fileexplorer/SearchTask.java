package com.fileexplorer;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

public class SearchTask extends Task<List<FileItem>> {
    private final Path startDir;
    private final String pattern;
    private final boolean useWildcard;
    private volatile boolean cancelled = false;
    private int resultCount = 0;
    private static final int MAX_RESULTS = 1000;

    // 通配符模式
    private Pattern compiledPattern;

    public SearchTask(Path startDir, String pattern) {
        this(startDir, pattern, true);
    }

    public SearchTask(Path startDir, String pattern, boolean useWildcard) {
        this.startDir = startDir;
        this.pattern = pattern;
        this.useWildcard = useWildcard;

        // 如果使用通配符，编译正则表达式模式
        if (useWildcard && pattern != null && !pattern.trim().isEmpty()) {
            this.compiledPattern = compileWildcardPattern(pattern.trim());
        }
    }

    @Override
    protected List<FileItem> call() throws Exception {
        List<FileItem> results = new ArrayList<>();

        // 如果搜索模式为空，返回空结果
        if (pattern == null || pattern.trim().isEmpty()) {
            return results;
        }

        updateMessage("正在搜索: " + pattern);

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
                                updateMessage("已达到最大结果限制 (" + MAX_RESULTS + " 个结果)");
                                return FileVisitResult.TERMINATE;
                            }

                            // 检查是否是普通文件
                            if (attrs.isRegularFile()) {
                                String fileName = path.getFileName().toString();
                                boolean matches = false;

                                if (useWildcard && compiledPattern != null) {
                                    // 使用通配符模式匹配
                                    matches = compiledPattern.matcher(fileName).matches();
                                } else {
                                    // 使用简单的文件名包含匹配（不区分大小写）
                                    String searchPattern = pattern.toLowerCase();
                                    matches = fileName.toLowerCase().contains(searchPattern);
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
                            // 在实际应用中，你可能想记录这个异常
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
                                // 对于其他异常，可以选择记录日志
                                System.err.println("访问目录后异常: " + dir + ", 原因: " + exc.getMessage());
                            }

                            return FileVisitResult.CONTINUE;
                        }
                    });

            updateMessage("搜索完成，找到 " + results.size() + " 个结果");
        } catch (Exception e) {
            // 如果任务没有被取消，抛出异常
            if (!isCancelled() && !cancelled) {
                // 检查是否是访问被拒绝的异常
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof AccessDeniedException) {
                        // 忽略访问被拒绝的异常，只是记录
                        System.err.println("访问被拒绝: " + cause.getMessage());
                        return results;
                    }
                    cause = cause.getCause();
                }
                // 对于其他异常，重新抛出
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

    // 可选：添加一个简单的方法来检查文件是否可读
    private boolean isFileReadable(Path path) {
        try {
            return Files.isReadable(path) && Files.isRegularFile(path);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * 将通配符模式编译为正则表达式模式
     * 支持的通配符：
     *   * - 匹配0个或多个任意字符
     *   ? - 匹配单个任意字符
     *   [abc] - 匹配a、b或c中的任意一个字符
     *   [a-z] - 匹配a到z之间的任意一个字符
     *   [!abc] 或 [^abc] - 匹配除了a、b、c之外的任意字符
     *
     * @param wildcard 通配符表达式
     * @return 编译后的正则表达式模式
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

        // 将通配符转换为正则表达式
        // 注意：需要先转换?，再转换*
        regex = regex.replace("?", ".");
        regex = regex.replace("*", ".*");

        // 处理字符类，如[abc]或[a-z]
        // 由于我们已经转义了[和]，现在需要恢复它们
        regex = restoreCharacterClasses(regex);

        // 添加行首和行尾锚点，确保完全匹配
        regex = "^" + regex + "$";

        // 编译为不区分大小写的模式
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
     * @param text 要匹配的文本
     * @param pattern 通配符模式
     * @return 是否匹配
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