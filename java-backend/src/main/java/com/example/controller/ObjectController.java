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
}
