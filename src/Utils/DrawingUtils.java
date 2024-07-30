/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Utils;

import Protocol.DrawingCommand;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;

import java.util.List;

public class DrawingUtils {

    public static void handleDrawingCommand(GraphicsContext gc, DrawingCommand drawingCommand) {
        if (gc == null) {
            System.err.println("GraphicsContext is not initialized");
            return;
        }

        Color originalColor = (Color) gc.getStroke();
        applyStrokeAndLineWidth(gc, drawingCommand);

        switch (drawingCommand.getTypeDraw()) {
            case DrawingCommand.LINE:
                applyStrokeAndLineWidth(gc, drawingCommand);
                gc.strokeLine(drawingCommand.getX1(), drawingCommand.getY1(), drawingCommand.getX2(), drawingCommand.getY2());
                break;
            case DrawingCommand.CIRCLE:
                applyStrokeAndLineWidth(gc, drawingCommand);
                double radius = Math.sqrt(Math.pow(drawingCommand.getX2() - drawingCommand.getX1(), 2) +
                        Math.pow(drawingCommand.getY2() - drawingCommand.getY1(), 2));
                gc.strokeOval(drawingCommand.getX1() - radius, drawingCommand.getY1() - radius, 2 * radius, 2 * radius);
                break;
            case DrawingCommand.RECTANGLE:
                applyStrokeAndLineWidth(gc, drawingCommand);
                gc.strokeRect(Math.min(drawingCommand.getX1(), drawingCommand.getX2()),
                        Math.min(drawingCommand.getY1(), drawingCommand.getY2()),
                        Math.abs(drawingCommand.getX2() - drawingCommand.getX1()),
                        Math.abs(drawingCommand.getY2() - drawingCommand.getY1()));
                break;
            case DrawingCommand.TEXT:
                gc.setFont(new Font("Arial", 20));
                gc.setFill(Color.valueOf(drawingCommand.getColor()));
                gc.fillText(drawingCommand.getText(), drawingCommand.getX1(), drawingCommand.getY1());
                break;
            case DrawingCommand.BRUSH:
                applyStrokeAndLineWidth(gc, drawingCommand);
                gc.setLineCap(StrokeLineCap.ROUND); // Customize the cap style if needed
                gc.strokeLine(drawingCommand.getX1(), drawingCommand.getY1(), drawingCommand.getX2(), drawingCommand.getY2());
                break;
            case DrawingCommand.ERASER:
                double eraserSize = drawingCommand.getStrokeWidth();
                double eraserX = drawingCommand.getX1() - eraserSize / 2;
                double eraserY = drawingCommand.getY1() - eraserSize / 2;
                gc.setFill(Color.WHITE);
                gc.fillRect(eraserX, eraserY, eraserSize, eraserSize);
                break;
            case DrawingCommand.POLYGON:
                applyStrokeAndLineWidth(gc, drawingCommand);
                drawPolygon(gc, drawingCommand.getPoints());
                break;
            default:
                System.err.println("Unsupported drawing command type: " + drawingCommand.getTypeDraw());
        }
        gc.setStroke(originalColor);
    }

    private static void applyStrokeAndLineWidth(GraphicsContext gc, DrawingCommand drawingCommand) {
        gc.setStroke(Color.valueOf(drawingCommand.getColor()));
        gc.setLineWidth(drawingCommand.getStrokeWidth());
    }

    private static void drawPolygon(GraphicsContext gc, List<Integer> points) {
        double[] xPoints = new double[points.size() / 2];
        double[] yPoints = new double[points.size() / 2];

        for (int i = 0; i < points.size(); i += 2) {
            xPoints[i / 2] = points.get(i).doubleValue();  // Convert to double to handle potential Long values
            yPoints[i / 2] = points.get(i + 1).doubleValue();
        }

        if (xPoints.length > 2) { // Ensure there are enough points to form a polygon
            gc.strokePolygon(xPoints, yPoints, xPoints.length);
        }
    }
}

