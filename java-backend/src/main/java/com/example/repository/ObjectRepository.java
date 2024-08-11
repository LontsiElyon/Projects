package com.example.repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PropertyKind;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.sql.JDBCType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectRepository {

    private static final Logger logger = LoggerFactory.getLogger(ObjectRepository.class);

    private final Pool jdbcPool;

    public ObjectRepository(Pool jdbcPool) {
        this.jdbcPool = jdbcPool;
    }

    public void insertObject(String message, Handler<AsyncResult<Void>> resultHandler) {
        // Execute insert query without preparing metadata
        jdbcPool.query("INSERT INTO objects (message) VALUES ('" + message + "')")
                .execute(ar -> {
                    if (ar.succeeded()) {
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }

    public void fetchObjects(Handler<AsyncResult<RowSet<io.vertx.sqlclient.Row>>> resultHandler) {
        // Execute select query
        jdbcPool.query("SELECT * FROM objects")
                .execute(resultHandler);
    }

    public void updateObject(int id, String message, Handler<AsyncResult<Void>> resultHandler) {
        // Execute update query without preparing metadata
        jdbcPool.query("UPDATE objects SET message = '" + message + "' WHERE id = " + id)
                .execute(ar -> {
                    if (ar.succeeded()) {
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }

    public void deleteObject(int id, Handler<AsyncResult<Void>> resultHandler) {
        // Execute delete query without preparing metadata
        jdbcPool.query("DELETE FROM objects WHERE id = " + id)
                .execute(ar -> {
                    if (ar.succeeded()) {
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }


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

     public void insertPlayer(String username, Handler<AsyncResult<Integer>> resultHandler) {
        // SQL query to insert a player into the Players table with username.
       // If the username already exists, update the existing record's username.
      // Uses LAST_INSERT_ID() to get the player_id of the existing or new row.
        String sql = "INSERT INTO Players (user_name) VALUES (?) ON DUPLICATE KEY UPDATE user_name = VALUES(user_name), player_id = LAST_INSERT_ID(player_id)";

        // Execute the prepared SQL query using a tuple containing the username
        jdbcPool.preparedQuery(sql)
                .execute(Tuple.of(username), ar -> {
                    if (ar.succeeded()) {
                        /*if (ar.result() == null) logger.info("Result is Null");
                        logger.info("Result was {} long", ar.result().size());
                        logger.info("Result was {}", ar.result().value());
                        logger.info("Result was {} namws", ar.result().columnsNames());
                        logger.info("Result was {} long", ar.result().rowCount());
                        ar.result().forEach(r -> logger.info(r.toString()));*/
                       // int playerIdLong = ar.result().property(PropertyKind.create("player_id", int.class)); // Retrieve the auto-incremented ID as Long
                        //int playerId = playerIdLong != null ? playerIdLong.intValue() : -1; // Safely convert to int
                        //logger.info("Player registered successfully with ID: {}", playerIdLong);

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

    public void insertDisplayInfo(String controllerId, int playerId, int points, int round, String username, Handler<AsyncResult<Void>> resultHandler) {
        String query = "INSERT INTO DisplayInfo (controller_id, player_id, points, round, username) VALUES (?, ?, ?, ?, ?)";
        jdbcPool.preparedQuery(query).execute(Tuple.of(controllerId, playerId, points, round, username), ar -> {
            if (ar.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }


}  

    

