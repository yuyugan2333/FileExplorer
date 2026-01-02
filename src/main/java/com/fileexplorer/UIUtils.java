package com.fileexplorer;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * UI工具类，提供常见的对话框和警报方法。
 */
public class UIUtils {

    /**
     * 显示警报对话框。
     */
    public static void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示错误警报（默认类型）。
     */
    public static void showAlert(String title, String message) {
        showAlert(title, message, Alert.AlertType.ERROR);
    }

    /**
     * 显示确认对话框。
     */
    public static boolean showConfirmDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * 显示信息对话框。
     */
    public static void showInfo(String message) {
        showAlert("信息", message, Alert.AlertType.INFORMATION);
    }

    /**
     * 显示文本输入对话框。
     */
    public static Optional<String> showTextInputDialog(String title, String header, String content, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        return dialog.showAndWait();
    }

    /**
     * 显示文件冲突对话框。
     */
    public static boolean showConflictDialog(List<Path> conflicts) {
        StringBuilder message = new StringBuilder();
        message.append("以下文件已存在，是否覆盖？\n\n");

        for (int i = 0; i < Math.min(5, conflicts.size()); i++) {
            message.append("• ").append(conflicts.get(i).getFileName()).append("\n");
        }

        if (conflicts.size() > 5) {
            message.append("... 还有 ").append(conflicts.size() - 5).append(" 个文件\n");
        }

        message.append("\n选择是覆盖所有，否跳过所有，取消中断操作");

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("文件冲突");
        alert.setHeaderText("目标位置已存在同名文件");
        alert.setContentText(message.toString());

        ButtonType replaceAll = new ButtonType("覆盖所有");
        ButtonType skipAll = new ButtonType("跳过所有");
        ButtonType cancel = ButtonType.CANCEL;

        alert.getButtonTypes().setAll(replaceAll, skipAll, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == replaceAll) {
                return true;
            } else if (result.get() == skipAll) {
                showInfo("已跳过所有冲突文件");
                return false;
            }
        }
        return false;
    }
}