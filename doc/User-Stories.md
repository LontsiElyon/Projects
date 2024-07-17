# Informatics Project "Simon Goes Multiplayer" - Summer Semester 2024

# Mandatory User Stories and Sub-Tasks

## 1. Controller Initialization and Registration
**As a controller, I want to automatically register with my controller ID at startup so that the backend is aware of my status and availability.**

### Sub-Tasks
- **Controller-ID Registration**
  - Implement logic within the controller to automatically register with the backend using its unique ID upon startup.
- **Backend Registration Management**
  - Develop a method in the backend to process and manage incoming registrations from controllers.

## 2. RFID-based Player Login
**As a player with an RFID card, I want to be able to log in at a controller so that the backend can pair my user ID with the respective controller for the current session.**

### Sub-Tasks
- **RFID Login Implementation**
  - Integrate the RFID reader into the controller software to detect RFID cards.
  - Transmit RFID data to the backend for identification and session pairing.

- **Backend Pairing Logic**
  - Develop backend logic to receive RFID data and perform pairing with the appropriate controller.

## 3. Frontend-based Player Login
**As a player without an RFID card, I want to assign my account to a controller via the frontend so that I can participate in the game.**

### Sub-Tasks
- **Frontend Login Interface**
  - Create a user interface in the frontend allowing players without RFID cards to assign their account to an available controller.
- **Backend Assignment Logic**
  - Implement backend logic to process player-to-controller assignments initiated via the frontend.

## 4. Display of Meta Information
**As a player, I want to see my name and the current points/rounds on the OLED display so that I can track my progress during the game.**

### Sub-Tasks
- **OLED Display Control**
  - Program the control of the OLED display to show text.
  - Implement the display of player name, points, and round.

- **Data Transmission to Display**
  - Develop logic for transmitting relevant data from the backend to the controller over MQTT.

## 5. Game Start via Frontend
**As a player, I want to start the game using a button in the frontend so that the start occurs simultaneously and synchronously for all players.**

### Sub-Tasks
- **Frontend Start-Button Implementation**
  - Develop and integrate a start button into the frontend interface.
  - Implement logic to send a start signal to the backend when the button is pressed.

- **Backend Start Logic**
  - Program the backend action to initialize the game when the start signal is received from the frontend.
  - Synchronize the game start across all connected controllers to ensure all players begin at the same time.

## 6. Visualization of the Color Sequence
**As a player, I want to see the current color sequence displayed on the Neopixel LEDs so that I can press the colors in the correct order.**

### Sub-Tasks
- **LED Sequence Programming**
  - Develop control logic for the LEDs to display the color sequence sequentially.
- **Integration into the Game**
  - Adjust the game logic in the backend to generate and send the color sequence to the controller based on the game state.

## 7. Response to Player Input
**As a player, I want to make my inputs using the colored buttons to mimic the displayed color sequence.**

### Sub-Tasks
- **Button Input Logic**
  - Implement logic to detect and process button presses.
- **Input Verification**
  - Develop a method in the backend to verify whether the pressed colors match the displayed sequence.

## 8. Game Termination on Errors
**As a player, I want to receive immediate feedback if I press the wrong color so that I know I have been eliminated.**

### Sub-Tasks
- **Error Detection and Notification**
  - Develop logic in both the backend and controller to detect incorrect inputs and notify the player accordingly.

## 9. Timed Player Actions
**As a player, I want clear signals indicating when to start and when the input time has expired to complete my inputs promptly.**

### Sub-Tasks
- **Countdown Implementation**
  - Program a countdown at the start of each round, visible on the OLED display or through LEDs.
- **Timeout Logic**
  - Develop a method in the backend to detect the end of the input time and automatically disqualify players who are too slow.

## 10. Database Integration for Player Data
**As the backend, I want to store player data such as names, RFID tag numbers, and personal high scores in a MariaDB so that this data can be managed securely and persistently.**

### Sub-Tasks
- **Database Schema Creation**
  - Design and create the schema for player data in MariaDB.

- **JDBC Integration**
  - Implement the JDBC connection to MariaDB for reading and writing data.

## 11. Frontend Interface for Game Information
**As a player, I want a clear and intuitive user interface so that I can easily understand game information such as current rounds and winners.**

### Sub-Tasks
- **Frontend Design**
  - Design and implement the user interface using HTML/Bootstrap.

- **Data Binding**
  - Develop logic to display game information in the frontend retrieved via REST APIs.

## 12. Synchronous Game Control
**As the backend, I want to ensure that all players receive the same color sequence so that the game is fair and runs synchronously.**

### Sub-Tasks
- **Synchronization Logic**
  - Program backend logic to synchronize color sequences across all connected controllers.

- **Consistency Checking**
  - Test the implementation to ensure that all players indeed receive the same sequence.

---

## Additional (Optional) User Stories

### Z1. Display of Game and Round Results
**As a player, I want to see clear displays of results after each round and at the end of the game so that I can understand my progress and the outcome of the game.**

#### Sub-Tasks
- **Implementation of Result Displays**
  - Develop logic in the backend to calculate game and round results.
  - Display these results in the frontend using dynamic components.

### Z2. Difficulty Settings and Inactivity Timeout
**As a player, I want to be able to adjust the difficulty of the game and have the game automatically pause or end if inactive to ensure fair game balance and appropriate gameplay experience.**

#### Sub-Tasks
- **Difficulty Setting Implementation**
  - Develop a feature in both backend and frontend allowing adjustment of game levels.
- **Inactivity Timeout**
  - Implement a timeout logic that automatically pauses or ends the game when no activity is detected.

### Z3. Play Against a Bot
**As a player, I want to be able to play against artificial intelligence to test my skills even without human opponents.**

#### Sub-Tasks
- **Bot Implementation**
  - Develop a bot that operates stochastically or through a neural network.
- **Bot Integration into the Game**
  - Integrate the bot into the existing game, including adjustments at the user interface and backend.

### Z4. Swagger Documentation of REST APIs
**As a developer, I want detailed and interactive documentation of the REST APIs to facilitate development and testing.**

#### Sub-Tasks
- **Creation of Swagger Documentation**
  - Set up Swagger UI to document the REST APIs and enable interactive testing.

### Z5. Security Against SQL Injection
**As a developer, I want to secure the database queries against SQL injections to ensure the application's security.**

#### Sub-Tasks
- **Implementation of Security Measures**
  - Apply best practices and techniques such as prepared statements to prevent SQL injections.

### Z6. ER and SERM Diagram of the Database
**As a developer, I want a well-structured and comprehensible database design that is clearly documented.**

#### Sub-Tasks
- **Creation of ER and SERM Diagrams**
  - Design and document the database structure using the Entity-Relationship Model (ERM) and Structured Entity-Relationship Model (SERM).
