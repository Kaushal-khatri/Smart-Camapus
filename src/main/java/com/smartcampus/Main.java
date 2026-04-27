package com.smartcampus;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final int PORT = 8080;
    // Grizzly uses the base URI directly — @ApplicationPath on the Application
    // subclass is ignored by GrizzlyHttpServerFactory, so /api/v1 is set here.
    private static final String BASE_URI = "http://0.0.0.0:" + PORT + "/api/v1/";

    public static void main(String[] args) throws Exception {
        URI baseUri = UriBuilder.fromUri(BASE_URI).build();

        ResourceConfig config = ResourceConfig.forApplicationClass(SmartCampusApplication.class);

        final HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);

        // Graceful Ctrl+C shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down Smart Campus API...");
            server.shutdownNow();
        }));

        LOGGER.info("=================================================");
        LOGGER.info("Smart Campus API started (Grizzly).");
        LOGGER.info("Base URL : http://localhost:" + PORT + "/api/v1");
        LOGGER.info("Rooms    : http://localhost:" + PORT + "/api/v1/rooms");
        LOGGER.info("Sensors  : http://localhost:" + PORT + "/api/v1/sensors");
        LOGGER.info("Press Ctrl+C to stop the server.");
        LOGGER.info("=================================================");

        // Block main thread until shutdown
        Thread.currentThread().join();
    }
}
