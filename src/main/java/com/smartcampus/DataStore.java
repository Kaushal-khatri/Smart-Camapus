package com.smartcampus;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory data store using ConcurrentHashMap.
 * Implemented as a singleton to ensure a single source of truth across
 * all JAX-RS resource instances (which are instantiated per-request by default).
 */
public class DataStore {

    private static final DataStore INSTANCE = new DataStore();

    // ConcurrentHashMap ensures thread-safe reads and writes without explicit synchronization
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Sensor> sensors = new ConcurrentHashMap<>();
    private final Map<String, List<SensorReading>> readings = new ConcurrentHashMap<>();

    private DataStore() {
        // Seed some demo data
        Room r1 = new Room("LIB-301", "Library Quiet Study", 40);
        Room r2 = new Room("LAB-101", "Computer Lab A", 30);
        rooms.put(r1.getId(), r1);
        rooms.put(r2.getId(), r2);

        Sensor s1 = new Sensor("TEMP-001", "Temperature", "ACTIVE", 22.5, "LIB-301");
        Sensor s2 = new Sensor("CO2-001", "CO2", "ACTIVE", 412.0, "LAB-101");
        Sensor s3 = new Sensor("OCC-001", "Occupancy", "MAINTENANCE", 15.0, "LIB-301");
        sensors.put(s1.getId(), s1);
        sensors.put(s2.getId(), s2);
        sensors.put(s3.getId(), s3);

        r1.getSensorIds().add(s1.getId());
        r1.getSensorIds().add(s3.getId());
        r2.getSensorIds().add(s2.getId());

        readings.put(s1.getId(), new ArrayList<>());
        readings.put(s2.getId(), new ArrayList<>());
        readings.put(s3.getId(), new ArrayList<>());
    }

    public static DataStore getInstance() {
        return INSTANCE;
    }

    public Map<String, Room> getRooms() { return rooms; }
    public Map<String, Sensor> getSensors() { return sensors; }

    public List<SensorReading> getReadingsForSensor(String sensorId) {
        readings.putIfAbsent(sensorId, new ArrayList<>());
        return readings.get(sensorId);
    }

    public synchronized void addReading(String sensorId, SensorReading reading) {
        readings.putIfAbsent(sensorId, new ArrayList<>());
        readings.get(sensorId).add(reading);
    }
}
