/**
 * Name: Kelven Lai    Student ID: 1255199
 */

import Utils.DrawingUtils;
import Utils.DrawingTools;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.json.simple.parser.ParseException;

import javax.net.ServerSocketFactory;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static Utils.AlertUtils.*;
import static Utils.ManagerUtils.*;

public class CreateWhiteBoard extends Application{
    private static String serverAddress;
    private static int serverPort;
    private static ServerSocket serverSocket;
    private static volatile boolean isRunning = true;
    private static String managerUserName;
    private static final Object lock = new Object();
    private static boolean serverStarted = false;
    private ObservableList<String> userListData = FXCollections.observableArrayList();
    private static ListView<String> userList;
    private static TextArea chatArea;
    private TextField chatInputField;
    private static CreateWhiteBoard instance;
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
    private static File currentFile;
    private DrawingTools drawingTools = new DrawingTools();
    private static List<DrawingCommand> canvasState = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws ParseException {
        if (args.length != 3) {
            System.out.println("Usage: java CreateWhiteBoard <serverIPAddress> <serverPort> <managerName>");
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
        managerUserName = args[2];

        new Thread(() -> startServer(serverPort)).start();
        waitForServerStart();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            shutdownServer();
        }));

        launch(args);
    }

    private static void startServer(int port) {
        ServerSocketFactory factory = ServerSocketFactory.getDefault();
        try {
            serverSocket = factory.createServerSocket(port);
            synchronized (lock) {
                serverStarted = true;
                lock.notifyAll();
            }
            System.out.println("Server started. Listening on port " + port);
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client's request from: " + clientSocket.getInetAddress().getHostAddress());
                new Thread(new UserHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            if (isRunning) {
                System.err.println("Error starting the server: " + e.getMessage());
                Platform.runLater(() -> {
                    showInformationAlert(MessageConstants.REPLY_ERROR, "Server Error: " + e.getMessage());
                    Platform.exit();
                    System.exit(1);
                });
            }
        }
    }

    private static void waitForServerStart() {
        synchronized (lock) {
            while (!serverStarted) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Thread interrupted while waiting for server to start.");
                }
            }
        }
    }

    private static void shutdownServer() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error while shutting down server: " + e.getMessage());
            }
        }
    }

    private static void connectToServer(String userName, String serverAddress, int serverPort) {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // Request to create whiteboard (manager)

            Message request = new Message(MessageConstants.REQUEST_CREATE_WHITEBOARD, userName);
            String jsonRequest = request.toJSON();
            System.out.println("Sending request: " + jsonRequest);
            out.println(jsonRequest);
            System.out.println("Sent request to create whiteboard for: " + userName);

            String responseLine;
            while ((responseLine = in.readLine()) != null) {
                System.out.println("Received response: " + responseLine);
                Message response = Message.fromJSON(responseLine);
                if (response.getType().equals(MessageConstants.SUCCESS_CREATE_WHITEBOARD)) {
                    Platform.runLater(() -> showInformationAlert(MessageConstants.REQUEST_CREATE_WHITEBOARD, MessageConstants.SUCCESS_CREATE_WHITEBOARD));
                }
                if (response.getType().equals(MessageConstants.FAILURE_CREATE_WHITEBOARD)) {
                    Platform.runLater(() -> showInformationAlert(MessageConstants.REQUEST_CREATE_WHITEBOARD, MessageConstants.FAILURE_CREATE_WHITEBOARD));
                }
                if (response.getType().equals(MessageConstants.USER_LIST_REQUEST)) {
                    Platform.runLater(() -> updateUserList(response.getData(), userList));
                }
                if (response.getType().equals(MessageConstants.REQUEST_JOIN_WHITEBOARD)) {
                    Platform.runLater(() -> joinRequestDialog(response.getData(), out));
                }
                // Handle drawing commands from server
                if (response.getType().equals(MessageConstants.DRAW_COMMAND)) {
                    try {
                        DrawingCommand drawingCommand = DrawingCommand.fromJSON(response.getData());
                        canvasState.add(drawingCommand);
                        Platform.runLater(() -> instance.handleDrawingCommand(drawingCommand));
                    } catch (ParseException ex) {
                        System.err.println("Error parsing drawing command: " + ex.getMessage());
                    }
                }
                if (response.getType().equals(MessageConstants.RECEIVE_CHAT_MESSAGE)) {
                    String chatMessage = response.getData();
                    appendMessageToChat(chatMessage);
                }
            }
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            //showInformationAlert(MessageConstants.REPLY_ERROR, MessageConstants.HAS_MANAGER);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleDrawingCommand(DrawingCommand drawingCommand) {
        DrawingUtils.handleDrawingCommand(this.gc, drawingCommand);
    }

    @Override
    public void start(Stage primaryStage) {
        instance = this;
        primaryStage.setTitle("Manager's Whiteboard");

        VBox root = new VBox();
        MenuBar menuBar = new MenuBar();

        // Create File menu
        Menu fileMenu = new Menu("File");
        MenuItem newItem = new MenuItem("New");
        MenuItem openItem = new MenuItem("Open");
        MenuItem saveItem = new MenuItem("Save");
        MenuItem saveAsItem = new MenuItem("Save As");
        MenuItem closeItem = new MenuItem("Close");
        fileMenu.getItems().addAll(newItem, openItem, saveItem, saveAsItem, new SeparatorMenuItem(), closeItem);
        menuBar.getMenus().add(fileMenu);

        // Ensure MenuBar is added to the root at the very top
        root.getChildren().add(menuBar);

        ToolBar toolBar = new ToolBar();

        mainCanvas = new Canvas(800, 600);
        overlayCanvas = new Canvas(800, 600);
        overlayCanvas.setMouseTransparent(true);
        stackPane = new StackPane();  // Initialize stackPane
        stackPane.getChildren().addAll(mainCanvas, overlayCanvas);

        gc = mainCanvas.getGraphicsContext2D();
        overlayGc = overlayCanvas.getGraphicsContext2D();

        userList = new ListView<>(userListData);
        userList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedUser = userList.getSelectionModel().getSelectedItem();
                if (selectedUser != null && !selectedUser.equals(managerUserName)) {
                    kickUser(selectedUser, serverAddress, serverPort);
                }
            }
        });

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

        HBox closeContainer = new HBox(5);
        Button closeButton = new Button("Close Whiteboard Session");
        closeButton.getStyleClass().add("red-border-button");
        closeButton.setOnAction(e -> sendCloseRequest(serverAddress, serverPort, managerUserName));
        closeContainer.getChildren().addAll(closeButton);

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
        toolBar.getItems().addAll(toolSelection, sliderContainer, new Separator(), closeContainer);

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

        setupMenuActions(newItem, openItem, saveItem, saveAsItem, closeItem);

        new Thread(() -> connectToServer(managerUserName, serverAddress, serverPort)).start();
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Closing application...");
            shutdownServer();
            Platform.exit();
            System.exit(0);
        });
    }

    private void setupMenuActions(MenuItem newItem, MenuItem openItem, MenuItem saveItem, MenuItem saveAsItem, MenuItem closeItem) {
        newItem.setOnAction(e -> handleNew());
        openItem.setOnAction(e -> handleOpen());
        saveItem.setOnAction(e -> handleSave());
        saveAsItem.setOnAction(e -> handleSaveAs());
        closeItem.setOnAction(e -> handleClose());
    }

    private void handleNew() {
        // Clear the whiteboard and reset the current file
        initDrawing();
        currentFile = null;
        synchronized (canvasState) {
            canvasState.clear();
        }
        try {
            Socket socket = new Socket(serverAddress, serverPort);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(new Message(MessageConstants.CLEAR_CANVAS, managerUserName).toJSON());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleSave() {
        if (currentFile == null) {
            handleSaveAs();
        } else {
            saveCanvasToFile(currentFile);
        }
    }

    private void handleSaveAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Whiteboard As");
        fileChooser.setInitialFileName("whiteboard.wbd");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Whiteboard files (*.wbd)", "*.wbd"));
        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            currentFile = file;
            saveCanvasToFile(currentFile);
        }
    }

    private void saveCanvasToFile(File file) {
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            synchronized (canvasState) {
                oos.writeObject(new ArrayList<>(canvasState));
            }
            oos.close();
            fos.close();
            showInformationAlert("Message: ", MessageConstants.SUCCESS_SAVE_WHITEBOARD);
        } catch (IOException ex) {
            showInformationAlert(MessageConstants.FAILURE_SAVE_WHITEBOARD, ex.getMessage());
        }
    }


    private void handleOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Whiteboard");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Whiteboard files (*.wbd)", "*.wbd"));
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            loadCanvasFromFile(file);
            currentFile = file;
        }
    }

    private void loadCanvasFromFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            List<DrawingCommand> loadedCommands = (List<DrawingCommand>) ois.readObject();
            synchronized (canvasState) {
                canvasState.clear();
                canvasState.addAll(loadedCommands);
            }
            ois.close();
            fis.close();
            redrawCanvas();
            sendCanvasToUsers(canvasState);
            // Optionally, show a success message
            showInformationAlert("Message: ", MessageConstants.SUCCESS_OPEN_WHITEBOARD);
        } catch (IOException | ClassNotFoundException ex) {
            // Optionally, show an error message
            showInformationAlert(MessageConstants.FAILURE_OPEN_WHITEBOARD, ex.getMessage());
        }
    }

    private void sendCanvasToUsers(List<DrawingCommand> loadedCommands) {
        try {
            Socket socket = new Socket(serverAddress, serverPort);
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            out.println(new Message(MessageConstants.CLEAR_CANVAS, managerUserName).toJSON());
            for (DrawingCommand command : loadedCommands) {
                out.println(new Message(MessageConstants.OPEN_CANVAS, command.toJSON()).toJSON());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void redrawCanvas() {
        initDrawing();
        synchronized (canvasState) {
            for (DrawingCommand command : canvasState) {
                DrawingUtils.handleDrawingCommand(gc, command);
            }
        }
    }

    private void handleClose() {
        sendCloseRequest(serverAddress, serverPort, managerUserName);
    }

    // Function to send chat messages
    private void sendChatMessage(String message) {
        if (!message.isEmpty()) {
            String formattedMessage = managerUserName + ": " + message;
            try (Socket socket = new Socket(serverAddress, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(new Message(MessageConstants.CHAT_MESSAGE, formattedMessage).toJSON());
            } catch (IOException ex) {
                System.err.println("Failed to send chat message: " + ex.getMessage());
                chatArea.appendText("Failed to send message\n");
            }
        }
    }

    // Method to append messages to chat area
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
                    DrawingCommand command = DrawingCommand.createText(managerUserName, (int) startX, (int) startY, text, currentColor.toString());
                    canvasState.add(command);
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
            DrawingCommand command = DrawingCommand.createEraser(managerUserName, (int) x, (int) y, (int) (x + eraserSize), (int) (y + eraserSize), eraserSize);
            canvasState.add(command);
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
            DrawingCommand command = DrawingCommand.createBrush(managerUserName, (int) startX, (int) startY, (int) x, (int) y, currentColor.toString(), 2);
            canvasState.add(command);
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
                command = DrawingCommand.createLine(managerUserName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                canvasState.add(command);
                break;
            case "Circle":
                command = DrawingCommand.createCircle(managerUserName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                canvasState.add(command);
                break;
            case "Rectangle":
                command = DrawingCommand.createRectangle(managerUserName, (int) x1, (int) y1, (int) x2, (int) y2, currentColor.toString(), 2);
                canvasState.add(command);
                break;
            case "Eraser":
                command = DrawingCommand.createEraser(managerUserName, (int) x1, (int) y1, (int) x2, (int) y2, eraserSize);
                canvasState.add(command);
                break;
            case "Pentagon":
                double radiusPentagon = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                command = DrawingCommand.createPolygon(managerUserName, x1, y1, radiusPentagon, currentColor.toString(), 2, 5);
                canvasState.add(command);
                break;
            case "Hexagon":
                double radiusHexagon = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
                command = DrawingCommand.createPolygon(managerUserName, x1, y1, radiusHexagon, currentColor.toString(), 2, 6);
                canvasState.add(command);
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