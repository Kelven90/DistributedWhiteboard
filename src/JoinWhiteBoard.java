/**
 * Name: Kelven Lai    Student ID: 1255199
 */

import Utils.DrawingTools;
import Utils.DrawingUtils;
import Protocol.Message;
import Protocol.MessageConstants;
import Protocol.DrawingCommand;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static Utils.AlertUtils.showInformationAlert;
import static Utils.AlertUtils.updateUserList;

public class JoinWhiteBoard extends Application {
    private static String serverAddress;
    private static int serverPort;
    private static String userName;
    private ObservableList<String> userListData = FXCollections.observableArrayList();
    private static ListView<String> userList;
    private static JoinWhiteBoard instance;
    private static TextArea chatArea;
    private TextField chatInputField;
    private ColorPicker colorPicker;
    private Canvas mainCanvas;
    private Canvas overlayCanvas;
    private GraphicsContext gc;
    private GraphicsContext overlayGc;
    private double startX, startY;
    private int eraserSize = 10;
    private Color currentColor = Color.BLACK;
    private TextField dynamicTextField;
    private StackPane stackPane;
    private DrawingTools drawingTools = new DrawingTools();

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java JoinWhiteBoard <serverIPAddress> <serverPort> <userName>");
            return;
        }

        serverAddress = args[0];
        try {
            InetAddress.getByName(serverAddress);
        } catch (UnknownHostException e) {
            System.out.println("Invalid server address: " + serverAddress);
            return;
        }
        try {
            serverPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid server port number. Please provide a valid integer.");
            return;
        }
        userName = args[2];

        new Thread(() -> connectToServer(userName, serverAddress, serverPort)).start();

        launch(args);
    }

    private static void connectToServer(String userName, String serverAddress, int serverPort) {
        try (Socket socket = new Socket(serverAddress, serverPort);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            Message request = new Message(MessageConstants.REQUEST_JOIN_WHITEBOARD, userName);
            out.println(request.toJSON());
            System.out.println("Sent request to join whiteboard for: " + userName);

            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println("Received response: " + responseLine);
                Message response = Message.fromJSON(responseLine);
                if (response.getType().equals(MessageConstants.REPLY_ERROR)) {
                    Platform.runLater(() -> {
                        showInformationAlert(MessageConstants.REPLY_ERROR, response.getData());
                        Platform.exit();
                        System.exit(0);
                    });
                }
                if (response.getType().equals(MessageConstants.USER_LIST_REQUEST)) {
                    Platform.runLater(() -> updateUserList(response.getData(), userList));
                }
                if (response.getType().equals(MessageConstants.MANAGER_CLOSED)) {
                    Platform.runLater(() -> {
                        showInformationAlert("Whiteboard Closed",
                                "The manager has closed the whiteboard. The session will now end.");
                        Platform.exit();
                        System.exit(0);
                    });
                    break;
                }
                if (response.getType().equals(MessageConstants.APPROVE_JOIN_REQUEST)) {
                    Platform.runLater(() -> showInformationAlert("Approved", MessageConstants.APPROVE_JOIN_REQUEST));
                    break;
                } else if (response.getType().equals(MessageConstants.DECLINE_JOIN_REQUEST)) {
                    Platform.runLater(() -> {
                        showInformationAlert(MessageConstants.DECLINE_JOIN_REQUEST, MessageConstants.SESSION_CLOSED);
                        Platform.exit();
                        System.exit(0);
                    });
                    break;
                }
                if (response.getType().equals(MessageConstants.KICK_USER)) {
                    Platform.runLater(() -> {
                        showInformationAlert(response.getType(), MessageConstants.SESSION_CLOSED);
                        Platform.exit();
                        System.exit(0);
                    });
                    break;
                }
                // Handle drawing commands from server
                if (response.getType().equals(MessageConstants.DRAW_COMMAND)) {
                    try {
                        // Parse the JSON data representing the drawing command
                        DrawingCommand drawingCommand = DrawingCommand.fromJSON(response.getData());
                        // Call the handleDrawingCommand method on the instance
                        Platform.runLater(() -> instance.handleDrawingCommand(drawingCommand));
                    } catch (ParseException ex) {
                        System.err.println("Error parsing drawing command: " + ex.getMessage());
                    }
                }
                if (response.getType().equals(MessageConstants.RECEIVE_CHAT_MESSAGE)) {
                    String chatMessage = response.getData();
                    appendMessageToChat(chatMessage);
                }
                if (response.getType().equals(MessageConstants.CLEAR_CANVAS)) {
                    Platform.runLater(() -> {
                        if (instance != null) {
                            instance.initDrawing();
                        }
                    });
                }
            }
        } catch (IOException | ParseException e) {
            Platform.runLater(() -> {
                showInformationAlert(MessageConstants.REPLY_ERROR,
                        "Error connecting to server: " +
                                "Please check if you entered the correct server Address and port");
                Platform.exit();
                System.exit(0);
            });
        }
    }

    public void handleDrawingCommand(DrawingCommand drawingCommand) {
        DrawingUtils.handleDrawingCommand(this.gc, drawingCommand);
    }

    private void sendLeaveRequest() {
        // Send a message to the server indicating that the user wants to leave
        try (Socket socket = new Socket(serverAddress, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            Message leaveRequest = new Message(MessageConstants.USER_LEAVE, userName);
            out.println(leaveRequest.toJSON());
            Platform.runLater(() -> Platform.exit()); // Exit the application
            System.exit(0);
        } catch (IOException e) {
            Platform.runLater(() -> showInformationAlert(MessageConstants.REPLY_ERROR,
                    MessageConstants.LEAVE_WHITEBOARD_ERROR + e.getMessage()));
        }
    }

    public void start(Stage primaryStage) {
        instance = this;
        primaryStage.setTitle("User's Whiteboard");

        VBox root = new VBox();
        // Create a toolbar with the leave button
        ToolBar toolBar = new ToolBar();

        // Add other toolbar items
        mainCanvas = new Canvas(800, 600);
        overlayCanvas = new Canvas(800, 600);
        overlayCanvas.setMouseTransparent(true);
        stackPane = new StackPane();
        stackPane.getChildren().addAll(mainCanvas, overlayCanvas);

        gc = mainCanvas.getGraphicsContext2D();
        overlayGc = overlayCanvas.getGraphicsContext2D();

        userList = new ListView<>(userListData);

        // Brush tools
        HBox brushTools = new HBox(5);
        Label brushLabel = new Label("Freehand:");
        Button brushButton = new Button("Brush");
        brushTools.getChildren().addAll(brushLabel, brushButton);

        // Shape tools
        HBox shapeTools = new HBox(10);
        Label shapeLabel = new Label("Shapes:");
        Button lineButton = new Button("Line");
        Button circleButton = new Button("Circle");
        Button rectButton = new Button("Rectangle");
        Button pentagonButton = new Button("Pentagon");
        Button hexagonButton = new Button("Hexagon");
        shapeTools.getChildren().addAll(shapeLabel, lineButton, circleButton, rectButton, pentagonButton, hexagonButton);

        // Other tools
        HBox otherTools = new HBox(20);
        Label otherLabel = new Label("Others:");
        Button textButton = new Button("Text");
        Button eraserButton = new Button("Eraser");
        otherTools.getChildren().addAll(otherLabel, textButton, eraserButton);

        colorPicker = new ColorPicker(Color.BLACK);
        colorPicker.setOnAction(e -> {
            Color newColor = colorPicker.getValue();
            setCurrentColor(newColor);
        });

        // Layout management
        HBox toolSelection = new HBox(10); // Root container for tools
        toolSelection.getChildren().addAll(brushTools, new Separator(), shapeTools, new Separator(), otherTools, colorPicker);

        // Slider for eraser size
        HBox sliderContainer = new HBox(5);
        Slider eraserSizeSlider = new Slider(5, 30, 10);
        eraserSizeSlider.setShowTickLabels(true);
        eraserSizeSlider.setShowTickMarks(true);
        eraserSizeSlider.setMajorTickUnit(5);
        eraserSizeSlider.setBlockIncrement(1);
        Label sliderLabel = new Label("Eraser Size:");
        sliderContainer.getChildren().addAll(sliderLabel, eraserSizeSlider);

        eraserSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> setEraserSize(newVal.intValue()));

        HBox leaveContainer = new HBox(5);
        Button leaveButton = new Button("Leave Whiteboard");
        leaveButton.getStyleClass().add("red-border-button");
        leaveButton.setOnAction(e -> sendLeaveRequest());
        leaveContainer.getChildren().addAll(leaveButton);

        // Configure actions for tools
        lineButton.setOnAction(e -> setActiveTool("Line"));
        circleButton.setOnAction(e -> setActiveTool("Circle"));
        rectButton.setOnAction(e -> setActiveTool("Rectangle"));
        pentagonButton.setOnAction(e -> setActiveTool("Pentagon"));
        hexagonButton.setOnAction(e -> setActiveTool("Hexagon"));
        brushButton.setOnAction(e -> setActiveTool("Brush"));
        eraserButton.setOnAction(e -> setActiveTool("Eraser"));
        textButton.setOnAction(e -> setActiveTool("Text"));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatInputField = new TextField();

        // Send button to send messages
        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> {
            String message = chatInputField.getText();
            if (!message.isEmpty()) {
                sendChatMessage(message);
                chatInputField.clear();
            }
        });

        VBox chatBox = new VBox(5);
        chatBox.getChildren().addAll(new Label("Chat:"), chatArea, chatInputField, sendButton);

        // Add to toolbar
        toolBar.getItems().addAll(toolSelection, sliderContainer, new Separator(), leaveContainer);

        VBox userListAndChat = new VBox(10);
        userListAndChat.getChildren().addAll(userList, chatBox);

        HBox canvasAndUserList = new HBox(10);
        canvasAndUserList.getChildren().addAll(stackPane, userListAndChat);

        root.getChildren().addAll(toolBar, canvasAndUserList);

        initDrawing();
        setupDynamicTextField();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void sendChatMessage(String message) {
        if (!message.isEmpty()) {
            String formattedMessage = userName + ": " + message;  // Prepend message with user name
            try (Socket socket = new Socket(serverAddress, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(new Message(MessageConstants.CHAT_MESSAGE, formattedMessage).toJSON());
            } catch (IOException ex) {
                System.err.println("Failed to send chat message: " + ex.getMessage());
                chatArea.appendText("Failed to send message\n");
            }
        }
    }

    private static void appendMessageToChat(String message) {
        Platform.runLater(() -> chatArea.appendText(message + "\n"));
    }

    private void initDrawing() {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, 800, 600);
        gc.setLineWidth(2);
        gc.setStroke(Color.BLACK);
        overlayGc.setLineWidth(2);
        overlayGc.setStroke(Color.BLACK);
    }

    public void setCurrentColor(Color color) {
        this.currentColor = color;
        gc.setStroke(color);
        overlayGc.setStroke(color);
    }

    public void setActiveTool(String tool) {
        mainCanvas.setUserData(tool);
        switch (tool) {
            case "Text":
                setTextMode(text -> {
                    DrawingCommand command = DrawingCommand.createText(userName, (int) startX, (int) startY, text, currentColor.toString());
                    try {
                        Message message = new Message(MessageConstants.DRAW_COMMAND, command.toJSON());
                        Socket socket = new Socket(serverAddress, serverPort);
                        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                        out.println(message.toJSON());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
                mainCanvas.setCursor(Cursor.TEXT);
                break;
            case "Eraser":
                setEraser();
                mainCanvas.setCursor(drawingTools.getEraserCursor());
                break;
            case "Brush":
                setBrush();
                mainCanvas.setCursor(drawingTools.getBrushCursor());
                break;
            default:
                setMouseEventsForShapes();
                mainCanvas.setCursor(Cursor.CROSSHAIR);
                break;
        }
    }

    private void setupDynamicTextField() {
        dynamicTextField = new TextField();
        dynamicTextField.setVisible(false);
        dynamicTextField.setMinWidth(50);
        dynamicTextField.setMaxHeight(50);
        dynamicTextField.setMaxWidth(200);
        stackPane.getChildren().add(dynamicTextField);

        dynamicTextField.textProperty().addListener((obs, oldText, newText) -> {
            double textWidth = dynamicTextField.getFont().getSize() * newText.length() * 0.6;
            dynamicTextField.setPrefWidth(Math.max(textWidth, 100));
        });
    }

    private void setTextMode(Consumer<String> textConsumer) {
        mainCanvas.setOnMouseClicked(e -> {
            startX = e.getX();
            startY = e.getY();
            dynamicTextField.setLayoutX(startX);
            dynamicTextField.setLayoutY(startY);
            dynamicTextField.setVisible(true);
            dynamicTextField.requestFocus();
        });

        dynamicTextField.setOnAction(e -> {
            String text = dynamicTextField.getText();
            if (!text.isEmpty()) {
                gc.setFont(new Font("Arial", 20));
                gc.setFill(currentColor);
                gc.fillText(text, startX, startY);
                textConsumer.accept(text); // Use the consumer to handle the text
                dynamicTextField.clear();
                dynamicTextField.setVisible(false);
                mainCanvas.setOnMouseClicked(null);
            }
        });
    }

    public void setEraserSize(int size) {
        this.eraserSize = size;
    }

    private void setEraser() {
        mainCanvas.setOnMouseDragged(e -> {
            double eraserHalfSize = eraserSize / 2;
            double x = e.getX() - eraserHalfSize;
            double y = e.getY() - eraserHalfSize;

            gc.setFill(Color.WHITE);
            gc.fillRect(x, y, eraserSize, eraserSize);

            DrawingCommand command = DrawingCommand.createEraser(userName, (int) x, (int) y, (int) (x + eraserSize), (int) (y + eraserSize), eraserSize);
            try {
                Message message = new Message(MessageConstants.DRAW_COMMAND, command.toJSON());
                Socket socket = new Socket(serverAddress, serverPort);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println(message.toJSON());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public void setBrush() {
        mainCanvas.setOnMousePressed(e -> {
            startX = e.getX();
            startY = e.getY();
            gc.beginPath();
            gc.moveTo(startX, startY);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setLineJoin(StrokeLineJoin.ROUND);
        });

        mainCanvas.setOnMouseDragged(e -> {
            double x = e.getX(), y = e.getY();
            gc.lineTo(x, y);
            gc.stroke();
            DrawingCommand command = DrawingCommand.createBrush(userName, (int) startX, (int) startY, (int) x, (int) y, currentColor.toString(), 2);
            try {
                Message message = new Message(MessageConstants.DRAW_COMMAND, command.toJSON());
                Socket socket = new Socket(serverAddress, serverPort);
                // Send the message using PrintWriter
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println(message.toJSON());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            startX = x;
            startY = y;
        });
        mainCanvas.setOnMouseReleased(e -> {
            gc.closePath();
        });
    }

    private void setMouseEventsForShapes() {
        mainCanvas.setOnMousePressed(e -> {
            startX = e.getX();
            startY = e.getY();
            overlayGc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
        });

        mainCanvas.setOnMouseDragged(e -> {
            double endX = e.getX();
            double endY = e.getY();
            overlayGc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
            drawShapeOnOverlay(startX, startY, endX, endY);
        });

        mainCanvas.setOnMouseReleased(e -> {
            double endX = e.getX();
            double endY = e.getY();
            overlayGc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
            drawShapeOnCanvas(startX, startY, endX, endY); // Render the final shape locally
            sendShapeToServer(startX, startY, endX, endY); // Send the command to the server
        });
    }

    private void sendShapeToServer(double x1, double y1, double x2, double y2) {
        String tool = (String) mainCanvas.getUserData();
        DrawingCommand command = null;
        switch (tool) {
            case "Line":
                command = DrawingCommand.createLine(userName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                break;
            case "Circle":
                command = DrawingCommand.createCircle(userName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                break;
            case "Rectangle":
                command = DrawingCommand.createRectangle(userName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                break;
            case "Eraser":
                command = DrawingCommand.createEraser(userName, (int) x1, (int) y1, (int) x2, (int) y2, eraserSize);
                break;
            case "Pentagon":
                double radiusPentagon = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                command = DrawingCommand.createPolygon(userName, x1, y1, radiusPentagon, currentColor.toString(), 2, 5);
                break;
            case "Hexagon":
                double radiusHexagon = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                command = DrawingCommand.createPolygon(userName, x1, y1, radiusHexagon, currentColor.toString(), 2, 6);
                break;
        }

        if (command != null) {  // Check if command is not null before proceeding
            try {
                Message message = new Message(MessageConstants.DRAW_COMMAND, command.toJSON());
                Socket socket = new Socket(serverAddress, serverPort);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                out.println(message.toJSON());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            System.out.println("No command generated for tool: " + tool);
        }
    }

    private void drawShapeOnOverlay(double x1, double y1, double x2, double y2) {
        String tool = (String) mainCanvas.getUserData();
        switch (tool) {
            case "Line":
                overlayGc.strokeLine(x1, y1, x2, y2);
                break;
            case "Circle":
                double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                overlayGc.strokeOval(x1 - radius, y1 - radius, 2 * radius, 2 * radius);
                break;
            case "Rectangle":
                overlayGc.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
                break;
            case "Pentagon":
                drawPolygonOnOverlay(x1, y1, x2, y2, 5);
                break;
            case "Hexagon":
                drawPolygonOnOverlay(x1, y1, x2, y2, 6);
                break;
        }
    }

    private void drawShapeOnCanvas(double x1, double y1, double x2, double y2) {
        String tool = (String) mainCanvas.getUserData();
        switch (tool) {
            case "Line":
                gc.strokeLine(x1, y1, x2, y2);
                break;
            case "Circle":
                double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                gc.strokeOval(x1 - radius, y1 - radius, 2 * radius, 2 * radius);
                break;
            case "Rectangle":
                gc.strokeRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
                break;
            case "Pentagon":
                drawPolygonOnCanvas(x1, y1, x2, y2, 5);
                break;
            case "Hexagon":
                drawPolygonOnCanvas(x1, y1, x2, y2, 6);
                break;
        }
    }

    private void drawPolygonOnOverlay(double x1, double y1, double x2, double y2, int sides) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double[] xPoints = new double[sides];
        double[] yPoints = new double[sides];
        for (int i = 0; i < sides; i++) {
            xPoints[i] = x1 + radius * Math.cos(2 * Math.PI * i / sides - Math.PI / 2);
            yPoints[i] = y1 + radius * Math.sin(2 * Math.PI * i / sides - Math.PI / 2);
        }
        overlayGc.strokePolygon(xPoints, yPoints, sides);
    }

    private void drawPolygonOnCanvas(double x1, double y1, double x2, double y2, int sides) {
        double radius = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double[] xPoints = new double[sides];
        double[] yPoints = new double[sides];
        for (int i = 0; i < sides; i++) {
            xPoints[i] = x1 + radius * Math.cos(2 * Math.PI * i / sides - Math.PI / 2);
            yPoints[i] = y1 + radius * Math.sin(2 * Math.PI * i / sides - Math.PI / 2);
        }
        gc.strokePolygon(xPoints, yPoints, sides);
    }
}
