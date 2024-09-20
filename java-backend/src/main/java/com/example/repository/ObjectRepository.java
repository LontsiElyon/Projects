/**
 * @file ObjectRepository.java
 * @brief The ObjectRepository class provides methods for performing database operations related to controllers, players, sessions,
 * and assignments.
 * 
 * The ObjectRepository class provides methods for performing database operations related to controllers, players, sessions,
 * and assignments. It includes functionality for CRUD operations on controllers, players, and sessions, as well as handling
 * RFID and frontend assignments. The class ensures interaction with the database is managed efficiently and correctly.
 * 
 * @defgroup object_repository Object Repository
 * @brief This module contains database operations for managing controllers, players, and sessions in the application.
 * 
 * The ObjectRepository class provides various methods for interacting with the database. This includes
 * CRUD operations on controllers, players, sessions, and managing RFID/Frontend assignments.
 * @{
 */
package com.example.repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;


import java.util.ArrayList;
import java.util.List;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @brief The repository class for performing database operations.
 * 
 * This class contains methods for handling database CRUD operations for controllers, players, sessions,
 * and RFID or frontend assignments. Each method is designed to interact with the database using Vert.x JDBC or SQL clients.
 */
public class ObjectRepository {

    private static final Logger logger = LoggerFactory.getLogger(ObjectRepository.class);

    private final Pool jdbcPool;
    /**
     * @brief Constructor for ObjectRepository
     * 
     * Initializes the repository with the provided JDBC pool.
     * 
     * @param jdbcPool The JDBC pool used for database queries.
     */
    public ObjectRepository(Pool jdbcPool) {
        this.jdbcPool = jdbcPool;
    }
    
