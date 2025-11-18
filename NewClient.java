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
    String roomType = null;   // standard | premium | suite
    String chosenDate = null; // YYYY-MM-DD

    public NewClient(Socket c, ArrayList<NewClient> clients) throws IOException {
        this.client = c;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        this.out = new PrintWriter(client.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            // === 1) طلب اسم المستخدم وكلمة المرور ===
            out.println("Enter username:");
            String username = in.readLine();
            if (username == null) { client.close(); return; }

            out.println("Enter password:");
            String password = in.readLine();
            if (password == null) { client.close(); return; }

            synchronized (NewServer.usernames) {
                int idx = NewServer.usernames.indexOf(username);
                if (idx == -1) {
                    // مستخدم جديد
                    NewServer.usernames.add(username);
                    NewServer.passwords.add(password);
                    out.println("REGISTERED_SUCCESSFULLY");
                    System.out.println("New user registered: " + username);
                    this.userName = username;
                } else {
                    // تحقق من كلمة المرور
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

            // === 2) اختيار الوضع: حجز جديد أو إدارة الحجوزات ===
            out.println("MODE?"); 
            String mode = in.readLine();
            if (mode == null) return;

            if ("MANAGE".equalsIgnoreCase(mode)) {
                handleManageBookings();   
            } else {
                handleNewReservation();   
            }

            out.println("Done. Goodbye.");

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            closeQuiet();
        }
    }

    private void handleNewReservation() throws IOException {
        // === نوع الغرفة ===
        out.println("Choose room type (standard/premium/suite):");
        roomType = in.readLine();

        // === التاريخ ===
        out.println("Enter date (YYYY-MM-DD):");
        chosenDate = in.readLine();

        // === عرض الغرف المتاحة ===
        out.println("AVAILABLE:");
        String list = listAvailableRooms();
        out.println(list.isEmpty() ? "No available rooms" : list);

        // === إدخال اسم الغرفة للحجز ===
        out.println("Enter ROOM NAME to reserve (e.g., Sakura-1):");
        String roomName = in.readLine();

        String result = confirmReservation(roomName);
        out.println(result);
    }

    // ========== إدارة الحجوزات (عرض + إلغاء) ==========
    private void handleManageBookings() throws IOException {
        // 1) نجيب حجوزات هذا المستخدم من السيرفر
        ArrayList<NewServer.Reservation> myRes = NewServer.getReservationsForUser(this.userName);

        if (myRes.isEmpty()) {
            out.println("NO_RES");  // لا يوجد حجوزات
            return;
        }

     
        String data = NewServer.buildReservationData(myRes);
        out.println("RES_LIST");  // هيدر
        out.println(data);        // البيانات
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
            out.println("Reservation cancelled for " + target.roomName + " on " + target.date);
        } else {
            out.println("Reservation not found or already cancelled.");
        }
    }

    // ========== دالة تجيب رقم التاريخ ==========
    private int getDateIndex(String date) {
        for (int i = 0; i < NewServer.dates.length; i++) {
            if (NewServer.dates[i].equals(date)) {
                return i;
            }
        }
        return -1;
    }

    // ========== عرض الغرف المتاحة للتاريخ المحدد ==========
    private String listAvailableRooms() {
        if (roomType == null || chosenDate == null) return "";
        int dateIndex = getDateIndex(chosenDate);
        if (dateIndex == -1) return "Invalid date.";

        String[] rooms;
        StringBuilder sb = new StringBuilder();

        if ("standard".equalsIgnoreCase(roomType)) {
            rooms = NewServer.standardRoomsPerDate[dateIndex];
        } else if ("premium".equalsIgnoreCase(roomType)) {
            rooms = NewServer.premiumRoomsPerDate[dateIndex];
        } else if ("suite".equalsIgnoreCase(roomType)) {
            rooms = NewServer.suiteRoomsPerDate[dateIndex];
        } else {
            return "";
        }

        for (String r : rooms) {
            if (r != null) sb.append(r).append(" ");
        }
        return sb.toString().trim();
    }

    // ========== تأكيد الحجز + إضافة الحجز لقائمة السيرفر ==========
    private String confirmReservation(String roomName) {
        if (roomType == null || chosenDate == null || roomName == null || roomName.isEmpty()) {
            return "ERR,MissingData";
        }

        synchronized (NewServer.class) {
            int dateIndex = getDateIndex(chosenDate);
            if (dateIndex == -1) return "ERR,InvalidDate";

            String[][] targetArray;
            if ("standard".equalsIgnoreCase(roomType)) {
                targetArray = NewServer.standardRoomsPerDate;
            } else if ("premium".equalsIgnoreCase(roomType)) {
                targetArray = NewServer.premiumRoomsPerDate;
            } else {
                targetArray = NewServer.suiteRoomsPerDate;
            }

            for (int i = 0; i < targetArray[dateIndex].length; i++) {
                if (targetArray[dateIndex][i] != null &&
                    targetArray[dateIndex][i].equalsIgnoreCase(roomName)) {

                    // نحجز الغرفة لهذا التاريخ
                    targetArray[dateIndex][i] = null;
                    System.out.println("Room " + roomName + " reserved by " + userName + " for " + chosenDate);

                    NewServer.reservations.add(
                        new NewServer.Reservation(userName, roomType, chosenDate, roomName)
                    );

                    return "OK,ReservationDone for " + roomName + " on " + chosenDate;
                }
            }
            return "ERR,RoomNotAvailable";
        }
    }

    // ========== إغلاق الاتصال ==========
    private void closeQuiet() {
        try { out.close(); } catch (Exception ignore) {}
        try { in.close(); } catch (Exception ignore) {}
        try { client.close(); } catch (Exception ignore) {}
    }
}










