/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Utils;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

public class AlertUtils {
    public static void showInformationAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void updateUserList(String userListString, ListView<String> userList) {
        // Update user list data in the GUI
        ObservableList<String> updatedList = FXCollections.observableArrayList(userListString.split(","));
        Platform.runLater(() -> {
            userList.setItems(updatedList);
        });
    }
}

