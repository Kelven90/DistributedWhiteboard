/**
 * Name: Kelven Lai    Student ID: 1255199
 */

package Protocol;

public class MessageConstants {
    // Request or Reply (type)
    public static final String REQUEST_CREATE_WHITEBOARD = "Request for creating whiteboard";
    public static final String REQUEST_JOIN_WHITEBOARD = "Request for joining whiteboard";
    public static final String SUCCESS_CREATE_WHITEBOARD = "WhiteBoard has been successfully created by the manager";
    public static final String SUCCESS_JOIN_WHITEBOARD = "Successful joining the whiteboard";
    public static final String FAILURE_CREATE_WHITEBOARD = "Failed to create a new whiteboard";
    public static final String FAILURE_JOIN_WHITEBOARD = "Failed to join the whiteboard";
    public static final String APPROVE_JOIN_REQUEST = "Request to join whiteboard approved by manager";
    public static final String DECLINE_JOIN_REQUEST = "Request to join whiteboard declined";
    public static final String USER_LEAVE = "User left the whiteboard session";
    public static final String MANAGER_CLOSED = "Manager closed the whiteboard session";
    public static final String REPLY_ERROR = "Error: There's an error while processing this request";
    public static final String USER_LIST_REQUEST = "Here's the updated user list";
    public static final String REQUEST_KICK_USER = "Manager's request to kick a user";
    public static final String DRAW_COMMAND = "This is drawing command: ";
    public static final String CHAT_MESSAGE = "Sending chat message: ";
    public static final String RECEIVE_CHAT_MESSAGE = "Receiving chat message: ";
    public static final String SUCCESS_SAVE_WHITEBOARD = "Successful saving the whiteboard!";
    public static final String FAILURE_SAVE_WHITEBOARD = "Error: There's an error while saving the whiteboard:";
    public static final String SUCCESS_OPEN_WHITEBOARD = "Successful opening the whiteboard!";
    public static final String FAILURE_OPEN_WHITEBOARD = "Error: There's an error while opening the whiteboard:";
    public static final String CLEAR_CANVAS = "Clearing Canvas:";
    public static final String OPEN_CANVAS = "Manager Opening Canvas:";

    // Reasons (data)
    public static final String HAS_MANAGER = "Failed Attempt: There is already a manager!";
    public static final String ABSENT_MANAGER = "Failed Attempt: There is no manager now";
    public static final String USERNAME_EXISTED = "Failed Attempt: The username has already existed, Please try again with " +
            "another username!";
    public static final String UNSUPPORTED_REQUEST_TYPE = "Unsupported request type";
    public static final String INVALID_MESSAGE = "Invalid message format";
    public static final String LEAVE_WHITEBOARD_ERROR = "Error leaving the whiteboard: ";
    public static final String CLOSE_WHITEBOARD_ERROR = "Error closing the whiteboard: ";
    public static final String SESSION_CLOSED = "The session will be closed.";
    public static final String KICK_USER = "You have been kicked out";

}
