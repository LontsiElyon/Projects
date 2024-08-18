package com.example.controller;

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import com.example.service.ObjectService;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mqtt.MqttClient;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ObjectController {

    private static final Logger logger = LoggerFactory.getLogger(ObjectController.class);
    private final ObjectService objectService;
    private final MqttClient mqttClient;
    private Vertx vertx;

    public ObjectController(Router router, ObjectService objectService, MqttClient mqttClient) {
        this.objectService = objectService;
        this.mqttClient = mqttClient;
        this.vertx = Vertx.vertx();
        // Register routes
        router.post("/api/create").handler(this::handleCreate);
        router.get("/api/objects").handler(this::handleRead);
        router.put("/api/update/:id").handler(this::handleUpdate);
        router.delete("/api/delete/:id").handler(this::handleDelete);

        router.get("/api/controllers").handler(this::handleFetchControllers);
        router.post("/api/login").handler(this::handleLogin);
        router.post("/api/generate-sequence").handler(this::handleGenerateSequence);          
       
    }

    private void handleCreate(RoutingContext ctx) {
        String message = ctx.getBodyAsString();
        logger.debug("Received request to create object with message: {}", message);

        // Call service to create object
        objectService.createObject(message, res -> {
            if (res.succeeded()) {
                // Publish message to MQTT
                mqttClient.publish("simon/game", Buffer.buffer(message), MqttQoS.AT_LEAST_ONCE, false, false, mqttAr -> {
                    if (mqttAr.succeeded()) {
                        ctx.response().setStatusCode(201).end("Object created, saved in DB and message sent via MQTT");
                        logger.debug("Published message to MQTT: {}", message);
                    } else {
                        ctx.response().setStatusCode(500).end("Failed to send message via MQTT");
                        logger.error("Failed to publish message to MQTT: {}", mqttAr.cause().getMessage());
                    }
                });
            } else {
                ctx.response().setStatusCode(500).end("Failed to insert data into database");
                logger.error("Failed to insert data into database: {}", res.cause().getMessage());
            }
        });
    }

    private void handleRead(RoutingContext ctx) {
        // Call service to read objects
        objectService.readObjects(res -> {
            if (res.succeeded()) {
                ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(res.result().encode());
                logger.debug("Fetched objects from database");
            } else {
                ctx.response().setStatusCode(500).end("Failed to fetch data from database");
                logger.error("Failed to fetch data from database: {}", res.cause().getMessage());
            }
        });
    }

    private void handleUpdate(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        String message = ctx.getBodyAsString();
        logger.debug("Received request to update object with id: {} and message: {}", id, message);

        // Call service to update object
        objectService.updateObject(id, message, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(200).end("Object updated successfully");
                logger.debug("Updated object with id: {}", id);
            } else {
                ctx.response().setStatusCode(500).end("Failed to update data in database");
                logger.error("Failed to update data in database: {}", res.cause().getMessage());
            }
        });
    }

    private void handleDelete(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        logger.debug("Received request to delete object with id: {}", id);

        // Call service to delete object
        objectService.deleteObject(id, res -> {
            if (res.succeeded()) {
                ctx.response().setStatusCode(200).end("Object deleted successfully");
                logger.debug("Deleted object with id: {}", id);
            } else {
                ctx.response().setStatusCode(500).end("Failed to delete data from database");
                logger.error("Failed to delete data from database: {}", res.cause().getMessage());
            }
        });
    }

    public void setupMqttHandlers() {

        logger.debug("Setting up MQTT Handlers");
        // Subscribe to the topic for controller connections with QoS level 1
        mqttClient.publishHandler(message -> {
            logger.debug("Received Message: {}", message);
            if ("controller/connect".equals(message.topicName())) {
                handleControllerConnect(message.payload());
            }
            //logger.debug("Received Message 2: {}", message);
            else if("controller/rfid".equals(message.topicName())){
               handleRfidScan(message.payload());
            }else if("controller/color_sequence".equals(message.topicName())){
                handleColorSequence(message.payload());
            }
        });
        mqttClient.subscribe("controller/connect", MqttQoS.EXACTLY_ONCE.value())
        .onSuccess(packetId -> {
            logger.info("Subscribed to controller/connect topic successfully with packet id {}", packetId);
        })
        .onFailure(cause -> {
            logger.error("Failed to subscribe to controller/connect topic: {}", cause.getMessage());
        });
        mqttClient.subscribe("controller/rfid", MqttQoS.EXACTLY_ONCE.value())
        .onSuccess(packetId -> {
            logger.info("Subscribed to controller/rfid topic successfully with packet id {}", packetId);
        })
        .onFailure(cause -> {
            logger.error("Failed to subscribe to controller/rfid topic: {}", cause.getMessage());
        });
        mqttClient.subscribe("controller/color_sequence", MqttQoS.EXACTLY_ONCE.value())
        .onSuccess(packetId -> {
            logger.info("Subscribed to controller/color_sequence topic successfully with packet id {}", packetId);
        })
        .onFailure(cause -> {
            logger.error("Failed to subscribe to controller/color_sequence topic: {}", cause.getMessage());
        });
    }  


    // Handle incoming connection messages from controllers
    private void handleControllerConnect(Buffer payload) {
        String controllerId = payload.toString();
        logger.debug("Received connection from controller: {}", controllerId);

        // Register the controller in the service
        objectService.registerController(controllerId, res -> {
            if (res.succeeded()) {
                logger.info("Controller registered: {}", controllerId);
                // Optionally publish an acknowledgment back to the controller
                mqttClient.publish("controller/ack", Buffer.buffer("Connected: " + controllerId), MqttQoS.AT_LEAST_ONCE, false, false);
            } else {
                logger.error("Failed to register controller: {}", res.cause().getMessage());
            }
        });
    }

    private void handleFetchControllers(RoutingContext ctx) {
        // Call the service to get the available controllers
        objectService.getAvailableControllers(ar -> {
            if (ar.succeeded()) {
                // If the operation is successful, set the response header and body
                ctx.response()
                        .putHeader("content-type", "application/json") // Set the response content type to JSON
                        .end(ar.result().encode()); // Encode the result as a JSON string and send the response
                logger.debug("Fetched available controllers"); // Log success
            } else {
                // If the operation failed, set the response status code to 500 and send an error message
                ctx.response().setStatusCode(500).end("Failed to fetch controllers from database");
                logger.error("Failed to fetch controllers from database: {}", ar.cause().getMessage()); // Log the error
            }
        });
    }
    private void handleLogin(RoutingContext ctx) {
        // Extract username and controllerId from the HTTP request parameters
        String username = ctx.request().getParam("username");
        String controllerId = ctx.request().getParam("controller");
    
        // Check if username and controllerId are provided, if not return a 400 Bad Request
        if (username == null || controllerId == null) {
            ctx.response().setStatusCode(400).end("Username and Controller ID are required");
            return;
        }
    
        // Log the received login request for debugging purposes
        logger.debug("Received login request for username: {} and controller: {}", username, controllerId);
    
        // Call the service method to register the player and create a session
        objectService.registerPlayerAndCreateSession(username, controllerId, res -> {
            if (res.succeeded()) {
                // On success, send a 200 OK response with a success message
                ctx.response().setStatusCode(200).end("Player registered and session created successfully");
                logger.info("Player {} logged in with controller {}", username, controllerId);
            } else {
                // On failure, send a 500 Internal Server Error response with an error message
                ctx.response().setStatusCode(500).end("Failed to register player or create session");
                logger.error("Failed to register player or create session: {}", res.cause().getMessage());
            }
        });
    }

    // Handle incoming RFID scan messages
