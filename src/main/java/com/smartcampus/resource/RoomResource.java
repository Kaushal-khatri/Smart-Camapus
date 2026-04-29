package com.smartcampus.resource;

import com.smartcampus.DataStore;
import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    private final DataStore store = DataStore.getInstance();

    /**
     * GET /api/v1/rooms
     * Returns a list of all rooms in the system.
     */
    @GET
    public Response getAllRooms() {
        List<Room> roomList = new ArrayList<>(store.getRooms().values());
        return Response.ok(roomList).build();
    }

    /**
     * POST /api/v1/rooms
     * Creates a new room. Returns 201 Created with the new room on success.
     */
    @POST
    public Response createRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Room ID is required."))
                    .build();
        }
        if (store.getRooms().containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Room with ID '" + room.getId() + "' already exists."))
                    .build();
        }
        store.getRooms().put(room.getId(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    /**
     * GET /api/v1/rooms/{roomId}
     * Returns detailed metadata for a single room.
     */
    @GET
    @Path("/{roomId}")
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room '" + roomId + "' not found."))
                    .build();
        }
        return Response.ok(room).build();
    }

    /**
     * DELETE /api/v1/rooms/{roomId}
     * Deletes a room if it has no sensors assigned.
     * Returns 409 Conflict if sensors are still present.
     * Idempotent: returns 404 if room does not exist on repeated calls.
     */
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Room '" + roomId + "' not found."))
                    .build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(
                    "Cannot delete room '" + roomId + "'. It still has " +
                    room.getSensorIds().size() + " sensor(s) assigned: " + room.getSensorIds()
            );
        }
        store.getRooms().remove(roomId);
        return Response.noContent().build();
    }
}
