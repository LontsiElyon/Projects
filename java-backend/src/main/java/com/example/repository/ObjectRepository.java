package com.example.repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

public class ObjectRepository {

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
}
