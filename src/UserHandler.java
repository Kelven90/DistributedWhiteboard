/**
 * Name: Kelven Lai    Student ID: 1255199
 */

import Protocol.Message;
import Protocol.MessageConstants;
import Protocol.DrawingCommand;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserHandler implements Runnable {
    private final Socket socket;
    private static boolean hasManager = false;
    private static String managerUserName;
    private static List<String> userList = new CopyOnWriteArrayList<>();
    private static ConcurrentHashMap<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, PrintWriter> pendingUserWriters = new ConcurrentHashMap<>();
    private static List<DrawingCommand> drawingCommands = Collections.synchronizedList(new ArrayList<>());

    private boolean isRunning;

    public UserHandler(Socket socket) {
        this.socket = socket;
        isRunning = true;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String inputLine;
            while ((inputLine = reader.readLine()) != null && isRunning) {
                System.out.println("Received: " + inputLine);
                try {
                    Message request = Message.fromJSON(inputLine);
                    Message replyMessage;
                    switch (request.getType()) {
                        case MessageConstants.REQUEST_CREATE_WHITEBOARD:
                            synchronized (UserHandler.class) {
                                replyMessage = addUser(request.getData(), true, writer);
                                writer.println(replyMessage.toJSON());
                            }
                            break;
                        case MessageConstants.REQUEST_JOIN_WHITEBOARD:
                            handleJoinRequest(request, writer);
                            break;
                        case MessageConstants.APPROVE_JOIN_REQUEST:
                            processJoinResponse(request.getData(), true);
                            break;
                        case MessageConstants.DECLINE_JOIN_REQUEST:
                            processJoinResponse(request.getData(), false);
                            break;
                        case MessageConstants.USER_LEAVE:
                            handleLeaveRequest(request);
                            break;
                        case MessageConstants.MANAGER_CLOSED:
                            handleCloseRequest();
                            break;
                        case MessageConstants.REQUEST_KICK_USER:
                            handleKickRequest(request);
                            break;
                        case MessageConstants.DRAW_COMMAND:
                            try {
                                DrawingCommand drawingCommand = DrawingCommand.fromJSON(request.getData());
                                broadcastDrawingCommand(drawingCommand, drawingCommand.getUser(), false);
                            } catch (ParseException e) {
                                e.printStackTrace();
                                writer.println(new Message(MessageConstants.REPLY_ERROR, "Error parsing drawing command").toJSON());
                            }
                            break;
                        case MessageConstants.OPEN_CANVAS:
                            try {
                                DrawingCommand drawingCommand = DrawingCommand.fromJSON(request.getData());
                                broadcastDrawingCommand(drawingCommand, drawingCommand.getUser(), true);
                            } catch (ParseException e) {
                                e.printStackTrace();
                                writer.println(new Message(MessageConstants.REPLY_ERROR, "Error parsing drawing command").toJSON());
                            }
                            break;
                        case MessageConstants.CHAT_MESSAGE:
                            broadcastChatMessage(request);
                            break;
                        case MessageConstants.CLEAR_CANVAS:
                            broadcastInitializeCanvas(request.getData());
                            break;
                        default:
                            writer.println(new Message(MessageConstants.REPLY_ERROR, MessageConstants.UNSUPPORTED_REQUEST_TYPE).toJSON());
                    }
                } catch (ParseException e) {
                    writer.println(new Message(MessageConstants.REPLY_ERROR, MessageConstants.INVALID_MESSAGE).toJSON());
                }
            }
        } catch (IOException e) {
           // System.err.println("Error handling client socket: " + e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private static synchronized Message addUser(String userName, boolean isManager, PrintWriter writer) {
        Message replyMessage;

        // Check if the username already exists
        if (clientWriters.containsKey(userName)) {
            System.out.println("User " + userName + " already exists");
            replyMessage = isManager
                    ? new Message(MessageConstants.FAILURE_CREATE_WHITEBOARD, MessageConstants.HAS_MANAGER)
                    : new Message(MessageConstants.FAILURE_JOIN_WHITEBOARD, MessageConstants.USERNAME_EXISTED);
            return replyMessage;
        }

        // Check if a manager already exists when adding a new manager
        if (isManager) {
            if (hasManager) {
                // System.out.println(MessageConstants.HAS_MANAGER);
                return new Message(MessageConstants.FAILURE_CREATE_WHITEBOARD, MessageConstants.HAS_MANAGER);
            }
            managerUserName = userName;
            hasManager = true;
        }

        // Add user to the list
        clientWriters.put(userName, writer);
        userList.add(userName);
        System.out.println(userName + " has been added to the user list.");
        sendUserListToAllClients();

        return isManager ? new Message(MessageConstants.SUCCESS_CREATE_WHITEBOARD, userName)
                : new Message(MessageConstants.SUCCESS_JOIN_WHITEBOARD, userName);
    }

    private synchronized void handleJoinRequest(Message request, PrintWriter userWriter) {
        String userName = request.getData();

        // Check for duplicate username before proceeding
        if (clientWriters.containsKey(userName) || pendingUserWriters.containsKey(userName)) {
            System.out.println("Duplicate join request for username: " + userName);
            userWriter.println(new Message(MessageConstants.REPLY_ERROR, MessageConstants.USERNAME_EXISTED).toJSON());
            return;
        }

        // Ensure that a manager exists
        if (managerUserName == null) {
            System.out.println("Manager not found! ");
            userWriter.println(new Message(MessageConstants.REPLY_ERROR, MessageConstants.ABSENT_MANAGER).toJSON());
            return;
        }
        // Get the manager writer
        PrintWriter managerWriter = clientWriters.get(managerUserName);
        // Store user writer temporarily until manager responds
        pendingUserWriters.put(userName, userWriter);
        // Forward the join request to the manager
        try {
            System.out.println("Forwarding join request to manager: " + request);
            managerWriter.println(request.toJSON()); // Forward the original JSON request
        } catch (Exception e) {
            System.err.println("Error sending join request to manager: " + e.getMessage());
            userWriter.println(new Message(MessageConstants.REPLY_ERROR, "Manager communication error").toJSON());
            pendingUserWriters.remove(userName); // Clean up
        }
    }

    private void broadcastDrawingCommand(DrawingCommand command, String userName, boolean includeOriginator) {
        drawingCommands.add(command);
        String commandJson = command.toJSON();
        Message message = new Message(MessageConstants.DRAW_COMMAND, commandJson);
        String messageJson = message.toJSON();

        // Broadcast to all clients
        for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
            String username = entry.getKey();
            PrintWriter writer = entry.getValue();
            // Check if the originator should be included in the broadcast
            if (includeOriginator || !username.equals(userName)) {
                writer.println(messageJson);
            }
        }
    }


    private void broadcastInitializeCanvas(String userName) {
        Message message = new Message(MessageConstants.CLEAR_CANVAS, userName);
        String messageJson = message.toJSON();
        // Broadcast to all clients except the originating user
        for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
            String username = entry.getKey();
            PrintWriter writer = entry.getValue();
            if (!username.equals(userName)) {
                writer.println(messageJson);
            }
        }
    }

    private void sendCurrentWhiteboardState(PrintWriter writer) {
        for (DrawingCommand command : drawingCommands) {
            writer.println(new Message(MessageConstants.DRAW_COMMAND, command.toJSON()).toJSON());
        }
    }

    private synchronized void handleLeaveRequest(Message request) {
        String userName = request.getData();
        clientWriters.remove(userName);
        removeUser(userName);
    }

    public synchronized static void removeUser(String userName) {
        userList.remove(userName);
        sendUserListToAllClients(); // Send updated user list to all clients
    }

    private void handleCloseRequest() {
        // Verify if the user attempting to close the session is the manager
        Message closeMessage = new Message(MessageConstants.MANAGER_CLOSED, managerUserName);
        // Send the close message to all clients
        clientWriters.values().forEach(writer -> writer.println(closeMessage.toJSON()));
        // Clear all users since the whiteboard session is closed
        clientWriters.clear();
        userList.clear();
        hasManager = false;
        managerUserName = null;
    }

    private synchronized void processJoinResponse(String userName, boolean isApproved) {
        PrintWriter userWriter = pendingUserWriters.remove(userName);
        if (userWriter != null) {
            if (isApproved) {
                // Add user to the client writers and user list
                clientWriters.put(userName, userWriter);
                userList.add(userName);
                System.out.println(userName + " has been added to the user list.");
                userWriter.println(new Message(MessageConstants.SUCCESS_JOIN_WHITEBOARD, userName).toJSON());
                sendUserListToAllClients();
                sendCurrentWhiteboardState(userWriter);
            } else {
                userWriter.println(new Message(MessageConstants.DECLINE_JOIN_REQUEST, "Join request declined by manager").toJSON());
            }
        }
    }

    private void handleKickRequest(Message request) {
        String userName = request.getData();
        PrintWriter userWriter = clientWriters.remove(userName);
        if (userWriter != null) {
            userList.remove(userName);
            sendUserListToAllClients();
            userWriter.println(new Message(MessageConstants.KICK_USER, userName).toJSON());
            userWriter.close();
        }
    }

    private synchronized static void sendUserListToAllClients() {
        Message userListMessage = new Message(MessageConstants.USER_LIST_REQUEST, String.join(",", userList));
        String messageJson = userListMessage.toJSON();

        // Iterate through all the client writers and send the updated user list
        clientWriters.values().forEach(writer -> writer.println(messageJson));
    }

    private void broadcastChatMessage(Message request) {
        String userName = request.getData();
        Message chatMessage = new Message(MessageConstants.RECEIVE_CHAT_MESSAGE, request.getData());

        for (Map.Entry<String, PrintWriter> entry : clientWriters.entrySet()) {
            String username = entry.getKey();
            PrintWriter writer = entry.getValue();
            // Exclude the specified username
            if (!username.equals(userName)) {
                // Send the JSON message to the client
                writer.println(chatMessage.toJSON());
            }
        }
    }

    private void closeSocket() {
        try {
            if (!socket.isClosed()) {
                socket.close();
                System.out.println("Socket closed.");
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }
}