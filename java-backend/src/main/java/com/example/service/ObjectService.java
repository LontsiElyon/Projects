package com.example.service;

import java.util.List;
import java.util.Random;

import com.example.repository.ObjectRepository;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ObjectService {

    private final ObjectRepository objectRepository;
    private static final List<String> COLORS = List.of("RED", "GREEN", "BLUE", "YELLOW");
    private final Random random = new Random();
    private final int minSequenceLength = 3; // Minimum sequence length
    private final int maxSequenceLength = 6; // Maximum sequence length

    public ObjectService(ObjectRepository objectRepository) {
        this.objectRepository = objectRepository;
    }

    public void createObject(String message, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate object creation to repository
        objectRepository.insertObject(message, resultHandler);
    }

    public void readObjects(Handler<AsyncResult<JsonArray>> resultHandler) {
        // Delegate reading objects to repository
        objectRepository.fetchObjects(ar -> {
            if (ar.succeeded()) {
                RowSet<Row> resultSet = ar.result();
                JsonArray jsonArray = new JsonArray();
                resultSet.forEach(row -> {
                    JsonObject jsonObject = new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("message", row.getString("message"))
                            .put("created_at", row.getTemporal("created_at").toString());
                    jsonArray.add(jsonObject);
                });
                resultHandler.handle(Future.succeededFuture(jsonArray));
            } else {
                resultHandler.handle(Future.failedFuture(ar.cause()));
            }
        });
    }

    public void updateObject(int id, String message, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate object update to repository
        objectRepository.updateObject(id, message, resultHandler);
    }

    public void deleteObject(int id, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate object deletion to repository
        objectRepository.deleteObject(id, resultHandler);
    }

    public void registerController(String controllerId, Handler<AsyncResult<Void>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.insertController(controllerId, resultHandler);
    } 

    public void getAvailableControllers(Handler<AsyncResult<JsonArray>> resultHandler) {
        // Delegate the operation to the repository
        objectRepository.fetchControllers(resultHandler);
    }  

    public void registerPlayerAndCreateSession(String username, String controllerId, Handler<AsyncResult<Void>> resultHandler) {
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

    public void registerPlayerWithRfid(String payload, Handler<AsyncResult<Void>> resultHandler) {

        // Parse the JSON payload
    JsonObject json = new JsonObject(payload);
    String rfidTag = json.getString("rfidTag");
    String controllerId = json.getString("controllerId");

         // Insert or update the player with the RFID tag
    objectRepository.findPlayerByRfid(rfidTag, findResult -> {
        if (findResult.succeeded()) {
            int playerId = findResult.result();
            if (playerId == -1) {
                // Player not found, create a new player with the RFID tag
                objectRepository.insertPlayerWithRfid(rfidTag, insertResult -> {
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

    public void fetchDisplayInfoByControllerId(String controllerId, Handler<AsyncResult<JsonObject>> resultHandler) {
        objectRepository.fetchDisplayInfoByControllerId(controllerId, resultHandler);
    }

    public void createDisplayInfo(String controllerId, int playerId, int points, int round, String username, Handler<AsyncResult<Void>> resultHandler) {
        objectRepository.insertDisplayInfo(controllerId, playerId, points, round, username, resultHandler);
    }
    
}
