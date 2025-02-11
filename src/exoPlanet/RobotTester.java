package exoPlanet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;

public class RobotTester {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8150;

        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("Verbindung zum Server hergestellt!");

            // Orbit-Befehl senden
            String orbitCommand = "{\"CMD\":\"orbit\",\"NAME\":\"bob\"}";
            String response1 = sendCommand(out, in, orbitCommand);
           

            // Land-Befehl senden
            String landCommand = "{\"CMD\":\"land\",\"POSITION\":{\"X\":0,\"Y\":0,\"DIRECTION\":\"EAST\"}}";
            String response2 = sendCommand(out, in, landCommand);
           

            // Scan-Befehl senden
            String scanCommand = "{\"CMD\":\"scan\"}";
            String response3 = sendCommand(out, in, scanCommand);
            

            // Exit-Befehl senden
            String exitCommand = "{\"CMD\":\"exit\"}";
            String response4 = sendCommand(out, in, exitCommand);
            System.out.println("Beende TestClient.");

        } catch (Exception e) {
            System.err.println("Fehler beim Verbinden oder Senden an den Server:");
            e.printStackTrace();
        }
    }

    // Hilfsmethode zum Senden eines Befehls und Empfangen der Antwort
    public static String sendCommand(PrintWriter out, BufferedReader in, String command) throws IOException {
        // Befehl senden
        out.println(command);
        out.flush(); // Sicherstellen, dass der Befehl gesendet wird

        // Antwort vom Server lesen
        String response = in.readLine();
        if (response != null) {
            System.out.println("Server antwortet: " + response); // Ausgabe der Serverantwort für Debugging
            return response;
        } else {
            throw new IOException("Keine Antwort vom Server erhalten!");
        }
    }
}
