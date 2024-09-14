
/**
 * 
 * @file ObjectService.java
 * @brief The ObjectService class manages game-related logic, including operations involving players, controllers, sessions, and points.
 * 
 * The ObjectService class manages game-related logic, including operations involving players, controllers, sessions, and points.
 * It interacts with the repository layer to perform CRUD operations, manage game sessions, update player scores, and handle game rounds.
 * This class includes methods for creating new rounds, updating player points, fetching current rounds, and determining round winners.
 
 * @defgroup ObjectService ObjectService
 * This module handles the logic related to players, controllers, sessions, and points.
 * It communicates with the repository layer to perform CRUD operations and manage game sequences.
 * @{
 */

package com.example.service;

import java.util.List;
import java.util.Random;

import com.example.repository.ObjectRepository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ingroup ObjectService
 * The ObjectService class handles core business logic for player registration, controller management,
 * game sessions, and color sequence generation for a gaming application.
 */
public class ObjectService {

    private static final Logger logger = LoggerFactory.getLogger(ObjectService.class);

    private final ObjectRepository objectRepository;
    private static final List<String> COLORS = List.of("RED", "GREEN", "BLUE", "YELLOW");
    private final Random random = new Random();
    private final int minSequenceLength = 1; // Minimum sequence length
    private final int maxSequenceLength = 4; // Maximum sequence length

    /**
     * Constructor for ObjectService.
     * @param objectRepository the repository to handle database operations.
     */
    public ObjectService(ObjectRepository objectRepository) {
        this.objectRepository = objectRepository;
    }
    /**
     * Registers a controller.
     * @param controllerId the ID of the controller to register.
     * @param resultHandler handles the result of the operation.
     */
    public void registerController(String controllerId, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.insertController(controllerId, resultHandler);
    } 
    
    /**
     * Fetches available controllers from the repository.
     * @param resultHandler handles the result containing a JSON array of controllers.
     */
    public void getAvailableControllers(Handler<AsyncResult<JsonArray>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.fetchControllers(resultHandler);
    }
    /**
     * Fetches connected controllers.
     * @param resultHandler handles the result containing a list of connected controllers.
     */
    public void getConnectedControllers(Handler<AsyncResult<List<String>>> resultHandler) {
        objectRepository.getConnectedControllers(resultHandler);
    }
    
