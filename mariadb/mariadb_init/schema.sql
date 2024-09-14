
/**
 * @file schema.sql
 * @brief SQL schema for the RFID-based game system
 * @details This script creates the necessary tables to store information about controllers, players, sessions, RFID reads, frontend assignments, and display updates.
 */

/**
 * @brief Table to store information about controllers
 * @details This table keeps track of controller statuses and their heartbeat signals.
 */
CREATE TABLE Controllers (
    controller_id VARCHAR(50) PRIMARY KEY,  -- Unique identifier for each controller, serving as the primary key
    status ENUM('online', 'offline') DEFAULT 'offline',  -- Status of the controller, with a default value of 'offline'
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp of the last heartbeat received from the controller
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- Timestamp when the controller record was created
);

/**
 * @brief Table to store information about players
 * @details This table stores details about players, including their usernames, RFID tags, and high scores.
 */
CREATE TABLE Players (
    player_id INT AUTO_INCREMENT PRIMARY KEY,  -- Unique identifier for each player, auto-incremented
    user_name VARCHAR(100),  -- Name of the player
    rfid_tag VARCHAR(50) UNIQUE,  -- Unique RFID tag assigned to the player
    high_score INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP  -- Timestamp when the player record was created
);

/**
 * @brief Table to store session details
 * @details This table logs the player sessions and records which controller is used in each session.
 */
CREATE TABLE Sessions (
    session_id INT AUTO_INCREMENT PRIMARY KEY,  -- Unique identifier for each session, auto-incremented
    player_id INT,  -- Foreign key referencing Players table, identifies the player in the session
    controller_id VARCHAR(50),  -- Foreign key referencing Controllers table, identifies the controller used in the session
    login_method ENUM('RFID', 'Frontend') NOT NULL,  -- Method used to login, either via RFID or the frontend
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the session started
    end_time TIMESTAMP NULL,  -- Timestamp when the session ended, null if session is ongoing
    round INT DEFAULT 1,  -- The current round within the session, defaulting to 1

    -- Composite unique key
    UNIQUE KEY unique_session (controller_id, player_id,round),

    -- Foreign key relationships
    FOREIGN KEY (player_id) REFERENCES Players(player_id),  -- Establishes a relationship with the Players table
    FOREIGN KEY (controller_id) REFERENCES Controllers(controller_id)  -- Establishes a relationship with the Controllers table
);

/**
 * @brief Table to log RFID read events
 * @details Stores logs of RFID reads by controllers, including the timestamp and related player information.
 */
CREATE TABLE RfidAssignments(
    log_id INT AUTO_INCREMENT PRIMARY KEY,  -- Unique identifier for each RFID log entry, auto-incremented
    player_id INT,  -- Foreign key referencing Players table, identifies the player associated with the RFID log
    controller_id VARCHAR(50),  -- Foreign key referencing Controllers table, identifies the controller where the RFID was read
    rfid_tag VARCHAR(50),  -- The RFID tag read by the controller
    read_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the RFID was read
    FOREIGN KEY (player_id) REFERENCES Players(player_id),  -- Establishes a relationship with the Players table
    FOREIGN KEY (controller_id) REFERENCES Controllers(controller_id)  -- Establishes a relationship with the Controllers table
);

/**
 * @brief Table to record frontend assignments of players to controllers
 * @details Logs when a player is assigned to a controller via the frontend interface.
 */
CREATE TABLE FrontendAssignments (
    assignment_id INT AUTO_INCREMENT PRIMARY KEY,  -- Unique identifier for each frontend assignment, auto-incremented
    player_id INT,  -- Foreign key referencing Players table, identifies the player in the assignment
    controller_id VARCHAR(50),  -- Foreign key referencing Controllers table, identifies the controller in the assignment
    assignment_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp when the assignment was made
    FOREIGN KEY (player_id) REFERENCES Players(player_id),  -- Establishes a relationship with the Players table
    FOREIGN KEY (controller_id) REFERENCES Controllers(controller_id)  -- Establishes a relationship with the Controllers table
);

/**
 * @brief Table to display information
 * @details This table stores information to be displayed for each controller and player, including points and rounds.
 */
CREATE TABLE DisplayInfo (
    controller_id VARCHAR(50),  -- Identifier for the controller
    player_id INT,              -- Foreign key referencing Players table
    points INT,                 -- Points to be displayed
    round INT,                  -- Round number to be displayed
    username VARCHAR(50),       -- Username associated with the player
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,  -- Last update timestamp
    
    -- Composite primary key to ensure uniqueness
    PRIMARY KEY (controller_id, round),
    
    -- Foreign key constraints
    FOREIGN KEY (controller_id, player_id, round) REFERENCES Sessions(controller_id, player_id, round),  -- Reference to Sessions table
    FOREIGN KEY (player_id) REFERENCES Players(player_id)  -- Reference to Players table
);


CREATE TABLE DisplayUpdates (
    update_id INT AUTO_INCREMENT PRIMARY KEY,  -- Unique identifier for each update
    controller_id VARCHAR(50),  -- Foreign key referencing Controllers table
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Timestamp of the update
    FOREIGN KEY (controller_id) REFERENCES Controllers(controller_id)  -- Reference to Controllers table
);

