package com.smartcampus.resource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {

    @GET
    public Response discover() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("name", "Smart Campus Sensor & Room Management API");
        meta.put("version", "1.0");
        meta.put("description", "RESTful API for managing campus rooms and IoT sensors.");
        meta.put("contact", "admin@smartcampus.ac.uk");

        Map<String, String> links = new HashMap<>();
        links.put("rooms", "/api/v1/rooms");
        links.put("sensors", "/api/v1/sensors");

        meta.put("resources", links);
        meta.put("timestamp", System.currentTimeMillis());

        return Response.ok(meta).build();
    }
}