private void handleRfidScan(Buffer payload) {
    // Convert the payload to a string to get the RFID tag
    String payloadStr = payload.toString();
    JsonObject json = new JsonObject(payloadStr);
    String controllerId = json.getString("controllerId");
    String username = json.getString("username");

    
    // Log the received RFID scan for debugging purposes
    logger.debug("Received RFID scan with tag: {}", payloadStr);

    // Call the service method to register the player using the RFID tag
    objectService.registerPlayerWithRfid(payloadStr, res -> {
        if (res.succeeded()) {
            // On success, log that the RFID scan was processed successfully
            logger.info("RFID scan processed successfully for tag: {}", payloadStr);

            // Construct the message to notify the frontend
            JsonObject message = new JsonObject()
                .put("action", "controller_assigned")
                .put("message", "Controller " + controllerId + " connected to " + username); // Message format

            // Publish the message to the "frontend/notifications" topic
            mqttClient.publish("frontend/notifications", Buffer.buffer(message.encode()), MqttQoS.AT_LEAST_ONCE, false, false);

        } else {
            /*// If the player is not found, notify the frontend to prompt registration
            if ("Player not found".equals(res.cause().getMessage())) {
                notifyFrontendForRegistration(payloadStr);
            }*/
            // On failure, log the error with the failure cause
            logger.error("Failed to process RFID scan: {}", res.cause().getMessage());
        }
    });
}


private static final int TOTAL_SEQUENCES = 50;

private void handleGenerateSequence(RoutingContext routingContext) {
    // Retrieve the list of connected controllers
    objectService.getConnectedControllers(connectedControllersResult -> {
        if (connectedControllersResult.succeeded()) {
            List<String> connectedControllers = connectedControllersResult.result();

            if (connectedControllers.isEmpty()) {
                routingContext.response()
                    .setStatusCode(200)
                    .end(new JsonObject().put("message", "No connected controllers associated with users").encode());
                return;
            }

            // Start the sequence generation and sending process
            sendSequencesToControllers(connectedControllers, routingContext);

        } else {
            routingContext.response()
                .setStatusCode(500)
                .end(new JsonObject().put("error", "Failed to retrieve connected controllers").encode());
            logger.error("Failed to retrieve connected controllers", connectedControllersResult.cause());
        }
    });
}

