package javaapplication4;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class NewServer {

    static ArrayList<NewClient> clients = new ArrayList<>();
    static ArrayList<String> usernames = new ArrayList<>();
    static ArrayList<String> passwords = new ArrayList<>();

    // ====== available dates ======
    static String[] dates = {
        "2025-10-16", "2025-10-17", "2025-10-18",
        "2025-10-19", "2025-10-20", "2025-10-21", "2025-10-22"
    };

    // room templates
    static String[] standardTemplate = {"Sakura-1", "Sakura-2", "Sakura-3", "Sakura-4", "Sakura-5"};
    static String[] premiumTemplate  = {"Fuji-1", "Fuji-2", "Fuji-3", "Fuji-4", "Fuji-5"};
    static String[] suiteTemplate    = {"Koi-1", "Koi-2", "Koi-3", "Koi-4", "Koi-5"};

    // availability for each date
    static String[][] standardRoomsPerDate = new String[dates.length][standardTemplate.length];
    static String[][] premiumRoomsPerDate  = new String[dates.length][premiumTemplate.length];
    static String[][] suiteRoomsPerDate    = new String[dates.length][suiteTemplate.length];

    // ====== reservations (now from–to) ======
    static class Reservation {
        String username;
        String roomType;   // standard | premium | suite
        String startDate;  // check-in
        String endDate;    // check-out (not staying this night)
        String roomName;

        Reservation(String username, String roomType,
                    String startDate, String endDate, String roomName) {
            this.username  = username;
            this.roomType  = roomType;
            this.startDate = startDate;
            this.endDate   = endDate;
            this.roomName  = roomName;
        }
    }

    static ArrayList<Reservation> reservations = new ArrayList<>();

    static {
        // copy templates into each day
        for (int i = 0; i < dates.length; i++) {
            System.arraycopy(standardTemplate, 0, standardRoomsPerDate[i], 0, standardTemplate.length);
            System.arraycopy(premiumTemplate, 0, premiumRoomsPerDate[i], 0, premiumTemplate.length);
            System.arraycopy(suiteTemplate, 0, suiteRoomsPerDate[i], 0, suiteTemplate.length);
        }
    }

    // ====== helpers ======

    public static synchronized ArrayList<Reservation> getReservationsForUser(String username) {
        ArrayList<Reservation> result = new ArrayList<>();
        for (Reservation r : reservations) {
            if (r.username != null && r.username.equals(username)) {
                result.add(r);
            }
        }
        return result;
    }

    // encode reservations for the client: idx, type, start, end, room
    public static synchronized String buildReservationData(ArrayList<Reservation> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Reservation r = list.get(i);
            sb.append(i)
              .append(",").append(r.roomType)
              .append(",").append(r.startDate)
              .append(",").append(r.endDate)
              .append(",").append(r.roomName);
            if (i < list.size() - 1) sb.append(";");
        }
        return sb.toString();
    }

    // index of date in dates[]
    static int getDateIndex(String date) {
        for (int i = 0; i < dates.length; i++) {
            if (dates[i].equals(date)) return i;
        }
        return -1;
    }

    // free a room for all nights in [startDate, endDate)
    static synchronized void freeRoomRange(String roomType,
                                           String startDate, String endDate,
                                           String roomName) {
        int startIdx = getDateIndex(startDate);
        int endIdx   = getDateIndex(endDate);
        if (startIdx == -1 || endIdx == -1) return;
        if (endIdx <= startIdx) return; // nothing

        String[][] targetArray;
        String[] template;

        if ("standard".equalsIgnoreCase(roomType)) {
            targetArray = standardRoomsPerDate;
            template = standardTemplate;
        } else if ("premium".equalsIgnoreCase(roomType)) {
            targetArray = premiumRoomsPerDate;
            template = premiumTemplate;
        } else {
            targetArray = suiteRoomsPerDate;
            template = suiteTemplate;
        }

        // which column is this room?
        int roomCol = -1;
        for (int i = 0; i < template.length; i++) {
            if (template[i].equalsIgnoreCase(roomName)) {
                roomCol = i;
                break;
            }
        }
        if (roomCol == -1) return;

        // put the original room name back for each date in the stay
        for (int d = startIdx; d < endIdx; d++) {
            targetArray[d][roomCol] = template[roomCol];
        }
    }

    // cancel reservation and free all related dates
    public static synchronized boolean cancelReservation(Reservation target) {
        boolean removed = reservations.remove(target);
        if (!removed) return false;
        freeRoomRange(target.roomType, target.startDate, target.endDate, target.roomName);
        System.out.println("Reservation cancelled for " + target.username +
                " -> " + target.roomName + " from " + target.startDate + " to " + target.endDate);
        return true;
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9090);
        System.out.println(" Server started on port 9090 ");

        while (true) {
            Socket client = serverSocket.accept();
            System.out.println("New client connected!");
            NewClient clientThread = new NewClient(client, clients);
            clients.add(clientThread);
            new Thread(clientThread).start();
        }
    }
}