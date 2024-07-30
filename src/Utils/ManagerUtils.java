/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Utils;

import Protocol.Message;
import Protocol.MessageConstants;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Optional;

import static Utils.AlertUtils.showInformationAlert;

public class ManagerUtils {

    public static void joinRequestDialog(String username, PrintWriter out) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText("Join Request");
        alert.setContentText("User " + username + " wants to share your whiteboard");

        ButtonType buttonAccept = new ButtonType("Accept");
        ButtonType buttonDecline = new ButtonType("Decline");

        alert.getButtonTypes().setAll(buttonAccept, buttonDecline);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.get() == buttonAccept) {
            out.println(new Message(MessageConstants.APPROVE_JOIN_REQUEST, username).toJSON());
        } else {
            out.println(new Message(MessageConstants.DECLINE_JOIN_REQUEST, username).toJSON());
        }
    }

    public static void sendCloseRequest(String serverAddress, int serverPort, String managerUserName) {
        // Notify server of closing and shut down application
        try (Socket socket = new Socket(serverAddress, serverPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            Message closeRequest = new Message(MessageConstants.MANAGER_CLOSED, managerUserName);
            out.println(closeRequest.toJSON());
            Platform.runLater(() -> Platform.exit());
            System.exit(0);
        } catch (IOException e) {
            Platform.runLater(() -> showInformationAlert(MessageConstants.REPLY_ERROR,
                    MessageConstants.CLOSE_WHITEBOARD_ERROR + e.getMessage()));
        }
    }

    public static void kickUser(String userName, String serverAddress, int serverPort) {
        // Create a confirmation dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Kick User");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to kick out " + userName + "?");

        ButtonType buttonYes = new ButtonType("Yes");
        ButtonType buttonNo = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonYes, buttonNo);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == buttonYes) {
            // Proceed to kick the user if 'Yes' is clicked
            try (Socket socket = new Socket(serverAddress, serverPort);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                Message kickRequest = new Message(MessageConstants.REQUEST_KICK_USER, userName);
                out.println(kickRequest.toJSON());
            } catch (IOException e) {
                showInformationAlert("Error", "Failed to send kick request: " + e.getMessage());
            }
        }
    }
}
