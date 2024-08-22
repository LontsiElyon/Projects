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

public class ObjectService {

    private static final Logger logger = LoggerFactory.getLogger(ObjectService.class);

    private final ObjectRepository objectRepository;
    private static final List<String> COLORS = List.of("RED", "GREEN", "BLUE", "YELLOW");
    private final Random random = new Random();
    private final int minSequenceLength = 3; // Minimum sequence length
    private final int maxSequenceLength = 6; // Maximum sequence length

    public ObjectService(ObjectRepository objectRepository) {
        this.objectRepository = objectRepository;
    }

    public void registerController(String controllerId, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.insertController(controllerId, resultHandler);
    } 

    public void getAvailableControllers(Handler<AsyncResult<JsonArray>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.fetchControllers(resultHandler);
    }
    public void getConnectedControllers(Handler<AsyncResult<List<String>>> resultHandler) {
        objectRepository.getConnectedControllers(resultHandler);
    }

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

    public Future<JsonObject> fetchDisplayInfo(String controllerId) {
        return objectRepository.fetchDisplayInfo(controllerId);
    }

    

}

