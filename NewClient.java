package javaapplication4;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

public class NewClient implements Runnable {

    private final Socket client;
    private final BufferedReader in;
    private final PrintWriter out;
    private final ArrayList<NewClient> clients;

    String userName = null;
    String roomType = null;
    String checkInDate = null;
    String checkOutDate = null;

    public NewClient(Socket c, ArrayList<NewClient> clients) throws IOException {
        this.client = c;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        this.out = new PrintWriter(client.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            // ==== login / register ====
            out.println("Enter username:");
            String username = in.readLine();
            if (username == null) { client.close(); return; }

            out.println("Enter password:");
            String password = in.readLine();
            if (password == null) { client.close(); return; }

            synchronized (NewServer.usernames) {
                int idx = NewServer.usernames.indexOf(username);
                if (idx == -1) {
                    NewServer.usernames.add(username);
                    NewServer.passwords.add(password);
                    out.println("REGISTERED_SUCCESSFULLY");
                    System.out.println("New user registered: " + username);
                    this.userName = username;
                } else {
                    String saved = NewServer.passwords.get(idx);
                    if (saved.equals(password)) {
                        out.println("LOGIN_SUCCESS");
                        System.out.println("User logged in: " + username);
                        this.userName = username;
                    } else {
                        out.println("WRONG_PASSWORD");
                        System.out.println("Failed login attempt for: " + username);
                        client.close();
                        return;
                    }
                }
            }

            // ==== choose mode ====
            out.println("MODE?"); // client: BOOK or MANAGE
            String mode = in.readLine();
            if (mode == null) return;

            if ("MANAGE".equalsIgnoreCase(mode)) {
                handleManageBookings();
            } else {
                handleNewReservationRange();
            }

            out.println("Done. Goodbye.");

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            closeQuiet();
        }
    }

    // ========== new reservation with date range ==========
    private void handleNewReservationRange() throws IOException {
        // room type
        out.println("Choose room type (standard/premium/suite):");
        roomType = in.readLine();

        // check-in
        out.println("Enter check-in date (YYYY-MM-DD):");
        checkInDate = in.readLine();

        // check-out
        out.println("Enter check-out date (YYYY-MM-DD):");
        checkOutDate = in.readLine();

        // basic validation
        int startIdx = getDateIndex(checkInDate);
        int endIdx = getDateIndex(checkOutDate);
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) {
            out.println("AVAILABLE:");
            out.println("Invalid date range.");
            return;
        }

        // show rooms available for *all* nights in the range
        out.println("AVAILABLE:");
        String list = listAvailableRoomsForRange();
        out.println(list.isEmpty() ? "No available rooms" : list);

        out.println("Enter ROOM NAME to reserve (e.g., Sakura-1):");
        String roomName = in.readLine();

        String result = confirmReservationForRange(roomName);
        out.println(result);
    }

    // ========== manage bookings ==========
    private void handleManageBookings() throws IOException {
        ArrayList<NewServer.Reservation> myRes = NewServer.getReservationsForUser(this.userName);

        if (myRes.isEmpty()) {
            out.println("NO_RES");
            return;
        }

        String data = NewServer.buildReservationData(myRes);
        out.println("RES_LIST");
        out.println(data);
        out.println("CHOOSE_INDEX");

        String line = in.readLine();
        if (line == null) return;

        int idx;
        try {
            idx = Integer.parseInt(line.trim());
        } catch (NumberFormatException ex) {
            out.println("Invalid index format.");
            return;
        }

        if (idx < 0) {
            out.println("No cancellation made.");
            return;
        }

        if (idx >= myRes.size()) {
            out.println("Index out of range.");
            return;
        }

        NewServer.Reservation target = myRes.get(idx);
        boolean ok = NewServer.cancelReservation(target);
        if (ok) {
            out.println("Reservation cancelled for " + target.roomName +
                        " from " + target.startDate + " to " + target.endDate);
        } else {
            out.println("Reservation not found or already cancelled.");
        }
    }

    // ========== helpers ==========

    private int getDateIndex(String date) {
        for (int i = 0; i < NewServer.dates.length; i++) {
            if (NewServer.dates[i].equals(date)) {
                return i;
            }
        }
        return -1;
    }

    // rooms free for all nights in [checkInDate, checkOutDate)
    private String listAvailableRoomsForRange() {
        if (roomType == null || checkInDate == null || checkOutDate == null) return "";
        int startIdx = getDateIndex(checkInDate);
        int endIdx   = getDateIndex(checkOutDate);
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return "";

        String[][] targetArray;
        String[] template;

        if ("standard".equalsIgnoreCase(roomType)) {
            targetArray = NewServer.standardRoomsPerDate;
            template = NewServer.standardTemplate;
        } else if ("premium".equalsIgnoreCase(roomType)) {
            targetArray = NewServer.premiumRoomsPerDate;
            template = NewServer.premiumTemplate;
        } else if ("suite".equalsIgnoreCase(roomType)) {
            targetArray = NewServer.suiteRoomsPerDate;
            template = NewServer.suiteTemplate;
        } else {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // for each room in template, check it's available every night
        for (int col = 0; col < template.length; col++) {
            boolean availableAll = true;
            for (int d = startIdx; d < endIdx; d++) {
                if (targetArray[d][col] == null) {
                    availableAll = false;
                    break;
                }
            }
            if (availableAll) {
                sb.append(template[col]).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // confirm reservation across date range
    private String confirmReservationForRange(String roomName) {
        if (roomType == null || checkInDate == null || checkOutDate == null ||
                roomName == null || roomName.isEmpty()) {
            return "ERR,MissingData";
        }

        synchronized (NewServer.class) {
            int startIdx = getDateIndex(checkInDate);
            int endIdx   = getDateIndex(checkOutDate);
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx)
                return "ERR,InvalidDateRange";

            String[][] targetArray;
            String[] template;

            if ("standard".equalsIgnoreCase(roomType)) {
                targetArray = NewServer.standardRoomsPerDate;
                template = NewServer.standardTemplate;
            } else if ("premium".equalsIgnoreCase(roomType)) {
                targetArray = NewServer.premiumRoomsPerDate;
                template = NewServer.premiumTemplate;
            } else {
                targetArray = NewServer.suiteRoomsPerDate;
                template = NewServer.suiteTemplate;
            }

            // find room column
            int roomCol = -1;
            for (int i = 0; i < template.length; i++) {
                if (template[i].equalsIgnoreCase(roomName)) {
                    roomCol = i;
                    break;
                }
            }
            if (roomCol == -1) return "ERR,RoomNotFound";

            // check availability for every night
            for (int d = startIdx; d < endIdx; d++) {
                if (targetArray[d][roomCol] == null) {
                    return "ERR,RoomNotAvailableForWholeRange";
                }
            }

            // mark taken for each night
            for (int d = startIdx; d < endIdx; d++) {
                targetArray[d][roomCol] = null;
            }

            // save reservation + log
            NewServer.Reservation r = new NewServer.Reservation(
                    userName, roomType, checkInDate, checkOutDate, roomName
            );
            NewServer.reservations.add(r);

            System.out.println("Reservation added for " + userName +
                    " -> " + roomName + " from " + checkInDate + " to " + checkOutDate);

            return "OK,ReservationDone for " + roomName +
                   " from " + checkInDate + " to " + checkOutDate;
        }
    }

    private void closeQuiet() {
        try { out.close(); } catch (Exception ignore) {}
        try { in.close(); } catch (Exception ignore) {}
        try { client.close(); } catch (Exception ignore) {}
    }
}








