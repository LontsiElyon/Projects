/**
 * @file ObjectController.java
 * @brief Controller class to manage routing, handle MQTT messages, and interact with ObjectService.
 * 
 * This class handles HTTP requests via the Vert.x router and MQTT messages for controllers.
 * It processes messages like controller connection, RFID scans, color sequence generation, and more.
 * 
 * @date 2024
 


 * @defgroup ObjectController ObjectController
 * @brief Group for ObjectController class and its related components.
 * 
 * This group includes all functions related to handling MQTT messages, controller connections, 
 * color sequence generation, and various HTTP routes.
 * 
 * @{
 */
/** 
 * @package com.example.controller
 * @brief Description of the package
 */
package com.example.controller;

import io.vertx.core.Vertx;
import com.example.service.ObjectService;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.mqtt.MqttClient;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @class ObjectController
 * @brief Handles HTTP routes and MQTT messages for controller management.
 * 
 * This class sets up HTTP routes, listens to MQTT messages from controllers, and 
 * manages player and controller interactions, including color sequence generation for games.
 * 
 * @ingroup ObjectController
 */
public class ObjectController {

    private static final Logger logger = LoggerFactory.getLogger(ObjectController.class);
    private final ObjectService objectService;
    private final MqttClient mqttClient;
    private Vertx vertx;
    private Map<String, Long> lastHeartbeat = new HashMap<>();
    private static final long HEARTBEAT_TIMEOUT = 30000; // 30 seconds
    
    /**
     * @brief Constructor for ObjectController class.
     * 
     * Initializes the controller with necessary services and sets up HTTP routes.
     * 
     * @param router Vert.x router to register HTTP routes.
     * @param objectService Service for interacting with controllers and players.
     * @param mqttClient MQTT client for handling MQTT messages.
     * @ingroup ObjectController
     */
    public ObjectController(Router router, ObjectService objectService, MqttClient mqttClient) {
        this.objectService = objectService;
        this.mqttClient = mqttClient;
        this.vertx = Vertx.vertx();
        // Register routes

        router.get("/api/controllers").handler(this::handleFetchControllers);
        router.post("/api/login").handler(this::handleLogin);
        router.post("/api/generate-sequence").handler(this::handleGenerateSequence);
        router.get("/api/round-winner").handler(this::handleFetchRoundWinner);         
       
    }


