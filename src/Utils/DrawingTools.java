/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Utils;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;

public class DrawingTools {
    private Cursor brushCursor;
    private Cursor eraserCursor;

    public DrawingTools() {
        // Initialize cursors on the JavaFX Application Thread
        Platform.runLater(() -> {
            try {
                Image brushImage = new Image(getClass().getResourceAsStream("/resources/paint_brush.png"));
                if (brushImage != null) {
                    brushCursor = new ImageCursor(brushImage, brushImage.getWidth() / 2, brushImage.getHeight() / 2);
                } else {
                    brushCursor = Cursor.DEFAULT;
                    System.err.println("Brush image is not found.");
                }

                Image eraserImage = new Image(getClass().getResourceAsStream("/resources/eraser.png"));
                if (eraserImage != null) {
                    eraserCursor = new ImageCursor(eraserImage, eraserImage.getWidth() / 2, eraserImage.getHeight() / 2);
                } else {
                    eraserCursor = Cursor.DEFAULT;
                    System.err.println("Eraser image is not found.");
                }
            } catch (Exception e) {
                System.err.println("Error loading cursor images: " + e.getMessage());
                brushCursor = Cursor.DEFAULT;
                eraserCursor = Cursor.DEFAULT;
            }
        });
    }

    public Cursor getBrushCursor() {
        return brushCursor;
    }

    public Cursor getEraserCursor() {
        return eraserCursor;
    }
}