    /**
     * Registers a player and creates a session for them.
     * @param username the username of the player.
     * @param controllerId the controller ID associated with the player.
     * @param resultHandler handles the result of the registration and session creation.
     */
    public void registerPlayerAndCreateSession(String username, String controllerId, Handler<AsyncResult<Void>> resultHandler) {
         // Check if the controller is already in use
         logger.info("Check if the controller is already in use: {}",controllerId );
         checkControllerUsage(controllerId, usageCheckResult -> {
             if (usageCheckResult.succeeded() && usageCheckResult.result()) {
                 // Controller is already in use, handle the error
                 resultHandler.handle(Future.failedFuture("Controller is already in use by another player"));
                 logger.info("Controller is already in use by another player: {}", controllerId);
             } else {

        // Call the insertPlayer method to add the player to the database
        objectRepository.insertPlayer(username, insertPlayerResult -> {
            if (insertPlayerResult.succeeded()) {
                // Retrieve the playerId from the result of the insertPlayer operation
                int playerId = insertPlayerResult.result();
    
                // Call the createSession method to create a session for the player
                objectRepository.createSession(playerId, controllerId, "Frontend", createSessionResult -> {
                    if (createSessionResult.succeeded()) {
                        // Call the recordFrontendAssignment method to record the player's assignment to the frontend
                        objectRepository.recordFrontendAssignment(playerId, controllerId, resultHandler);
                    } else {
                        // Handle the failure in creating a session by passing the cause to the result handler
                        resultHandler.handle(Future.failedFuture(createSessionResult.cause()));
                    }
                });
            } else {
                // Handle the failure in inserting a player by passing the cause to the result handler
                resultHandler.handle(Future.failedFuture(insertPlayerResult.cause()));
            }
        });
            }
        });
    }
    /**
     * Registers a player using an RFID tag and creates a session.
     * @param payload the payload containing player information including RFID tag.
     * @param resultHandler handles the result of the operation.
     */
    public void registerPlayerWithRfid(String payload, Handler<AsyncResult<Void>> resultHandler) {
        JsonObject json = new JsonObject(payload);
        String rfidTag = json.getString("rfidTag");
        String controllerId = json.getString("controllerId");
        String username = json.getString("username");
    
        // Check if the controller is already in use
        logger.info("Check if the controller is already in use: {}",controllerId );
        checkControllerUsage(controllerId, usageCheckResult -> {
            if (usageCheckResult.succeeded() && usageCheckResult.result()) {
                // Controller is already in use, handle the error
                resultHandler.handle(Future.failedFuture("Controller is already in use by another player"));
                logger.info("Controller is already in use by another player: {}", controllerId);
            } else {
                // Proceed with the existing logic to register the player with RFID
                objectRepository.findPlayerByRfid(rfidTag, findResult -> {
                    if (findResult.succeeded()) {
                        int playerId = findResult.result();
                        if (playerId == -1) {
                            // Player not found, create a new player with the RFID tag
                            objectRepository.insertPlayerWithRfid(username, rfidTag, insertResult -> {
                                if (insertResult.succeeded()) {
                                    int newPlayerId = insertResult.result();
                                    createRfidSession(newPlayerId, controllerId, rfidTag, resultHandler);
                                } else {
                                    resultHandler.handle(Future.failedFuture(insertResult.cause()));
                                }
                            });
                        } else {
                            // Player found, create a session for the existing player
                            createRfidSession(playerId, controllerId, rfidTag, resultHandler);
                        }
                    } else {
                        resultHandler.handle(Future.failedFuture(findResult.cause()));
                    }
                });
            }
        });
    }

    
    /**
     * Checks if a controller is in use.
     * @param controllerId the ID of the controller to check.
     * @param resultHandler handles the result of the usage check.
     */
    private void checkControllerUsage(String controllerId, Handler<AsyncResult<Boolean>> resultHandler) {
        // Check if the controller is in use in the RFIDAssignments table
        objectRepository.isControllerInUse(controllerId, rfidCheckResult -> {
            if (rfidCheckResult.succeeded() && rfidCheckResult.result()) {
                // Controller is in use in RFID assignments
                resultHandler.handle(Future.succeededFuture(true));
            } else if (rfidCheckResult.failed()) {
                // Error in checking RFID assignments
                resultHandler.handle(Future.failedFuture(rfidCheckResult.cause()));
            } else {
                // Controller is not in use in RFID assignments, check FrontendAssignments
        objectRepository.isControllerInUseInFrontend(controllerId, frontendCheckResult -> {
                    if (frontendCheckResult.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(frontendCheckResult.result()));
                    } else {
                        // Error in checking frontend assignments
                        resultHandler.handle(Future.failedFuture(frontendCheckResult.cause()));
                    }
                });
            }
        });
    }

    /**
     * Creates a session and assigns an RFID tag to the player.
     * @param playerId the player's ID.
     * @param controllerId the controller ID to associate.
     * @param rfidTag the RFID tag to assign.
     * @param resultHandler handles the result of session creation.
     */
    private void createRfidSession(int playerId, String controllerId, String rfidTag, Handler<AsyncResult<Void>> resultHandler) {
        // Create a new session and record RFID assignment
        objectRepository.createSession(playerId, controllerId, "RFID", createSessionResult -> {
            if (createSessionResult.succeeded()) {
                objectRepository.RfidAssignments(playerId, controllerId, rfidTag, resultHandler);
            } else {
                resultHandler.handle(Future.failedFuture(createSessionResult.cause()));
            }
        });
    }


    private JsonArray storedSequence;
    
    /**
     * Generates a random color sequence.
     * @return the generated color sequence.
     */
    public JsonArray generateColorSequence() {
        // Generate a random sequence length between min and max
        int sequenceLength = random.nextInt(maxSequenceLength - minSequenceLength + 1) + minSequenceLength;

        JsonArray colorSequence = new JsonArray();
        for (int i = 0; i < sequenceLength; i++) {
            String color = COLORS.get(random.nextInt(COLORS.size()));
            colorSequence.add(color);
        }

        //Store the generated sequence
        this.storedSequence = colorSequence;
        return colorSequence;
    }
    
    /**
     * Compares the received color sequence with the stored sequence and awards points if they match.
     * @param controllerId the ID of the controller.
     * @param receivedSequence the received color sequence.
     * @return a Future indicating success or failure.
     */
    public Future<Boolean> compareSequenceAndAwardPoints(String controllerId, JsonArray receivedSequence) {
        if (controllerId == null || receivedSequence == null) {
            logger.error("Invalid input: controllerId or receivedSequence is null");
            return Future.succeededFuture(false);
        }
    
        if (storedSequence == null) {
            logger.error("No stored sequence available for comparison");
            return Future.succeededFuture(false);
        }
    
        logger.debug("Stored sequence: {}", storedSequence.encode());
        logger.debug("Received sequence: {}", receivedSequence.encode());
    
        return objectRepository.fetchPlayerIdByControllerId(controllerId)
            .compose(playerId -> {
                if (playerId == null) {
                    logger.error("No player found for controllerId: {}", controllerId);
                    return Future.succeededFuture(false);
                }
    
                return objectRepository.fetchCurrentRoundByControllerId(controllerId,playerId)
                    .compose(round -> {
                        if (round == null) {
                            logger.error("Failed to fetch the current round for controllerId: {}", controllerId);
                            return Future.succeededFuture(false);
                        }
    
                        boolean sequencesMatch = compareSequences(storedSequence, receivedSequence);
    
                        if (sequencesMatch) {
                            return objectRepository.updatePoints(controllerId, playerId, round)
                                .map(v -> true)
                                .recover(cause -> {
                                    logger.error("Failed to update points for controller {}: {}", controllerId, cause.getMessage());
                                    return Future.succeededFuture(false);
                                });
                        } else {
                            return Future.succeededFuture(false);
                        }
                    });
            });
    }

    /**
     * Compares two sequences of colors.
     * @param generated the generated color sequence.
     * @param received the received color sequence.
     * @return true if the sequences match, false otherwise.
     */
    private boolean compareSequences(JsonArray generated, JsonArray received) {
        if (generated == null || received == null) {
            logger.error("Either generated or received sequence is null");
            return false;
        }
    
        if (generated.size() != received.size()) {
            logger.debug("Sequence size mismatch. Generated: {}, Received: {}", generated.size(), received.size());
            return false;
        }
    
        for (int i = 0; i < generated.size(); i++) {
            String genColor = generated.getString(i);
            String recColor = received.getString(i);
            logger.debug("Comparing color at index {}: Generated '{}' vs Received '{}'", i, genColor, recColor);
            if (!genColor.equalsIgnoreCase(recColor)) {
                logger.debug("Mismatch at index {}", i);
                return false;
            }
        }
    
        return true;
    }
    
    /**
     * Fetches display information for a controller and round.
     * @param controllerId the ID of the controller.
     * @param currentRound the current round.
     * @return a Future containing display information.
     */
    public Future<JsonObject> fetchDisplayInfo(String controllerId,int currentRound) {
        return objectRepository.fetchDisplayInfo(controllerId,currentRound);
    }
    
    /**
     * Creates a new round for a controller.
     * @param controllerId the ID of the controller.
     * @return a Future indicating success or failure.
     */
    public Future<Void> createNewRound(String controllerId) {
        return objectRepository.createNewRound(controllerId);
    }
    
    /**
     * Fetches the current round for a controller.
     * @param controllerId the ID of the controller.
     * @return a Future containing the current round number.
     */
    public Future<Integer> fetchCurrentRound(String controllerId) {
        return objectRepository.fetchCurrentRound(controllerId);
    }
    
    /**
     * Retrieves the winner of the current round.
     * @param resultHandler handles the result containing the round winner information.
     */
    public void getRoundWinner(Handler<AsyncResult<JsonObject>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.getRoundWinner(resultHandler);
    }
    

    /**
     * Updates the high score for a player based on their controller ID.
     * @param controllerId the controller ID.
     * @return a Future indicating success or failure.
     */
    public Future<Void> updatePlayerHighScore(String controllerId) {
        return objectRepository.fetchPlayerIdByControllerId(controllerId)
            .compose(playerId -> {
                if (playerId == null) {
                    return Future.failedFuture("No player found for controller: " + controllerId);
                }
                return objectRepository.updateHighScore(playerId);
            })
            .onSuccess(v -> logger.info("High score updated for player with controller: {}", controllerId))
            .onFailure(cause -> logger.error("Failed to update high score for player with controller: {}", controllerId, cause));
    }
    
}

/** @} */
