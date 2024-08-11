package com.example.controller;

import com.example.service.ObjectService;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mqtt.MqttClient;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectController {

    private static final Logger logger = LoggerFactory.getLogger(ObjectController.class);
    private final ObjectService objectService;
    private final MqttClient mqttClient;

    public ObjectController(Router router, ObjectService objectService, MqttClient mqttClient) {
        this.objectService = objectService;
        this.mqttClient = mqttClient;
        // Register routes
        router.post("/api/create").handler(this::handleCreate);
        router.get("/api/objects").handler(this::handleRead);
        router.put("/api/update/:id").handler(this::handleUpdate);
        router.delete("/api/delete/:id").handler(this::handleDelete);

        router.get("/api/controllers").handler(this::handleFetchControllers);
        router.post("/api/login").handler(this::handleLogin);
        router.post("/api/display").handler(this::handleCreateDisplayInfo);
        router.get("/api/display/:controllerId").handler(this::handleFetchDisplayInfoByControllerId);
        router.post("/generate-sequence").handler(this::handleGenerateSequence);
        //router.post("/api/register").handler(this::handleRegisterUserWithRfid);
       
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
    
    // Log the received RFID scan for debugging purposes
    logger.debug("Received RFID scan with tag: {}", payloadStr);

    // Call the service method to register the player using the RFID tag
    objectService.registerPlayerWithRfid(payloadStr, res -> {
        if (res.succeeded()) {
            // On success, log that the RFID scan was processed successfully
            logger.info("RFID scan processed successfully for tag: {}", payloadStr);

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

// Method to notify the frontend for registration
/*private void notifyFrontendForRegistration(String payloadStr) {
    JsonObject json = new JsonObject(payloadStr);
    String rfidTag = json.getString("rfidTag");

    // Publish a message to the frontend via MQTT
    JsonObject message = new JsonObject()
            .put("action", "promptRegistration")
            .put("rfidTag", rfidTag);

    // Publish the message to the predefined MQTT topic
    mqttClient.publish("frontend/registration", 
            Buffer.buffer(message.encode()), 
            MqttQoS.AT_LEAST_ONCE, 
            false, 
            false);
}*/

// Handler method for user registration
/*private void handleRegisterUserWithRfid(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    String rfidTag = body.getString("rfidTag");
    String username = body.getString("username");

    if (rfidTag == null || username == null) {
        ctx.response().setStatusCode(400).end("RFID tag and username are required");
        return;
    }

    // Register the player with the provided username and RFID tag
    objectService.fetchDisplayInfoByControllerId(username, rfidTag, res -> {
        if (res.succeeded()) {
            ctx.response().setStatusCode(200).end(new JsonObject().put("success", true).encode());
        } else {
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("error", res.cause().getMessage()).encode());
        }
    });
}*/

private void handleCreateDisplayInfo(RoutingContext ctx) {
    JsonObject requestBody = ctx.getBodyAsJson();
    String controllerId = requestBody.getString("controller_id");
    int playerId = requestBody.getInteger("player_id");
    int points = requestBody.getInteger("points");
    int round = requestBody.getInteger("round");
    String username = requestBody.getString("username");

    logger.debug("Received request to create display info with controller_id: {}, player_id: {}, points: {}, round: {}, username: {}", controllerId, playerId, points, round, username);

    objectService.createDisplayInfo(controllerId, playerId, points, round, username, res -> {
        if (res.succeeded()) {
            ctx.response().setStatusCode(201).end("Display info created successfully");
            logger.debug("Created display info with controller_id: {}", controllerId);
        } else {
            ctx.response().setStatusCode(500).end("Failed to create display info");
            logger.error("Failed to create display info: {}", res.cause().getMessage());
        }
    });
}

private void handleFetchDisplayInfoByControllerId(RoutingContext ctx) {
    String controllerId = ctx.pathParam("controllerId");
    logger.debug("Received request to fetch display info for controller_id: {}", controllerId);

    objectService.fetchDisplayInfoByControllerId(controllerId, res -> {
        if (res.succeeded()) {
            ctx.response().putHeader("content-type", "application/json")
                    .end(res.result().encode());
            logger.debug("Fetched display info for controller_id: {}", controllerId);
        } else {
            ctx.response().setStatusCode(500).end("Failed to fetch display info");
            logger.error("Failed to fetch display info: {}", res.cause().getMessage());
        }
    });
}

private void handleGenerateSequence(RoutingContext routingContext) {
    // Generate a new color sequence
    JsonArray colorSequence = objectService.generateColorSequence();

    // Send the color sequence to the MQTT client
    sendColorSequenceToNeopixel(colorSequence);

    // Respond to the client with the generated sequence
    JsonObject response = new JsonObject().put("sequence", colorSequence);
    routingContext.response()
                  .putHeader("content-type", "application/json")
                  .end(response.encode());
}

private void sendColorSequenceToNeopixel(JsonArray colorSequence) {
    String topic = "neopixel/display"; // The MQTT topic for NeoPixel
    String message = colorSequence.encode();

    mqttClient.publish(topic,
        Buffer.buffer(message),
        MqttQoS.AT_LEAST_ONCE,
        false,
        false,
        ar -> {
            if (ar.succeeded()) {
                logger.info("Color sequence sent to NeoPixel: " + message);
            } else {
                logger.error("Failed to send color sequence to NeoPixel", ar.cause());
            }
        });
}


}


