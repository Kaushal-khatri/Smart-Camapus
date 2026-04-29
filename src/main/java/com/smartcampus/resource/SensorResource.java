package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final DataStore store = DataStore.getInstance();

    /**
     * GET /api/v1/sensors
     * Returns all sensors, optionally filtered by ?type=CO2
     */
    @GET
    public Response getAllSensors(@QueryParam("type") String type) {
        List<Sensor> result = store.getSensors().values().stream()
                .filter(s -> type == null || s.getType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
        return Response.ok(result).build();
    }

    /**
     * POST /api/v1/sensors
     * Registers a new sensor. Validates that the referenced roomId exists.
     */
    @POST
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Sensor ID is required."))
                    .build();
        }
        if (store.getSensors().containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Sensor '" + sensor.getId() + "' already exists."))
                    .build();
        }
        // Validate that the referenced room exists
        if (sensor.getRoomId() == null || !store.getRooms().containsKey(sensor.getRoomId())) {
            throw new LinkedResourceNotFoundException(
                    "Room '" + sensor.getRoomId() + "' does not exist. Cannot register sensor without a valid room."
            );
        }

        // Default status to ACTIVE if not provided
        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }

        store.getSensors().put(sensor.getId(), sensor);

        // Link sensor to room
        Room room = store.getRooms().get(sensor.getRoomId());
        if (!room.getSensorIds().contains(sensor.getId())) {
            room.getSensorIds().add(sensor.getId());
        }

        return Response.status(Response.Status.CREATED).entity(sensor).build();
    }

    /**
     * GET /api/v1/sensors/{sensorId}
     * Returns a single sensor by ID.
     */
    @GET
    @Path("/{sensorId}")
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Sensor '" + sensorId + "' not found."))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    /**
     * Sub-resource locator: delegates all /sensors/{sensorId}/readings/* to SensorReadingResource
     */
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            throw new NotFoundException("Sensor '" + sensorId + "' not found.");
        }
        return new SensorReadingResource(sensorId);
    }
}
