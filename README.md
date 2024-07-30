## Introduction
This is a univesity project for implementing a distributed shared whiteboard application.
It is a dynamic, collaborative platform that enables multiple users to interact simultaneously on a common canvas.

## How to run
The first user (manager) creates a new whiteboard session
    - java CreateWhiteBoard <serverIPAddress> <serverPort> username
Other users can ask to join the whiteboard application any time by inputting server’s IP address and port number
    - java JoinWhiteBoard <serverIPAddress> <serverPort> username


### System Components

#### Client Applications

- **CreateWhiteBoard**: Manages the session for the whiteboard creator or manager. It initiates the server, sends commands to other clients, and handles administrative tasks such as inviting users, approving join requests, and managing the session (including closing the session or kicking users).
- **JoinWhiteBoard**: Used by clients wanting to join an existing whiteboard session. It handles receiving drawing commands, participating in the shared canvas, and managing personal interactions like sending messages or leaving the session.

#### Server Application

- **UserHandler**: Acts as a centralized server handler for individual client connections. It manages user sessions, relays drawing commands and messages between clients, and maintains the state of the whiteboard session, including the list of connected users.

#### Utility Classes

- **DrawingUtils**: Provides static methods to handle the drawing operations on the canvas, interpreting DrawingCommand objects to render graphical representations.
- **ManagerUtils**: Contains methods that facilitate managerial actions, such as handling join requests, closing sessions, and kicking users, often involving decision dialogs.
- **AlertUtils**: Offers static methods to show different types of alerts and informational dialogs to the user.
- **DrawingTools**: Manages custom cursors used for different drawing tools within the application, enhancing the user interface.

#### Protocol Classes

- **DrawingCommand**: Encapsulates all the information necessary for drawing operations, such as drawing type, coordinates, color, and stroke width.
- **Message**: Used for creating and parsing messages that are sent and received over the network, supporting both command and control operations within the application.

#### Constants Class

- **MessageConstants**: Defines constants used across the application for identifying types of messages and commands, ensuring consistency in the communication protocol.