    /**
     * @brief Sets up MQTT message handlers for controller interactions.
     * 
     * Handles MQTT messages for controller connections, RFID scans, color sequences, and heartbeats.
     * @ingroup ObjectController
     */
    public void setupMqttHandlers() {

        logger.debug("Setting up MQTT Handlers");
        // Subscribe to the topic for controller connections with QoS level 1
        mqttClient.publishHandler(message -> {
            logger.debug("Received Message: {}", message);
            if ("controller/connect".equals(message.topicName())) {
                handleControllerConnect(message.payload());
            }
            else if("controller/rfid".equals(message.topicName())){
               handleRfidScan(message.payload());
            }else if("controller/color_sequence".equals(message.topicName())){
                handleColorSequence(message.payload());
            }else if ("controller/request_sequence".equals(message.topicName())) {
                handleSequenceRequest(message.payload());
            }else if("controller/playerstatus".equals(message.topicName())){
                handlePlayerStatus(message.payload());
            }else if("controller/status".equals(message.topicName())){
                handleControllerStatus(message.payload());
            }else if("controller/heartbeat".equals(message.topicName())){
                handleControllerHeartbeat(message.payload());
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
        mqttClient.subscribe("controller/request_sequence", MqttQoS.AT_LEAST_ONCE.value())
            .onSuccess(packetId -> {
                logger.info("Subscribed to controller/request_sequence topic successfully with packet id {}", packetId);
            })
            .onFailure(cause -> {
                logger.error("Failed to subscribe to controller/request_sequence topic: {}", cause.getMessage());
        });
        mqttClient.subscribe("controller/playerstatus", MqttQoS.AT_LEAST_ONCE.value())
            .onSuccess(packetId -> {
                logger.info("Subscribed to controller/playerstatus topic successfully with packet id {}", packetId);
            })
            .onFailure(cause -> {
                logger.error("Failed to subscribe to controller/playerstatus topic: {}", cause.getMessage());
        });
        mqttClient.subscribe("controller/status", MqttQoS.AT_LEAST_ONCE.value())
            .onSuccess(packetId -> {
                logger.info("Subscribed to controller/status topic successfully with packet id {}", packetId);
            })
            .onFailure(cause -> {
                logger.error("Failed to subscribe to controller/status topic: {}", cause.getMessage());
        });
        mqttClient.subscribe("controller/heartbeat", MqttQoS.AT_LEAST_ONCE.value())
            .onSuccess(packetId -> {
                logger.info("Subscribed to controller/heartbeat topic successfully with packet id {}", packetId);
            })
            .onFailure(cause -> {
                logger.error("Failed to subscribe to controller/heartbeat topic: {}", cause.getMessage());
        });
    }  
   
    
    /**
     * @brief Handles controller connection via MQTT message.
     * 
     * Registers the controller and publishes an acknowledgment message.
     * 
     * @param payload MQTT message payload containing controller ID.
     * @ingroup ObjectController
     */
    // Handle incoming connection messages from controllers
    private void handleControllerConnect(Buffer payload) {
        String controllerId = payload.toString();
        logger.debug("Received connection from controller: {}", controllerId);

        // Register the controller in the service
        objectService.registerController(controllerId, res -> {
            if (res.succeeded()) {
                logger.info("Controller registered: {}", controllerId);
                connectedControllers.add(controllerId);
                lastHeartbeat.put(controllerId, System.currentTimeMillis());
                // Optionally publish an acknowledgment back to the controller
                mqttClient.publish("controller/ack", Buffer.buffer("Connected: " + controllerId), MqttQoS.AT_LEAST_ONCE, false, false);
            } else {
                logger.error("Failed to register controller: {}", res.cause().getMessage());
            }
        });
    }

    /**
     * @brief Handles heartbeats from controllers via MQTT message.
     * 
     * Updates the heartbeat timestamp for the controller.
     * 
     * @param payload MQTT message payload containing controller heartbeat information.
     * @ingroup ObjectController
     */
    private void handleControllerHeartbeat(Buffer payload) {
        JsonObject heartbeatJson = payload.toJsonObject();
        String controllerId = heartbeatJson.getString("controllerId");
        if (controllerId != null) {
            lastHeartbeat.put(controllerId, System.currentTimeMillis());
            logger.debug("Received heartbeat from controller: {}", controllerId);
        }
    }

    private void handleControllerStatus(Buffer payload) {
        JsonObject statusJson = payload.toJsonObject();
        String controllerId = statusJson.getString("controllerId");
        String status = statusJson.getString("status");

        if (controllerId != null && status != null) {
            if ("reconnected".equals(status)) {
                logger.info("Controller reconnected: {}", controllerId);
                connectedControllers.add(controllerId);
                lastHeartbeat.put(controllerId, System.currentTimeMillis());
            } else if ("disconnected".equals(status)) {
                logger.info("Controller disconnected: {}", controllerId);
                connectedControllers.remove(controllerId);
                lastHeartbeat.remove(controllerId);
            }
        }
    }
    
    /**
     * @brief Handles heartbeats from controllers via MQTT message.
     * 
     * Updates the heartbeat timestamp for the controller.
     * 
     * @param payload MQTT message payload containing controller heartbeat information.
     * @ingroup ObjectController
     */
    private boolean isControllerActive(String controllerId) {
        Long lastBeat = lastHeartbeat.get(controllerId);
        return lastBeat != null && (System.currentTimeMillis() - lastBeat) < HEARTBEAT_TIMEOUT;
    }

     /**
     * @brief Handles fetching available controllers via HTTP GET.
     * 
     * Fetches the list of connected controllers from the service.
     * 
     * @param ctx RoutingContext of the HTTP request.
     * @ingroup ObjectController
     */
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

    /**
     * @brief Handles player login via HTTP POST.
     * 
     * Registers the player and creates a session for the given controller.
     * 
     * @param ctx RoutingContext of the HTTP request.
     * @ingroup ObjectController
     */
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
        
     /**
     * @brief Handles incoming RFID scan messages from controllers via MQTT.
     * 
     * Converts the payload to extract the controller ID and username, then registers the player.
     * Notifies the frontend upon successful registration of the controller.
     * 
     * @param payload MQTT message payload containing RFID scan information.
     * @ingroup ObjectController
     */
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
                // On failure, log the error with the failure cause
                logger.error("Failed to process RFID scan: {}", res.cause().getMessage());
            }
        });
    }


    private AtomicBoolean isWaitingForResponse = new AtomicBoolean(false);
    private JsonArray currentColorSequence;
    private AtomicInteger activePlayers = new AtomicInteger(0);
    // Add a variable to track the number of rounds played
    private AtomicInteger roundsPlayed = new AtomicInteger(0);
    private List<String> connectedControllers = new ArrayList<>();
    private List<String> controllersWaitingForSequence = new ArrayList<>();
    
   /**
     * @brief Handles the generation of a color sequence for a game.
     * 
     * Fetches connected controllers and starts the game sequence generation process.
     * 
     * @param routingContext RoutingContext of the HTTP request.
     * @ingroup ObjectController
     */
    private void handleGenerateSequence(RoutingContext routingContext) {
        // Retrieve the list of connected controllers
        objectService.getConnectedControllers(connectedControllersResult -> {
            if (connectedControllersResult.succeeded()) {
                List<String> connectedControllers = connectedControllersResult.result();
                activePlayers.set(connectedControllers.size());

                if (connectedControllers.isEmpty()) {
                    routingContext.response()
                        .setStatusCode(200)
                        .end(new JsonObject().put("message", "No connected controllers associated with users").encode());
                    return;
                }

                // Send countdown and start the first round
                sendCountdownToAllControllers(connectedControllers);
                // Start the sequence generation and sending process
                
                try {
                    sendNextSequence(connectedControllers);
                    routingContext.response()
                        .setStatusCode(200)
                        .end(new JsonObject().put("message", "Game sequence generation started").encode());
                } catch (Exception e) {
                    logger.error("Error while generating sequence: ", e);
                    routingContext.response()
                        .setStatusCode(500)
                        .end(new JsonObject().put("error", "Failed to start sequence generation").encode());
                }
                
            } else {
                routingContext.response()
                    .setStatusCode(500)
                    .end(new JsonObject().put("error", "Failed to retrieve connected controllers").encode());
                logger.error("Failed to retrieve connected controllers", connectedControllersResult.cause());
            }
        });
    }


    /**
     * @brief Handles a request from the controller for the next color sequence.
     * 
     * Adds the controller to the waiting list and sends the next sequence if possible.
     * 
     * @param payload MQTT message payload containing the controller ID.
     * @ingroup ObjectController
     */
    private void handleSequenceRequest(Buffer payload) {
    String controllerId = payload.toString();
    logger.debug("Received sequence request from controller: {}", controllerId);

    // Add the controller to the list of controllers waiting for the sequence
    controllersWaitingForSequence.add(controllerId);

        // If we're not already waiting for a response, start the sequence generation process
        if (!isWaitingForResponse.get()) {
            isWaitingForResponse.set(true);
            sendNextSequence(Arrays.asList(controllerId));
        }
    }
    
    /**
     * @brief Sends the next color sequence to the list of controllers.
     * 
     * Generates a color sequence and publishes it to the active controllers.
     * 
     * @param controllers List of active controllers.
     * @return A future representing the completion of the operation.
     * @ingroup ObjectController
     */
    private Future<Void> sendNextSequence(List<String> controllers) {
    Promise<Void> promise = Promise.promise();
    
    // Generate a single color sequence for this round
    currentColorSequence = objectService.generateColorSequence();
    String message = currentColorSequence.encode();
    
    List<Future<Void>> publishFutures = new ArrayList<>();
    
    for (String controllerId : controllers) {
        if (isControllerActive(controllerId)) {
            String topic = "neopixel/display" + controllerId;
            Promise<Void> publishPromise = Promise.promise();
            
            mqttClient.publish(topic,
                Buffer.buffer(message),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                ar -> {
                    if (ar.succeeded()) {
                        logger.info("Color sequence sent to controller {}: {}", controllerId, message);
                        publishPromise.complete();
                    } else {
                        logger.error("Failed to send color sequence to controller {}", controllerId, ar.cause());
                        publishPromise.fail(ar.cause());
                    }
                });
            publishFutures.add(publishPromise.future());
        } else {
            logger.warn("Controller {} is not active, skipping sequence send", controllerId);
        }
    }
    
    // Wait for all publish operations to complete
    Future.all(publishFutures).onComplete(ar -> {
        if (ar.succeeded()) {
            logger.info("Sequence sent to all active controllers for round {}", roundsPlayed.get());
            promise.complete();
        } else {
            logger.error("Failed to send sequence to all controllers", ar.cause());
            promise.fail(ar.cause());
        }
    });
    
    return promise.future();
    }
    
   /**
 * @brief Notifies a controller when a player has lost the game.
 * 
 * Sends a "Game Over" message to the specified controller via MQTT.
 * 
 * @param controllerId ID of the controller to notify.
 * @ingroup ObjectController
 */
    private void notifyControllerOfLoss(String controllerId) {
        JsonObject lossMessage = new JsonObject()
            .put("username", "Game Over")
            .put("points", 0)
            .put("round", 0)
            .put("message", "You lost!");

        String topic = "oled/display/" + controllerId;

        mqttClient.publish(topic,
            Buffer.buffer(lossMessage.encode()),
            MqttQoS.AT_LEAST_ONCE,
            false,
            false,
            ar -> {
                if (ar.succeeded()) {
                    logger.info("Loss notification sent to controller: {}", controllerId);
                } else {
                    logger.error("Failed to send loss notification to controller: {}", controllerId, ar.cause());
                }
            });
    }
    
    /**
     * @brief Handles the incoming color sequence from the controller and compares it with the correct sequence.
     * 
     * Compares the received sequence with the correct one and updates points accordingly.
     * Notifies the controller if they lost or won the round.
     * 
     * @param payload MQTT message payload containing the controller ID and received color sequence.
     * @return A Future<Boolean> indicating the success or failure of the sequence comparison.
     * @ingroup ObjectController
     */
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
                    notifyControllerOfLoss(controllerId);
                    handlePlayerLoss(controllerId);
                    promise.complete(true);
                }
                isWaitingForResponse.set(false);
                    promise.complete(isMatch);
            })
            .onFailure(cause -> {
                logger.error("Failed to compare sequence or update points: {}", cause.getMessage());
                isWaitingForResponse.set(false);
                promise.fail(cause);
            });

        return promise.future();
    }
     
    /**
     * @brief Handles the event when a player loses the game.
     * 
     * Decrements the active player count and removes the disconnected controller from the list.
     * If no players are left, starts a new round.
     * 
     * @param controllerId ID of the controller representing the player who lost.
     * @ingroup ObjectController
     */
    private void handlePlayerLoss(String controllerId) {
        int remainingPlayers = activePlayers.decrementAndGet();
        logger.info("Player lost: {}. Remaining players: {}", controllerId, remainingPlayers);
    
        // Remove the disconnected controller from the connectedControllers list
        connectedControllers.remove(controllerId);
    
        if (remainingPlayers <= 0 ) {
            startNewRound();
        } else {
            logger.info("Waiting for other players to complete their turns.");
        }
    } 
    
    /**
     * @brief Handles incoming player status updates from controllers via MQTT.
     * 
     * Processes status messages, such as a player losing the game.
     * 
     * @param payload MQTT message payload containing controller ID and player status.
     * @ingroup ObjectController
     */
    private void handlePlayerStatus(Buffer payload) {
        JsonObject payloadJson = payload.toJsonObject();
        String controllerId = payloadJson.getString("controllerId");
        String status = payloadJson.getString("status");

        if (controllerId == null || status == null) {
            logger.error("Invalid payload: controllerId or status is missing");
            return;
        }

        if ("lost".equals(status)) {
            handlePlayerLoss(controllerId);
        } else {
            logger.warn("Unrecognized status '{}' for controller {}", status, controllerId);
        }
    }
    
    /**
     * @brief Handles fetching the winner of the current round via an HTTP request.
     * 
     * Retrieves the round winner from the service and sends the result back to the client.
     * 
     * @param ctx RoutingContext of the HTTP request.
     * @ingroup ObjectController
     */
    // New method to handle fetching round winner
    private void handleFetchRoundWinner(RoutingContext ctx) {
        objectService.getRoundWinner(ar -> {
            if (ar.succeeded()) {
                JsonObject winner = ar.result();
                if (winner != null) {
                    int round = winner.getInteger("round");
                    String name = winner.getString("name");
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject()
                             .put("round", round)
                             .put("name", name)
                             .encode());
                    logger.info("Fetched round winner: Round {}, {} (Score: {})", round, name);
                } else {
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(new JsonObject().put("message", "No winner for this round yet.").encode());
                    logger.info("No winner for this round yet.");
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .end("Failed to fetch round winner");
                logger.error("Failed to fetch round winner: {}", ar.cause().getMessage());
            }
        });
    }

    /**
     * @brief Starts a new game round after all players have lost or the round limit is reached.
     * 
     * Resets the game state, increments the round counter, and starts a new round for all connected controllers.
     * 
     * @ingroup ObjectController
     */
    private void startNewRound() {
    
        logger.info("All players have lost. Starting a new round.");

        // Increment the round counter
        int currentRound = roundsPlayed.incrementAndGet();
        logger.info("Starting round number: {}", currentRound);

        // Check if the round limit has been reached
        if (currentRound > 4) {
            stopGame();
            return;
        }

        objectService.getConnectedControllers(ar -> {
            if (ar.succeeded()) {
                List<String> connectedControllers = ar.result();
                activePlayers.set(connectedControllers.size());
                
                // Send countdown signal to all controllers
                sendCountdownToAllControllers(connectedControllers)
                    .compose(v -> {
                        // Wait for 4 seconds (3 second countdown + 1 second "GO!")
                        Promise<Void> timerPromise = Promise.promise();
                        vertx.setTimer(4000, id -> {
                            timerPromise.complete();
                        });
                        return timerPromise.future();
                    })
                    .compose(v -> {
                        List<Future<Void>> futures = new ArrayList<>();
                        for (String controllerId : connectedControllers) {
                            futures.add(objectService.createNewRound(controllerId)
                                .compose(v2 -> {
                                    sendNextSequence(connectedControllers);
                                    return Future.succeededFuture();
                                }));
                        }
                        return Future.all(futures);
                    })
                    .onSuccess(v -> {
                        logger.info("New round started successfully for all controllers");
                    })
                    .onFailure(cause -> {
                        logger.error("Failed to start new round", cause);
                    });
            } else {
                logger.error("Failed to retrieve connected controllers for new round", ar.cause());
            }
        });
    }
    
    /**
     * @brief Sends a countdown signal to all connected controllers.
     * 
     * Iterates over the list of controllers and sends a countdown message to each one.
     * Uses MQTT to publish the countdown signal.
     * 
     * @param controllers List of controller IDs to send the countdown to.
     * @return A Future<Void> that completes when all countdown messages are sent.
     * @ingroup ObjectController
     */
    private Future<Void> sendCountdownToAllControllers(List<String> controllers) {
        List<Future<Void>> futures = new ArrayList<>();
        for (String controllerId : controllers) {
            futures.add(sendCountdown(controllerId));
        }
        return Future.all(futures).mapEmpty();
    }

    private Future<Void> sendCountdown(String controllerId) {
        Promise<Void> promise = Promise.promise();
        JsonObject countdownMessage = new JsonObject().put("action", "countdown");
        String topic = "controller/action/" + controllerId;

        mqttClient.publish(topic,
            Buffer.buffer(countdownMessage.encode()),
            MqttQoS.AT_LEAST_ONCE,
            false,
            false,
            ar -> {
                if (ar.succeeded()) {
                    logger.info("Countdown signal sent to controller: {}", controllerId);
                    promise.complete();
                } else {
                    logger.error("Failed to send countdown signal to controller: {}", controllerId, ar.cause());
                    promise.fail(ar.cause());
                }
            });
        return promise.future();
    }

     /**
     * @brief Sends display information to a specific controller.
     * 
     * Fetches the current round and corresponding display information from the service, then publishes it to the controller's display topic.
     * 
     * @param controllerId ID of the controller to send the display information to.
     * @ingroup ObjectController
     */
    public void sendDisplayInfoToController(String controllerId) {
        // First, fetch the current round
        objectService.fetchCurrentRound(controllerId)
            .compose(currentRound -> 
                // Then fetch the display info for that round
                objectService.fetchDisplayInfo(controllerId, currentRound)
            )
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

    
    /**
     * @brief Stops the game after the game has reached the round limit (5 rounds).
     * 
     * Retrieves all connected controllers, sends a game-over message, and updates the high scores for each player.
     * 
     * @ingroup ObjectController
     */
    private void stopGame() {
        logger.info("Game has reached 5 rounds. Stopping the game.");
        
        objectService.getConnectedControllers(ar -> {
            if (ar.succeeded()) {
                List<String> connectedControllers = ar.result();
                List<Future<Void>> futures = new ArrayList<>();
                
                for (String controllerId : connectedControllers) {
                    notifyControllerOfGameEnd(controllerId);
                    // Assuming updatePlayerHighScore returns Future<Void>
                    futures.add(objectService.updatePlayerHighScore(controllerId));
                }
                
                Future.all(futures).onComplete(result -> {
                    if (result.succeeded()) {
                        logger.info("Game ended and high scores updated for all players.");
                    } else {
                        logger.error("Error occurred while updating high scores", result.cause());
                    }
                });
            } else {
                logger.error("Failed to get connected controllers", ar.cause());
            }
        });
    }
    
    /**
     * @brief Notifies a controller that the game has ended.
     * 
     * Sends a "Game Over" message with the current round number to the controller's display topic.
     * 
     * @param controllerId ID of the controller to notify.
     * @ingroup ObjectController
     */
    // Method to notify a specific controller that the game has ended
    private void notifyControllerOfGameEnd(String controllerId) {
        JsonObject endGameMessage = new JsonObject()
            .put("round", roundsPlayed.get())
            .put("message", "Game Over!");

        String topic = "oled/display/" + controllerId;

        mqttClient.publish(topic,
            Buffer.buffer(endGameMessage.encode()),
            MqttQoS.AT_LEAST_ONCE,
            false,
            false,
            ar -> {
                if (ar.succeeded()) {
                    logger.info("Game end notification sent to controller: {}", controllerId);
                } else {
                    logger.error("Failed to send game end notification to controller: {}", controllerId, ar.cause());
                }
            });
    }


}

/** @} */


