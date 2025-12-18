package com.project.fim;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MongoManager {
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_NAME = "fim_db";
    private static final String EVENTS_COLLECTION_NAME = "file_events";
    private static final String SNAPSHOTS_COLLECTION_NAME = "file_snapshots";

    private final MongoCollection<Document> eventsCollection;
    private final MongoCollection<Document> snapshotsCollection;
    private final MongoClient mongoClient;

    public MongoManager() {
        mongoClient = MongoClients.create(CONNECTION_STRING);
        MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
        eventsCollection = database.getCollection(EVENTS_COLLECTION_NAME);
        snapshotsCollection = database.getCollection(SNAPSHOTS_COLLECTION_NAME);
    }

    public void close() {
        mongoClient.close();
    }

    public Map<Path, String> loadHashes() {
        Map<Path, String> hashes = new HashMap<>();
        for (Document doc : snapshotsCollection.find()) {
            hashes.put(Paths.get(doc.getString("_id")), doc.getString("hash"));
        }
        return hashes;
    }

    public void logEvent(Path filePath, String eventType, String hash) {
        // Log the event to the events collection
        Document event = new Document("eventType", eventType)
                .append("hash", hash)
                .append("timestamp", new java.util.Date());

        eventsCollection.updateOne(
                Filters.eq("_id", filePath.toString()),
                Updates.push("events", event),
                new com.mongodb.client.model.UpdateOptions().upsert(true)
        );

        // Update the snapshots collection
        if ("DELETE".equals(eventType)) {
            snapshotsCollection.deleteOne(Filters.eq("_id", filePath.toString()));
        } else {
            snapshotsCollection.updateOne(
                    Filters.eq("_id", filePath.toString()),
                    Updates.set("hash", hash),
                    new com.mongodb.client.model.UpdateOptions().upsert(true)
            );
        }
    }
}
