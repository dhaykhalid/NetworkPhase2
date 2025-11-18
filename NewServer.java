package javaapplication4;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class NewServer {

    static ArrayList<NewClient> clients = new ArrayList<>();
    static ArrayList<String> usernames = new ArrayList<>();
    static ArrayList<String> passwords = new ArrayList<>();

    // ====== التواريخ المتاحة ======
    static String[] dates = {
        "2025-10-16", "2025-10-17", "2025-10-18",
        "2025-10-19", "2025-10-20", "2025-10-21", "2025-10-22"
    };

    // ====== القوالب الأساسية للغرف ======
    static String[] standardTemplate = {"Sakura-1", "Sakura-2", "Sakura-3", "Sakura-4", "Sakura-5"};
    static String[] premiumTemplate  = {"Fuji-1", "Fuji-2", "Fuji-3", "Fuji-4", "Fuji-5"};
    static String[] suiteTemplate    = {"Koi-1", "Koi-2", "Koi-3", "Koi-4", "Koi-5"};

    // ====== مصفوفات الغرف لكل تاريخ ======
    static String[][] standardRoomsPerDate = new String[dates.length][standardTemplate.length];
    static String[][] premiumRoomsPerDate  = new String[dates.length][premiumTemplate.length];
    static String[][] suiteRoomsPerDate    = new String[dates.length][suiteTemplate.length];

    // ====== قائمة الحجوزات لكل المستخدمين ======
    static class Reservation {
        String username;
        String roomType;  // standard | premium | suite
        String date;      // YYYY-MM-DD
        String roomName;  // Sakura-1, Fuji-2, ...

        Reservation(String username, String roomType, String date, String roomName) {
            this.username = username;
            this.roomType = roomType;
            this.date = date;
            this.roomName = roomName;
        }
    }

    static ArrayList<Reservation> reservations = new ArrayList<>();

    static {
        // ننسخ القوالب إلى كل يوم
        for (int i = 0; i < dates.length; i++) {
            System.arraycopy(standardTemplate, 0, standardRoomsPerDate[i], 0, standardTemplate.length);
            System.arraycopy(premiumTemplate, 0, premiumRoomsPerDate[i], 0, premiumTemplate.length);
            System.arraycopy(suiteTemplate, 0, suiteRoomsPerDate[i], 0, suiteTemplate.length);
        }
    }

    // ====== دوال مساعدة للحجوزات ======

    // ترجع كل حجوزات المستخدم
    public static synchronized ArrayList<Reservation> getReservationsForUser(String username) {
        ArrayList<Reservation> result = new ArrayList<>();
        for (Reservation r : reservations) {
            if (r.username != null && r.username.equals(username)) {
                result.add(r);
            }
        }
        return result;
    }

    // تبني سترنق لإرسال الحجوزات للكلاينت
    public static synchronized String buildReservationData(ArrayList<Reservation> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Reservation r = list.get(i);
            sb.append(i)
              .append(",").append(r.roomType)
              .append(",").append(r.date)
              .append(",").append(r.roomName);
            if (i < list.size() - 1) sb.append(";");
        }
        return sb.toString();
    }

    // ترجع إندكس التاريخ من المصفوفة
    private static int getDateIndex(String date) {
        for (int i = 0; i < dates.length; i++) {
            if (dates[i].equals(date)) return i;
        }
        return -1;
    }

    // تجعل الغرفة متاحة من جديد بعد الإلغاء
    public static synchronized void freeRoom(String roomType, String date, String roomName) {
        int dateIndex = getDateIndex(date);
        if (dateIndex == -1) return;

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

        for (int i = 0; i < template.length; i++) {
            if (template[i].equalsIgnoreCase(roomName)) {
                targetArray[dateIndex][i] = template[i];  // نرجع الاسم الأصلي
                break;
            }
        }
    }

    
    public static synchronized boolean cancelReservation(Reservation target) {
        boolean removed = reservations.remove(target);
        if (!removed) return false;
        freeRoom(target.roomType, target.date, target.roomName);
        System.out.println("Reservation cancelled for " + target.username +
                " -> " + target.roomName + " on " + target.date);
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