    /**
     * @ingroup object_repository
     * @brief Inserts or updates a controller's status to 'online'.
     * 
     * This method inserts a new controller or updates its status to 'online' if it already exists in the database.
     * 
     * @param controllerId The unique ID of the controller.
     * @param resultHandler The result handler that returns success or failure.
     */
    public void insertController(String controllerId, Handler<AsyncResult<Void>> resultHandler) {
        // Prepare SQL statement to insert controller data
        String sql = "INSERT INTO Controllers (controller_id, status) VALUES (?, 'online') " +
             "ON DUPLICATE KEY UPDATE status = 'online', last_heartbeat = NOW()";

        logger.debug("Attempting to insert/update controller with ID: {}", controllerId);
    
        jdbcPool.preparedQuery(sql)
                .execute(Tuple.of(controllerId), ar -> {
                    if (ar.succeeded()) {
                        logger.info("Controller inserted/updated successfully: {}", controllerId);
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        logger.error("Failed to insert/update controller: {}", ar.cause().getMessage());
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
        
    
    }
    
    /**
     * @ingroup object_repository
     * @brief Fetches all online controllers.
     * 
     * This method retrieves a list of controllers that are currently online from the database.
     * 
     * @param resultHandler The result handler that returns a JsonArray of online controllers or a failure.
     */
    public void fetchControllers(Handler<AsyncResult<JsonArray>> resultHandler) {
        // SQL query to select controller_id and status from the Controllers table where the status is 'online'
        String sql = "SELECT controller_id, status FROM Controllers WHERE status = 'online'";
    
        // Execute the SQL query using the JDBC client
        jdbcPool.query(sql).execute(ar -> {
            if (ar.succeeded()) {
                // If the query is successful, retrieve the result set
                RowSet<Row> rows = ar.result();
                JsonArray controllersArray = new JsonArray();
    
                // Iterate over each row in the result set
                for (Row row : rows) {
                    // Create a JsonObject for each controller with its ID and status
                    JsonObject controller = new JsonObject()
                            .put("controller_id", row.getString("controller_id")) // Get the controller ID
                            .put("status", row.getString("status")); // Get the controller status
                    controllersArray.add(controller); // Add the JsonObject to the JsonArray
                }
    
                // Handle the result with a successful future containing the JsonArray of controllers
                resultHandler.handle(Future.succeededFuture(controllersArray));
            } else {
                // If the query failed, handle the result with a failed future containing the cause of the failure
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
    
    /**
     * @ingroup object_repository
     * @brief Retrieves a list of connected controllers.
     * 
     * This method retrieves all controller IDs from active sessions where a player is associated.
     * 
     * @param resultHandler The result handler that returns a list of connected controller IDs or a failure.
     */
    public void getConnectedControllers(Handler<AsyncResult<List<String>>> resultHandler) {
        // SQL query to retrieve all controller IDs from active sessions (with a player associated)
        String query = "SELECT controller_id FROM Sessions WHERE end_time IS NULL AND player_id IS NOT NULL";
    
        jdbcPool.query(query).execute(ar -> {
            if (ar.succeeded()) {
                List<String> connectedControllers = new ArrayList<>();
                RowSet<Row> rows = ar.result();
                for (Row row : rows) {
                    connectedControllers.add(row.getString("controller_id"));
                }
                resultHandler.handle(Future.succeededFuture(connectedControllers));
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    /**
     * @ingroup object_repository
     * @brief Checks if a controller is in use based on RFID assignments.
     * 
     * This method checks the database to determine if a controller is currently in use based on RFID assignments.
     * 
     * @param controllerId The unique ID of the controller.
     * @param resultHandler The result handler that returns true if the controller is in use, otherwise false.
     */
    public void isControllerInUse(String controllerId, Handler<AsyncResult<Boolean>> resultHandler) {
        // Query the database to check if the controller is already in use
        String query = "SELECT COUNT(*) FROM RfidAssignments WHERE controller_id = ?";
        jdbcPool.preparedQuery(query)
              .execute(Tuple.of(controllerId), ar -> {
                  if (ar.succeeded()) {
                      RowSet<Row> rows = ar.result();
                      if (rows.iterator().hasNext()) {
                          Row row = rows.iterator().next();
                          int count = row.getInteger(0);
                          resultHandler.handle(Future.succeededFuture(count > 0));
                      } else {
                          resultHandler.handle(Future.succeededFuture(false));
                      }
                  } else {
                      resultHandler.handle(Future.failedFuture(ar.cause()));
                  }
              });
    }
    

    public void isControllerInUseInFrontend(String controllerId, Handler<AsyncResult<Boolean>> resultHandler) {
        // Query the database to check if the controller is already in use in frontend assignments
        String query = "SELECT COUNT(*) FROM FrontendAssignments WHERE controller_id = ?";
        jdbcPool.preparedQuery(query)
                .execute(Tuple.of(controllerId), ar -> {
                    if (ar.succeeded()) {
                        RowSet<Row> rows = ar.result();
                        if (rows.iterator().hasNext()) {
                            Row row = rows.iterator().next();
                            int count = row.getInteger(0);
                            resultHandler.handle(Future.succeededFuture(count > 0));
                        } else {
                            resultHandler.handle(Future.succeededFuture(false));
                        }
                    } else {
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }
    
    /**
     * @ingroup object_repository
     * @brief Inserts a new player into the database.
     * 
     * This method registers a player with a given username and returns the player's unique ID.
     * If the username already exists, it updates the existing player's record.
     * 
     * @param username The name of the player.
     * @param resultHandler The result handler that returns the player ID or a failure.
     */
     public void insertPlayer(String username, Handler<AsyncResult<Integer>> resultHandler) {
        // SQL query to insert a player into the Players table with username.
       // If the username already exists, update the existing record's username.
      // Uses LAST_INSERT_ID() to get the player_id of the existing or new row.
        String sql = "INSERT INTO Players (user_name) VALUES (?) ON DUPLICATE KEY UPDATE user_name = VALUES(user_name), player_id = LAST_INSERT_ID(player_id)";

        // Execute the prepared SQL query using a tuple containing the username
        jdbcPool.preparedQuery(sql)
                .execute(Tuple.of(username), ar -> {
                    if (ar.succeeded()) {
                    // Successfully executed SQL, now get the auto-generated player ID
                       int playerID = ar.result().property(JDBCPool.GENERATED_KEYS).getInteger(0);
                      // Pass the player ID to the result handler
                       resultHandler.handle(Future.succeededFuture(playerID));
                    } else {
                        // Log and handle any errors encountered during query execution
                        logger.error("Failed to register player: {}", ar.cause().getMessage());
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }
    
    /**
     * @ingroup object_repository
     * @brief Creates a new session in the database.
     * 
     * This method creates a new session entry in the Sessions table with the given player ID, controller ID,
     * and login method.
     * 
     * @param playerId The unique ID of the player.
     * @param controllerId The unique ID of the controller.
     * @param loginMethod The method used for login.
     * @param resultHandler The result handler that returns success or failure.
     */
    public void createSession(int playerId, String controllerId, String loginMethod, Handler<AsyncResult<Void>> resultHandler) {
        // SQL query to create a new session in the Sessions table
        String sql = "INSERT INTO Sessions (player_id, controller_id, login_method) VALUES (?, ?, ?)";
    
        logger.info("Trying to insert: {}", playerId);
        logger.info("Trying to insert: {}", controllerId);
        logger.info("Trying to insert: {}", loginMethod);
    
        // Execute the prepared SQL query using a tuple containing playerId, controllerId, and loginMethod
        jdbcPool.preparedQuery(sql)
            .execute(Tuple.of(playerId, controllerId, loginMethod), ar -> {
                if (ar.succeeded()) {
                    // Successfully executed SQL
                    logger.info("Session created successfully for player ID: {} with controller ID: {}", playerId, controllerId);
                    resultHandler.handle(Future.succeededFuture());
                } else {
                    // Log and handle any errors encountered during query execution
                    logger.error("Failed to create session: {}", ar.cause().getMessage());
                    resultHandler.handle(Future.failedFuture(ar.cause()));
                }
            });
    }
    /**
     * @ingroup object_repository
     * @brief Records a frontend assignment for a player and controller.
     * 
     * This method inserts a new record into the FrontendAssignments table for the given player ID and controller ID.
     * 
     * @param playerId The unique ID of the player.
     * @param controllerId The unique ID of the controller.
     * @param resultHandler The result handler that returns success or failure.
     */
    public void recordFrontendAssignment(int playerId, String controllerId, Handler<AsyncResult<Void>> resultHandler) {
        // SQL query to record an assignment in the FrontendAssignments table
        String sql = "INSERT INTO FrontendAssignments (player_id, controller_id) VALUES (?, ?)";
        
        // Execute the prepared SQL query using a tuple containing playerId and controllerId
        jdbcPool.preparedQuery(sql)
            .execute(Tuple.of(playerId, controllerId), ar -> {
                if (ar.succeeded()) {
                    // Successfully executed SQL
                    logger.info("Frontend assignment recorded successfully for player ID: {} with controller ID: {}", playerId, controllerId);
                    resultHandler.handle(Future.succeededFuture());
                } else {
                    // Log and handle any errors encountered during query execution
                    logger.error("Failed to record frontend assignment: {}", ar.cause().getMessage());
                    resultHandler.handle(Future.failedFuture(ar.cause()));
                }
            });
    }
    /**
     * @ingroup object_repository
     * @brief Finds a player by RFID tag.
     * 
     * This method retrieves the player ID associated with the given RFID tag from the Players table.
     * 
     * @param rfidTag The RFID tag of the player.
     * @param resultHandler The result handler that returns the player ID or -1 if not found.
     */
    public void findPlayerByRfid(String rfidTag, Handler<AsyncResult<Integer>> resultHandler) {
        // SQL query to find a player by RFID tag in the Players table
        String sql = "SELECT player_id FROM Players WHERE rfid_tag = ?";
        
        // Execute the prepared SQL query using a tuple containing the rfidTag
        jdbcPool.preparedQuery(sql).execute(Tuple.of(rfidTag), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> rows = ar.result();
                if (rows.rowCount() > 0) {
                    // Player found, retrieve player ID from the first row
                    int playerId = rows.iterator().next().getInteger("player_id");
                    resultHandler.handle(Future.succeededFuture(playerId));
                } else {
                    // Player not found, return -1
                    resultHandler.handle(Future.succeededFuture(-1));
                }
            } else {
                // Log and handle any errors encountered during query execution
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
    /**
     * @ingroup object_repository
     * @brief Inserts a player with an RFID tag.
     * 
     * This method inserts a new player with the specified username and RFID tag into the Players table.
     * If the player already exists, it updates the existing record and returns the player ID.
     * 
     * @param username The name of the player.
     * @param rfidTag The RFID tag associated with the player.
     * @param resultHandler The result handler that returns the player ID or a failure.
     */
    public void insertPlayerWithRfid(String username,String rfidTag, Handler<AsyncResult<Integer>> resultHandler) {
        
        // SQL query to insert a player with an RFID tag into the Players table
        // Uses LAST_INSERT_ID() to get the player_id of the existing or new row.
        String sql = "INSERT INTO Players (user_name, rfid_tag) VALUES (?, ?) ON DUPLICATE KEY UPDATE player_id = LAST_INSERT_ID(player_id)";
        
        // Execute the prepared SQL query using a tuple containing defaultName and rfidTag
        jdbcPool.preparedQuery(sql).execute(Tuple.of(username, rfidTag), ar -> {
            if (ar.succeeded()) {
                // Retrieve the auto-generated player ID
                int playerID = ar.result().property(JDBCPool.GENERATED_KEYS).getInteger(0);
                resultHandler.handle(Future.succeededFuture(playerID));
            } else {
                // Log and handle any errors encountered during query execution
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
    /**
     * @ingroup object_repository
     * @brief Records an RFID assignment.
     * 
     * This method records an assignment of an RFID tag to a player and controller in the RfidAssignments table.
     * 
     * @param playerId The unique ID of the player.
     * @param controllerId The unique ID of the controller.
     * @param rfidTag The RFID tag associated with the assignment.
     * @param resultHandler The result handler that returns success or failure.
     */
    public void RfidAssignments(int playerId, String controllerId, String rfidTag, Handler<AsyncResult<Void>> resultHandler) {
        // SQL query to record an RFID assignment in the RfidAssignments table
        String sql = "INSERT INTO RfidAssignments (player_id, controller_id, rfid_tag) VALUES (?, ?, ?)";
        
        // Execute the prepared SQL query using a tuple containing playerId, controllerId, and rfidTag
        jdbcPool.preparedQuery(sql).execute(Tuple.of(playerId, controllerId, rfidTag), ar -> {
            if (ar.succeeded()) {
                // Successfully executed SQL
                resultHandler.handle(Future.succeededFuture());
            } else {
                // Log and handle any errors encountered during query execution
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
    /**
     * @ingroup object_repository
     * @brief Fetches display information by controller ID.
     * 
     * This method retrieves display information, including points, round, last update time, and the username of the player,
     * associated with a specific controller ID.
     * 
     * @param controllerId The unique ID of the controller.
     * @param resultHandler The result handler that returns the display information as a JsonObject or a failure.
     */
    public void fetchDisplayInfoByControllerId(String controllerId, Handler<AsyncResult<JsonObject>> resultHandler) {
        String query = "SELECT d.controller_id, d.points, d.round, d.last_update, p.username " +
                       "FROM DisplayInfo d " +
                       "JOIN Players p ON d.player_id = p.player_id " +
                       "WHERE d.controller_id = ?";
        jdbcPool.preparedQuery(query).execute(Tuple.of(controllerId), ar -> {
            if (ar.succeeded()) {
                RowSet<Row> resultSet = ar.result();
                if (resultSet.size() > 0) {
                    Row row = resultSet.iterator().next();
                    JsonObject jsonObject = new JsonObject()
                            .put("controller_id", row.getString("controller_id"))
                            .put("points", row.getInteger("points"))
                            .put("round", row.getInteger("round"))
                            .put("last_update", row.getLocalDateTime("last_update").toString())
                            .put("username", row.getString("username"));
                    resultHandler.handle(Future.succeededFuture(jsonObject));
                } else {
                    resultHandler.handle(Future.succeededFuture(new JsonObject().put("message", "No display info found")));
                }
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }
     /**
     * @ingroup object_repository
     * @brief Fetches the player ID associated with a specific controller ID.
     * 
     * This method retrieves the player ID of an active session associated with a given controller ID.
     * 
     * @param controllerId The unique ID of the controller.
     * @return A Future containing the player ID or null if no active session is found.
     */
    public Future<Integer> fetchPlayerIdByControllerId(String controllerId) {
        String query = "SELECT player_id FROM Sessions WHERE controller_id = ? AND end_time IS NULL";
        return jdbcPool.preparedQuery(query)
            .execute(Tuple.of(controllerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getInteger("player_id");
                } else {
                    logger.error("No active session found for controllerId: {}", controllerId);
                    return null; // Handle case where no active session is found
                }
            });
    }
    /**
     * @ingroup object_repository
     * @brief Fetches the current round for a player and controller.
     * 
     * This method retrieves the current round number for a player and controller from the Sessions table.
     * If no active session is found, it defaults to round 1.
     * 
     * @param controllerId The unique ID of the controller.
     * @param playerId The unique ID of the player.
     * @return A Future containing the current round number.
     */
    public Future<Integer> fetchCurrentRoundByControllerId(String controllerId, int playerId) {
        String selectQuery = "SELECT round FROM Sessions WHERE controller_id = ? AND player_id = ? AND end_time IS NULL";
    
        return jdbcPool.preparedQuery(selectQuery)
            .execute(Tuple.of(controllerId, playerId))
            .compose(rows -> {
                if (rows.size() > 0) {
                    // Return the round number if found
                    return Future.succeededFuture(rows.iterator().next().getInteger("round"));
                } else {
                    // If no session is found, default to round 1
                    return Future.succeededFuture(1);
                }
            })
            .onFailure(cause -> {
                // Log the error and return a failed Future
                logger.error("Failed to retrieve current round for controller {}: {}", controllerId, cause.getMessage());
            });
    }
    /**
     * @ingroup object_repository
     * @brief Updates the points for a given player and controller.
     * 
     * This method updates the points for the current round of a given player and controller. If no record is found,
     * a new one is inserted. It also ensures the username is updated.
     * 
     * @param controllerId The unique ID of the controller.
     * @param playerId The unique ID of the player.
     * @param round The current round number.
     * @return A Future indicating the result of the update operation.
     */
    public Future<Void> updatePoints(String controllerId, int playerId, int round) {
        return fetchUsernameByPlayerId(playerId)  // Fetch the username
            .compose(username -> {
                String updateQuery = "UPDATE DisplayInfo SET points = points + 1, username = ? WHERE controller_id = ? AND player_id = ? AND round = ?";
                return jdbcPool.preparedQuery(updateQuery)
                    .execute(Tuple.of(username, controllerId, playerId, round))
                    .onFailure(cause -> logger.error("Failed to update points for controller {}: {}", controllerId, cause.getMessage()))
                    .compose(result -> {
                        if (result.rowCount() == 0) {
                            // No existing record, insert a new one
                            return insertDisplayInfo(controllerId, playerId, round, username);
                        } else {
                            return Future.succeededFuture();
                        }
                    });
            });
    }
    /**
     * @ingroup object_repository
     * @brief Inserts new display information for a player and controller.
     * 
     * This method inserts a new record into the DisplayInfo table with initial points set to 1.
     * 
     * @param controllerId The unique ID of the controller.
     * @param playerId The unique ID of the player.
     * @param round The round number.
     * @param username The username of the player.
     * @return A Future indicating the result of the insert operation.
     */
    private Future<Void> insertDisplayInfo(String controllerId, int playerId, int round, String username) {
        String insertQuery = "INSERT INTO DisplayInfo (controller_id, player_id, round, points, username) VALUES (?, ?, 1, ?, ?)";
        return jdbcPool.preparedQuery(insertQuery)
            .execute(Tuple.of(controllerId, playerId, round, username))
            .onFailure(cause -> logger.error("Failed to insert DisplayInfo for controller {}: {}", controllerId, cause.getMessage()))
            .mapEmpty();
    }

    /**
     * @ingroup object_repository
     * @brief Fetches the username for a given player ID.
     * 
     * This method retrieves the username associated with a specific player ID from the Players table.
     * 
     * @param playerId The unique ID of the player.
     * @return A Future containing the username or null if not found.
     */
    private Future<String> fetchUsernameByPlayerId(int playerId) {
        String query = "SELECT user_name FROM Players WHERE player_id = ?";
        return jdbcPool.preparedQuery(query)
            .execute(Tuple.of(playerId))
            .map(rows -> {
                if (rows.size() > 0) {
                    return rows.iterator().next().getString("user_name");
                } else {
                    logger.error("No username found for playerId: {}", playerId);
                    return null;
                }
            });
    }
    /**
     * @ingroup object_repository
     * @brief Fetches display information for a specific controller and round.
     * 
     * This method retrieves the display information including username, points, and round from the DisplayInfo table
     * for a given controller ID and round number.
     * 
     * @param controllerId The unique ID of the controller.
     * @param round The round number.
     * @return A Future containing the display information as a JsonObject.
     */
    public Future<JsonObject> fetchDisplayInfo(String controllerId, int round) {
        String query = "SELECT username, points, round FROM DisplayInfo WHERE controller_id = ? AND round = ?";
        return jdbcPool.preparedQuery(query)
            .execute(Tuple.of(controllerId, round))
            .map(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    return new JsonObject()
                        .put("username", row.getString("username"))
                        .put("points", row.getInteger("points"))
                        .put("round", row.getInteger("round"));
                } else {
                    return new JsonObject(); // Return an empty JsonObject if no rows are found
                }
            })
            .onFailure(cause -> logger.error("Failed to fetch display info for controller {} and round {}: {}", controllerId, round, cause.getMessage()));
    }
     /**
     * @ingroup object_repository
     * @brief Creates a new round for a given controller.
     * 
     * This method updates the end time of the current round and inserts a new session for the next round,
     * as well as initializes new display information for that round.
     * 
     * @param controllerId The unique ID of the controller.
     * @return A Future indicating the result of the operation.
     */
    public Future<Void> createNewRound(String controllerId) {
        return jdbcPool.withTransaction(client -> {
            // First, get the current session info
            String getCurrentSessionQuery = "SELECT player_id, MAX(round) as current_round, login_method FROM Sessions WHERE controller_id = ? AND end_time IS NULL";
            
            return client.preparedQuery(getCurrentSessionQuery)
                .execute(Tuple.of(controllerId))
                .compose(rows -> {
                    if (rows.iterator().hasNext()) {
                        Row row = rows.iterator().next();
                        int playerId = row.getInteger("player_id");
                        int currentRound = row.getInteger("current_round");
                        String loginMethod = row.getString("login_method");
                        int newRound = currentRound + 1;
    
                        // Update the end time of the current round
                        String updateEndTimeQuery = "UPDATE Sessions SET end_time = NOW() WHERE controller_id = ? AND player_id = ? AND round = ?";
                        
                        return client.preparedQuery(updateEndTimeQuery)
                            .execute(Tuple.of(controllerId, playerId, currentRound))
                            .compose(v -> {
                                // Insert new session for the new round
                                String insertNewSessionQuery = "INSERT INTO Sessions (controller_id, player_id, round, start_time, login_method) VALUES (?, ?, ?, NOW(), ?)";
                                
                                return client.preparedQuery(insertNewSessionQuery)
                                    .execute(Tuple.of(controllerId, playerId, newRound, loginMethod));
                            })
                            .compose(v -> {
                                // Insert new DisplayInfo for the new round
                                String insertDisplayInfoQuery = "INSERT INTO DisplayInfo (controller_id, player_id, round, points, username) SELECT ?, ?, ?, 0, user_name FROM Players WHERE player_id = ?";
                                
                                return client.preparedQuery(insertDisplayInfoQuery)
                                    .execute(Tuple.of(controllerId, playerId, newRound, playerId));
                            });
                    } else {
                        return Future.failedFuture("No active session found for controller: " + controllerId);
                    }
                })
                .mapEmpty();
        });
    }
   
    /**
     * @ingroup object_repository
     * @brief Fetches the current round number for a given controller.
     * 
     * This method retrieves the highest round number from the Sessions table where the end time is NULL for a given controller ID.
     * 
     * @param controllerId The unique ID of the controller.
     * @return A Future containing the current round number or 0 if no active session is found.
     */
    public Future<Integer> fetchCurrentRound(String controllerId) {
        String query = "SELECT MAX(round) as current_round FROM Sessions WHERE controller_id = ? AND end_time IS NULL";
        return jdbcPool.preparedQuery(query)
            .execute(Tuple.of(controllerId))
            .map(rows -> {
                if (rows.size() > 0) {
                    Row row = rows.iterator().next();
                    return row.getInteger("current_round");
                } else {
                    return 0; // Default to 0 if no active session is found
                }
            });
    }

    /**
     * @ingroup object_repository
     * @brief Fetches the highest score for a specific player.
     * 
     * This method retrieves the maximum points achieved by a player from the DisplayInfo table.
     * 
     * @param playerId The unique ID of the player.
     * @return Future with the highest score or 0 if no scores are found.
     */
    public Future<Integer> fetchHighestScore(int playerId) {
        String query = "SELECT MAX(points) as highest_score FROM DisplayInfo WHERE player_id = ?";
        return jdbcPool.preparedQuery(query)
            .execute(Tuple.of(playerId))
            .map(rows -> {
                if (rows.iterator().hasNext()) {
                    return rows.iterator().next().getInteger("highest_score");
                } else {
                    return 0; // Return 0 if no scores are found
                }
            });
    }
    /**
     * @ingroup object_repository
     * @brief Updates the high score for a specific player.
     * 
     * This method updates the high_score field for a player in the Players table based on the highest score found in DisplayInfo.
     * 
     * @param playerId The unique ID of the player.
     * @return Future indicating the completion of the update.
     */
    public Future<Void> updateHighScore(int playerId) {
        return fetchHighestScore(playerId)
            .compose(highestScore -> {
                String updateQuery = "UPDATE Players SET high_score = ? WHERE player_id = ?";
                return jdbcPool.preparedQuery(updateQuery)
                    .execute(Tuple.of(highestScore, playerId))
                    .mapEmpty();
            });
    }
    /**
     * @ingroup object_repository
     * @brief Retrieves the winner of the current round.
     * 
     * This method finds the player with the highest points in the current round and returns their username and round number.
     * 
     * @param resultHandler Handler for the result or failure.
     */
    public void getRoundWinner(Handler<AsyncResult<JsonObject>> resultHandler) {
        String query = "SELECT round, username FROM DisplayInfo WHERE round = (SELECT MAX(round) FROM DisplayInfo) ORDER BY points DESC LIMIT 1";
        jdbcPool.preparedQuery(query).execute(ar -> {
          if (ar.succeeded()) {
            RowSet<Row> rows = ar.result();
            if (rows.size() > 0) {
              Row row = rows.iterator().next();
              JsonObject winner = new JsonObject()
                                  .put("round", row.getInteger("round"))
                                  .put("name", row.getString("username"));
              resultHandler.handle(Future.succeededFuture(winner));
            } else {
              logger.warn("No winner found for the current round.");
              resultHandler.handle(Future.succeededFuture(new JsonObject().put("message", "No winner for this round yet.")));
            }
          } else {
            logger.error("Failed to execute query to fetch round winner: {}", ar.cause().getMessage());
            resultHandler.handle(Future.failedFuture(ar.cause()));
          }
        });
    }


}  

  /** @} */ // End of object_repository group  

