/**
 * @file MainVerticle.java
 * @brief Verticle for setting up MQTT client, JDBC client, and HTTP server in a Vert.x application.
 *
 * This class configures and starts the necessary components for the application:
 * - MQTT client for messaging
 * - JDBC client for database operations
 * - HTTP server with routing and CORS support
 * 
 * It also handles the deployment of the Vert.x application.
 * 
 * @date 2024
 


 * @defgroup MainVerticleGroup Main Verticle
 * @brief Group for MainVerticle class and its related components.
 * 
 * This group includes all classes, methods, and components required for setting up 
 * and running the Vert.x application, including the MQTT client, JDBC client, and HTTP server.
 * 
 * @{
 */

package com.example;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.controller.ObjectController;
import com.example.repository.ObjectRepository;
import com.example.service.ObjectService;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.sqlclient.PoolOptions;


/**
 * @class MainVerticle
 * @brief Verticle for initializing and managing MQTT, JDBC, and HTTP server.
 * 
 * This verticle handles the setup of MQTT and JDBC clients, HTTP routing, and server creation.
 * 
 * @ingroup MainVerticleGroup
 */
public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    ObjectController objectController;

    /**
     * @brief Starts the verticle by initializing MQTT client, JDBC client, and HTTP server.
     * 
     * @param startPromise Promise used to signal completion of the startup process.
     * @ingroup MainVerticleGroup
     */
    @Override
    public void start(Promise<Void> startPromise) {
        // Retrieve environment variables
        String mqttUsername = System.getenv("MQTT_USERNAME");
        logger.info("MQTT_USERNAME: {}", mqttUsername);

        String mqttPassword = System.getenv("MQTT_PASSWORD");
        logger.info("MQTT_PASSWORD: {}", mqttPassword);

        String dbHost = System.getenv("DB_HOST");
        logger.info("DB_HOST: {}", dbHost);

        int dbPort = Integer.parseInt(System.getenv("DB_PORT"));
        logger.info("DB_PORT: {}", dbPort);

        String dbName = System.getenv("DB_NAME");
        logger.info("DB_NAME: {}", dbName);

        String dbUser = System.getenv("DB_USER");
        logger.info("DB_USER: {}", dbUser);

        String dbPassword = System.getenv("DB_PASSWORD");
        logger.info("DB_PASSWORD: {}", dbPassword);

        // Setup MQTT client
        MqttClient mqttClient = setupMqttClient(mqttUsername, mqttPassword);
        
        // Setup JDBC client pool
        JDBCPool jdbcPool = setupJdbcClient(dbHost, dbPort, dbName, dbUser, dbPassword);
        // Setup HTTP server router
        Router router = setupRouter(mqttClient, jdbcPool);

        // Create and start the HTTP server
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, http -> {
                    if (http.succeeded()) {
                        startPromise.complete();
                        logger.info("HTTP server started on port 8080");
                    } else {
                        startPromise.fail(http.cause());
                        logger.error("Failed to start HTTP server: {}", http.cause().getMessage());
                    }
                });
    }
    
    /**
     * @brief Configures and creates an MQTT client.
     * 
     * @param username MQTT username.
     * @param password MQTT password.
     * @return Configured MqttClient instance.
     * @ingroup MainVerticleGroup
     */
    private MqttClient setupMqttClient(String username, String password) {
        // MQTT client configuration options
        MqttClientOptions options = new MqttClientOptions()
                .setAutoKeepAlive(true)
                .setUsername(username)
                .setPassword(password);

        MqttClient mqttClient = MqttClient.create(vertx, options);

        // Connect to MQTT broker
        mqttClient.connect(1883, "mosquitto", ar -> {
            if (ar.succeeded()) {
                logger.info("Connected to the MQTT broker successfully!");
                // Setup MQTT subscription and handlers
                this.objectController.setupMqttHandlers();
                //this.objectController.setupMqttHandlers2();
                
            } else {
                logger.error("Failed to connect to the MQTT broker: {}", ar.cause().getMessage());
            }
        });

        return mqttClient;
    }

    
    /**
     * @brief Configures and creates a JDBC client pool.
     * 
     * @param host Database host.
     * @param port Database port.
     * @param dbName Database name.
     * @param user Database user.
     * @param password Database password.
     * @return Configured JDBCPool instance.
     * @ingroup MainVerticleGroup
     */
    private JDBCPool setupJdbcClient(String host, int port, String dbName, String user, String password) {
        // JDBC client pool configuration options
        return JDBCPool.pool(vertx,
                new io.vertx.jdbcclient.JDBCConnectOptions()
                        .setJdbcUrl(String.format("jdbc:mariadb://%s:%d/%s", host, port, dbName))
                        .setUser(user)
                        .setPassword(password),
                new PoolOptions().setMaxSize(5)
        );
    }
    
    /**
     * @brief Sets up the HTTP server router with CORS support and routes.
     * 
     * @param mqttClient MQTT client instance.
     * @param jdbcPool JDBC client pool instance.
     * @return Configured Router instance.
     * @ingroup MainVerticleGroup
     */
    private Router setupRouter(MqttClient mqttClient, JDBCPool jdbcPool) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // CORS configuration
        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");

        Set<io.vertx.core.http.HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(io.vertx.core.http.HttpMethod.GET);
        allowedMethods.add(io.vertx.core.http.HttpMethod.POST);
        allowedMethods.add(io.vertx.core.http.HttpMethod.OPTIONS);
        allowedMethods.add(io.vertx.core.http.HttpMethod.PUT);
        allowedMethods.add(io.vertx.core.http.HttpMethod.DELETE);

        router.route().handler(CorsHandler.create("*")
                .allowedHeaders(allowedHeaders)
                .allowedMethods(allowedMethods));

        // Setup controllers
        this.objectController = new ObjectController(router, new ObjectService(new ObjectRepository(jdbcPool)), mqttClient);

        return router;
    }
    
    /**
     * @brief Main method for deploying MainVerticle.
     * 
     * @ingroup MainVerticleGroup
     */
    public static void main(String[] args) {
        // Create Vertx instance and deploy MainVerticle
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle(), res -> {
            if (res.succeeded()) {
                logger.info("Verticle deployment succeeded");
            } else {
                logger.error("Verticle deployment failed: {}", res.cause().getMessage());
            }
        });
    }
}

/** @} */  // End of MainVerticleGroup