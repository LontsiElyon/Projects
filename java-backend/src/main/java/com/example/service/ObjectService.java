package com.example.service;

import com.example.repository.ObjectRepository;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

public class ObjectService {

    private final ObjectRepository objectRepository;

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
}