private void sendSequencesToControllers(List<String> connectedControllers, RoutingContext routingContext) {
    AtomicInteger sequenceCount = new AtomicInteger(0);

    vertx.setPeriodic(100000, timerId -> {
        if (sequenceCount.get() >= TOTAL_SEQUENCES) {
            vertx.cancelTimer(timerId);
            logger.info("Completed sending all sequences.");
            return;
        }

        JsonArray colorSequence = objectService.generateColorSequence();
        sendColorSequenceToControllers(connectedControllers, colorSequence);

        // Handle response and validation for each sequence
        handleSequenceResponse(connectedControllers, colorSequence, result -> {
            if (result) {
                sequenceCount.incrementAndGet();
            } else {
                logger.info("Controller failed to match the sequence, stopping further sequence sending.");
                vertx.cancelTimer(timerId);
                notifyControllerOfLoss(connectedControllers);
            }
        });
    });
}

private void sendColorSequenceToControllers(List<String> connectedControllers, JsonArray colorSequence) {
    String message = colorSequence.encode();
    String topic = "neopixel/display";

    mqttClient.publish(topic,
        Buffer.buffer(message),
        MqttQoS.AT_LEAST_ONCE,
        false,
        false,
        ar -> {
            if (ar.succeeded()) {
                logger.info("Color sequence sent to all controllers: {}", message);
            } else {
                logger.error("Failed to send color sequence to controllers", ar.cause());
            }
        });
}

private void handleSequenceResponse(List<String> connectedControllers, JsonArray sentSequence, Handler<Boolean> resultHandler) {
    vertx.setTimer(10000, timerId -> {
        JsonArray receivedSequence = simulateReceivingSequence(sentSequence);
        handleColorSequence(Buffer.buffer(receivedSequence.encode()))  // Call directly
            .onSuccess(isMatch -> {
                if (isMatch) {
                    resultHandler.handle(true);
                } else {
                    resultHandler.handle(false);
                }
            })
            .onFailure(cause -> {
                logger.error("Failed to handle sequence response: {}", cause.getMessage());
                resultHandler.handle(false);
            });
    });
}

// This is a simulation method for the received sequence
private JsonArray simulateReceivingSequence(JsonArray sentSequence) {
    // In this simulation, we simply return the sent sequence as if it was correctly received.
    // You can modify this method to simulate different scenarios (e.g., returning an incorrect sequence).
    return sentSequence;
}

private void notifyControllerOfLoss(List<String> connectedControllers) {
    String lossMessage = new JsonObject().put("message", "You lost! The sequence did not match.").encode();
    String topic = "neopixel/loss";

    mqttClient.publish(topic,
        Buffer.buffer(lossMessage),
        MqttQoS.AT_LEAST_ONCE,
        false,
        false,
        ar -> {
            if (ar.succeeded()) {
                logger.info("Loss notification sent to controllers.");
            } else {
                logger.error("Failed to send loss notification to controllers", ar.cause());
            }
        });
}

public Future<Boolean> handleColorSequence(Buffer payload) {
    Promise<Boolean> promise = Promise.promise();

    JsonObject payloadJson = payload.toJsonObject();
    String controllerId = payloadJson.getString("controllerId");
    JsonArray receivedSequence = payloadJson.getJsonArray("sequence");

    if (controllerId == null || receivedSequence == null) {
        logger.error("Invalid payload: controllerId or sequence is missing");
        promise.complete(false);
        return promise.future();
    }

    logger.debug("Received sequence for controller {}: {}", controllerId, receivedSequence.encode());

    objectService.compareSequenceAndAwardPoints(controllerId, receivedSequence)
        .onSuccess(isMatch -> {
            if (isMatch) {
                logger.info("Sequence match! Points updated for controller: {}", controllerId);
                sendDisplayInfoToController(controllerId);
                promise.complete(true);
            } else {
                logger.info("Sequence did not match for controller: {}", controllerId);
                notifyControllerOfLoss(Collections.singletonList(controllerId));
                promise.complete(false);
            }
        })
        .onFailure(cause -> {
            logger.error("Failed to compare sequence or update points: {}", cause.getMessage());
            promise.fail(cause);
        });

    return promise.future();
}

public void sendDisplayInfoToController(String controllerId) {
    objectService.fetchDisplayInfo(controllerId) // Using objectService instance
        .onSuccess(displayInfo -> {
            if (displayInfo != null && !displayInfo.isEmpty()) {
                String message = displayInfo.encode();
                String topic = "oled/display/" + controllerId;

                mqttClient.publish(topic,
                    Buffer.buffer(message),
                    MqttQoS.AT_LEAST_ONCE,
                    false,
                    false,
                    ar -> {
                        if (ar.succeeded()) {
                            logger.info("Display info sent to controller {}: {}", controllerId, message);
                        } else {
                            logger.error("Failed to send display info to controller {}: {}", controllerId, ar.cause());
                        }
                    });
            } else {
                logger.error("No display info found for controller {}", controllerId);
            }
        })
        .onFailure(cause -> logger.error("Failed to fetch display info: {}", cause.getMessage()));
}


}


