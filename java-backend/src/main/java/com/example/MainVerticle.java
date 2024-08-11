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

public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

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
            } else {
                logger.error("Failed to connect to the MQTT broker: {}", ar.cause().getMessage());
            }
        });

        return mqttClient;
    }

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
        ObjectController objectController = new ObjectController(router, new ObjectService(new ObjectRepository(jdbcPool)), mqttClient);

        return router;
    }

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
