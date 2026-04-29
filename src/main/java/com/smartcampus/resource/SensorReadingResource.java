package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorReadingResource {

    private final String sensorId;
    private final DataStore store = DataStore.getInstance();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    /**
     * GET /api/v1/sensors/{sensorId}/readings
     * Returns the historical reading log for this sensor.
     */
    @GET
    public Response getReadings() {
        List<SensorReading> history = store.getReadingsForSensor(sensorId);
        return Response.ok(history).build();
    }

    /**
     * POST /api/v1/sensors/{sensorId}/readings
     * Appends a new reading. Blocked if the sensor is in MAINTENANCE status.
     * Side effect: updates the parent sensor's currentValue.
     */
    @POST
    public Response addReading(SensorReading reading) {
        Sensor sensor = store.getSensors().get(sensorId);

        // Business logic: sensors in MAINTENANCE cannot accept readings
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                    "Sensor '" + sensorId + "' is currently under MAINTENANCE and cannot accept new readings."
            );
        }
        if (reading == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Reading body is required."))
                    .build();
        }

        // Auto-assign ID and timestamp if not provided
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading = new SensorReading(reading.getValue());
        }

        store.addReading(sensorId, reading);

        // Side effect: update the sensor's currentValue for data consistency
        sensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}
