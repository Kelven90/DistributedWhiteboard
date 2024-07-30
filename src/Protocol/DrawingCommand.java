/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Protocol;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DrawingCommand implements Serializable {
    public static final String LINE = "LINE";
    public static final String CIRCLE = "CIRCLE";
    public static final String RECTANGLE = "RECTANGLE";
    public static final String TEXT = "TEXT";
    public static final String BRUSH = "BRUSH";
    public static final String ERASER = "ERASER";
    public static final String POLYGON = "POLYGON";

    private String user;
    private String typeDraw;
    private int x1, y1, x2, y2;
    private double strokeWidth;
    private String color;
    private String text;
    private List<Integer> points;

    public DrawingCommand(String user, String typeDraw, int x1, int y1, int x2, int y2, String color, double strokeWidth) {
        this.user = user;
        this.typeDraw = typeDraw;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    public DrawingCommand(String user, String typeDraw, int x1, int y1, String text, String color) {
        this.user = user;
        this.typeDraw = typeDraw;
        this.x1 = x1;
        this.y1 = y1;
        this.text = text;
        this.color = color;
    }

    public DrawingCommand(String user, String typeDraw, List<Integer> points, String color, double strokeWidth) {
        this.user = user;
        this.typeDraw = typeDraw;
        this.points = points;
        this.color = color;
        this.strokeWidth = strokeWidth;
    }

    public static DrawingCommand fromJSON(String jsonData) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
        String user = (String) jsonObject.get("user");
        String typeDraw = (String) jsonObject.get("typeDraw");
        int x1 = ((Long) jsonObject.get("x1")).intValue();
        int y1 = ((Long) jsonObject.get("y1")).intValue();
        int x2 = jsonObject.containsKey("x2") ? ((Long) jsonObject.get("x2")).intValue() : 0;
        int y2 = jsonObject.containsKey("y2") ? ((Long) jsonObject.get("y2")).intValue() : 0;
        String color = (String) jsonObject.get("color");
        double strokeWidth = jsonObject.containsKey("strokeWidth") ? ((Number) jsonObject.get("strokeWidth")).doubleValue() : 0;
        String text = (String) jsonObject.get("text");
        // Check if the drawing command includes text, implying it's a TEXT type command
        if (typeDraw.equals("TEXT") && text != null) {
            return new DrawingCommand(user, typeDraw, x1, y1, text, color);
        } else if (typeDraw.equals(DrawingCommand.POLYGON)) {
            List<Long> longPoints = (List<Long>) jsonObject.get("points");
            List<Integer> intPoints = new ArrayList<>();
            for (Long point : longPoints) {
                intPoints.add(point.intValue());
            }
            return new DrawingCommand(user, typeDraw, intPoints, color, strokeWidth);
        } else {
            return new DrawingCommand(user, typeDraw, x1, y1, x2, y2, color, strokeWidth);
        }
    }

    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user", user);
        jsonObject.put("typeDraw", typeDraw);
        jsonObject.put("x1", x1);
        jsonObject.put("y1", y1);
        jsonObject.put("x2", x2);
        jsonObject.put("y2", y2);
        jsonObject.put("strokeWidth", strokeWidth);
        jsonObject.put("color", color);
        jsonObject.put("text", text);
        if (typeDraw.equals(POLYGON) && points != null) {
            jsonObject.put("points", points);
        }
        return jsonObject.toJSONString();
    }

    // Factory methods
    public static DrawingCommand createLine(String user, int x1, int y1, int x2, int y2, String color, double strokeWidth) {
        return new DrawingCommand(user, LINE, x1, y1, x2, y2, color, strokeWidth);
    }

    public static DrawingCommand createCircle(String user, int x1, int y1, int x2, int y2, String color, double strokeWidth) {
        return new DrawingCommand(user, CIRCLE, x1, y1, x2, y2, color, strokeWidth);
    }

    public static DrawingCommand createRectangle(String user, int x1, int y1, int x2, int y2, String color, double strokeWidth) {
        return new DrawingCommand(user, RECTANGLE, x1, y1, x2, y2, color, strokeWidth);
    }

    public static DrawingCommand createText(String user, int x, int y, String text, String color) {
        return new DrawingCommand(user, TEXT, x, y, text, color);
    }

    public static DrawingCommand createBrush(String user, int x1, int y1, int x2, int y2, String color, double strokeWidth) {
        return new DrawingCommand(user, BRUSH, x1, y1, x2, y2, color, strokeWidth);
    }

    public static DrawingCommand createEraser(String user, int x1, int y1, int x2, int y2, double strokeWidth) {
        return new DrawingCommand(user, ERASER, x1, y1, x2, y2, "#FF0000", strokeWidth);
    }

    public static DrawingCommand createPolygon(String user, double centerX, double centerY, double radius, String color, double strokeWidth, int sides) {
        List<Integer> points = new ArrayList<>();
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides - Math.PI / 2; // Adjust rotation if needed (-Math.PI/2 rotates the polygon to start from the top vertex)
            int x = (int) (centerX + radius * Math.cos(angle));
            int y = (int) (centerY + radius * Math.sin(angle));
            points.add(x);
            points.add(y);
        }
        return new DrawingCommand(user, DrawingCommand.POLYGON, points, color, strokeWidth);
    }


    // Getters for all properties
    public String getUser() {
        return user;
    }

    public String getTypeDraw() {
        return typeDraw;
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public String getColor() {
        return color;
    }

    public String getText() {
        return text;
    }

    public List<Integer> getPoints() {
        return points;
    }
}
