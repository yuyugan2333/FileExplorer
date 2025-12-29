package com.fileexplorer;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 图标管理器，负责加载和管理文件图标
 */
public class IconManager {
    private static IconManager instance;

    // 图标缓存
    private final Map<String, Image> iconCache = new HashMap<>();

    // 默认图标
    private Image defaultFolderIcon;
    private Image defaultFileIcon;

    // 常见文件类型图标
    private Image imageIcon;
    private Image documentIcon;
    private Image musicIcon;
    private Image videoIcon;
    private Image archiveIcon;
    private Image executableIcon;

    private IconManager() {
        loadDefaultIcons();
    }

    public static synchronized IconManager getInstance() {
        if (instance == null) {
            instance = new IconManager();
        }
        return instance;
    }

    /**
     * 加载默认图标
     */
    private void loadDefaultIcons() {
        try {
            // 尝试从资源加载图标
            defaultFolderIcon = loadResourceIcon("folder.png");
            defaultFileIcon = loadResourceIcon("file.png");
            imageIcon = loadResourceIcon("image.png");
            documentIcon = loadResourceIcon("document.png");
            musicIcon = loadResourceIcon("music.png");
            videoIcon = loadResourceIcon("video.png");
            archiveIcon = loadResourceIcon("archive.png");
            executableIcon = loadResourceIcon("executable.png");
        } catch (Exception e) {
            // 如果资源图标不存在，使用系统图标或内置图标
            createFallbackIcons();
        }
    }

    /**
     * 从资源加载图标
     */
    private Image loadResourceIcon(String iconName) {
        try {
            String resourcePath = "/com/fileexplorer/icons/" + iconName;
            java.net.URL url = getClass().getResource(resourcePath);
            if (url != null) {
                return new Image(url.toExternalForm());
            }
        } catch (Exception e) {
            // 资源不存在，使用回退方案
        }
        return null;
    }

    /**
     * 创建回退图标（使用简单的图形）
     */
    private void createFallbackIcons() {
        defaultFolderIcon = createColoredIcon("#4CAF50", "📁");
        defaultFileIcon = createColoredIcon("#757575", "📄");
        imageIcon = createColoredIcon("#2196F3", "🖼️");
        documentIcon = createColoredIcon("#FF9800", "📄");
        musicIcon = createColoredIcon("#9C27B0", "🎵");
        videoIcon = createColoredIcon("#F44336", "🎬");
        archiveIcon = createColoredIcon("#795548", "📦");
        executableIcon = createColoredIcon("#4CAF50", "⚙️");
    }

    /**
     * 创建简单的文本图标
     */
    private Image createColoredIcon(String color, String text) {
        // 这是一个简化的实现，实际项目中可以使用Canvas或SVG创建图标
        // 这里返回null，实际实现时创建图标
        return null;
    }

    /**
     * 获取文件图标
     */
    public Image getIconForFile(Path path) {
        if (path == null) {
            return defaultFileIcon;
        }

        // 生成缓存键
        String cacheKey = generateCacheKey(path);

        // 检查缓存
        if (iconCache.containsKey(cacheKey)) {
            return iconCache.get(cacheKey);
        }

        Image icon = null;

        // 如果是目录
        if (Files.isDirectory(path)) {
            icon = getFolderIcon();
        } else {
            // 根据文件扩展名获取图标
            String fileName = path.getFileName().toString().toLowerCase();
            icon = getIconByExtension(fileName);
        }

        // 缓存图标
        if (icon != null) {
            iconCache.put(cacheKey, icon);
        }

        return icon != null ? icon : defaultFileIcon;
    }

    /**
     * 获取文件夹图标
     */
    public Image getFolderIcon() {
        if (defaultFolderIcon != null) {
            return defaultFolderIcon;
        }
        // 如果还没有图标，创建一个简单的文件夹图标
        return createSimpleIcon("#4CAF50");
    }

    /**
     * 根据文件扩展名获取图标
     */
    private Image getIconByExtension(String fileName) {
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
                fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
                fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {
            return imageIcon != null ? imageIcon : createSimpleIcon("#2196F3");
        } else if (fileName.endsWith(".txt") || fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") || fileName.endsWith(".pdf") ||
                fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            return documentIcon != null ? documentIcon : createSimpleIcon("#FF9800");
        } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") ||
                fileName.endsWith(".flac") || fileName.endsWith(".aac")) {
            return musicIcon != null ? musicIcon : createSimpleIcon("#9C27B0");
        } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") ||
                fileName.endsWith(".mkv") || fileName.endsWith(".mov")) {
            return videoIcon != null ? videoIcon : createSimpleIcon("#F44336");
        } else if (fileName.endsWith(".zip") || fileName.endsWith(".rar") ||
                fileName.endsWith(".7z") || fileName.endsWith(".tar")) {
            return archiveIcon != null ? archiveIcon : createSimpleIcon("#795548");
        } else if (fileName.endsWith(".exe")) {
            return executableIcon != null ? executableIcon : createSimpleIcon("#4CAF50");
        }

        return defaultFileIcon != null ? defaultFileIcon : createSimpleIcon("#757575");
    }

    /**
     * 创建简单颜色图标
     */
    private Image createSimpleIcon(String color) {
        // 在实际项目中，这里应该创建真正的图标
        // 为了简化，这里返回null
        return null;
    }

    /**
     * 生成缓存键
     */
    private String generateCacheKey(Path path) {
        return path.toString().toLowerCase();
    }

    /**
     * 创建ImageView
     */
    public ImageView createIconView(Path path, int size) {
        Image icon = getIconForFile(path);
        if (icon != null) {
            ImageView imageView = new ImageView(icon);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return null;
    }

    /**
     * 创建文件夹图标视图
     */
    public ImageView createFolderIconView(int size) {
        if (defaultFolderIcon != null) {
            ImageView imageView = new ImageView(defaultFolderIcon);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        }
        return null;
    }

    /**
     * 清空图标缓存
     */
    public void clearCache() {
        iconCache.clear();
    }
}