package com.fileexplorer;

import javafx.scene.input.KeyEvent;

/**
 * 键盘事件处理类，管理快捷键。
 */
class KeyboardHandler {
    private final Controller controller;

    public KeyboardHandler(Controller controller) {
        this.controller = controller;
        controller.getRoot().addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
    }

    private void handleKeyPressed(KeyEvent event) {
        if (event.isControlDown()) {
            switch (event.getCode()) {
                case C:
                    controller.fileOperationHandler.copySelected();
                    event.consume();
                    break;
                case X:
                    controller.fileOperationHandler.cutSelected();
                    event.consume();
                    break;
                case V:
                    controller.fileOperationHandler.paste();
                    event.consume();
                    break;
                case A:
                    controller.fileOperationHandler.selectAll();
                    event.consume();
                    break;
                default:
                    break;
            }
        } else {
            switch (event.getCode()) {
                case DELETE:
                    controller.fileOperationHandler.deleteSelected();
                    event.consume();
                    break;
                case F2:
                    controller.fileOperationHandler.renameSelected();
                    event.consume();
                    break;
                case F5:
                    controller.refresh();
                    event.consume();
                    break;
                default:
                    break;
            }
        }
    }
}