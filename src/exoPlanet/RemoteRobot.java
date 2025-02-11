package exoPlanet;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class RemoteRobot {
    private String robotName;
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    // Steuerung für autonomes Erkunden
    private boolean running = true;

    public RemoteRobot(String robotName, String serverAddress, int serverPort) {
        this.robotName = robotName;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    /**
     * Stellt die Verbindung zum Server her und versetzt den Roboter in den Orbit.
     */
    public void connect() throws IOException {
        socket = new Socket(serverAddress, serverPort);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(socket.getOutputStream(), true);
        System.out.println("Connected to ExoPlanet server.");

        // Sende Orbit-Befehl (analog zum funktionierenden RobotTester-Code)
        sendOrbitCommand();
    }

    /**
     * Private Hilfsmethode zum Senden eines Befehls und Abrufen der Antwort.
     */
    private String sendCommand(String command) throws IOException {
        writer.println(command);
        writer.flush();
        String response = reader.readLine();
        System.out.println("Server: " + response);
        return response;
    }

    /**
     * Sendet den Orbit-Befehl, der den Roboter in den Orbit versetzt.
     */
    private String sendOrbitCommand() throws IOException {
        String command = "{\"CMD\":\"orbit\",\"NAME\":\"" + robotName + "\"}";
        String response = sendCommand(command);
        if (response != null && response.contains("\"CMD\":\"init\"")) {
            return response;
        } else {
            throw new IOException("Unexpected response from server: " + response);
        }
    }

    /**
     * Sendet den Land-Befehl.
     */
    public String land(int x, int y, String direction) throws IOException {
        String command = "{\"CMD\":\"land\",\"POSITION\":{\"X\":" + x + ",\"Y\":" + y + ",\"DIRECTION\":\"" + direction + "\"}}";
        String response = sendCommand(command);
        if (response != null && response.contains("\"CMD\":\"landed\"")) {
            return response;
        } else {
            throw new IOException("Unexpected response from server: " + response);
        }
    }

    /**
     * Sendet den Move-Befehl.
     */
    public String move() throws IOException {
        String command = "{\"CMD\":\"move\"}";
        String response = sendCommand(command);
        if (response != null && response.contains("\"CMD\":\"moved\"")) {
            return response;
        } else {
            throw new IOException("Unexpected response from server: " + response);
        }
    }

    /**
     * Sendet den Scan-Befehl.
     */
    public String scan() throws IOException {
        String command = "{\"CMD\":\"scan\"}";
        String response = sendCommand(command);
        if (response != null && response.contains("\"CMD\":\"scaned\"")) {
            return response;
        } else {
            throw new IOException("Unexpected response from server: " + response);
        }
    }

    /**
     * Sendet den Exit-Befehl und schließt die Verbindung.
     */
    public void disconnect() throws IOException {
        String command = "{\"CMD\":\"exit\"}";
        sendCommand(command);
        running = false; // Beendet autonomes Erkunden
        socket.close();
        System.out.println("Disconnected from server.");
    }

    /**
     * Startet einen Thread für autonomes Erkunden.
     * Führt periodisch einen Scan aus und reagiert auf gefährliches Terrain.
     * Bei gefährlichem Terrain dreht der Roboter nun **immer nach rechts**,
     * um ein Hin- und Herlaufen zu vermeiden.
     */
    public void exploreAutonomously() {
        Thread autoExploreThread = new Thread(() -> {
            while (running) {
                try {
                    String scanResult = scan();
                    // Wenn gefährliches Terrain erkannt wird (z. B. LAVA oder NICHTS)
                    if (scanResult.contains("\"GROUND\":\"LAVA\"") || scanResult.contains("\"GROUND\":\"NICHTS\"")) {
                        // Immer nach rechts drehen
                        String rotation = "LEFT";
                        String rotateCommand = "{\"CMD\":\"rotate\",\"ROTATION\":\"" + rotation + "\"}";
                        String rotateResponse = sendCommand(rotateCommand);
                        if (rotateResponse != null && rotateResponse.contains("\"CMD\":\"rotated\"")) {
                            System.out.println("Avoiding dangerous terrain, rotated " + rotation + " successfully.");
                        } else {
                            System.out.println("Unexpected rotate response: " + rotateResponse);
                        }
                    } else {
                        // Bei sicherem Terrain vorwärts bewegen
                        move();
                    }
                    Thread.sleep(1000);
                } catch (IOException | InterruptedException e) {
                    System.out.println("Error during autonomous exploration: " + e.getMessage());
                    // Bei einem Fehler (z. B. Verbindungsabbruch) den autonomen Modus beenden
                    running = false;
                }
            }
        });
        autoExploreThread.start();
    }

    /**
     * Startet einen Listener-Thread, der auf Befehle von der Bodenstation hört.
     */
    public void waitForGroundStationCommands(Socket groundStationSocket) {
        Thread groundStationListener = new Thread(() -> {
            try (BufferedReader gsReader = new BufferedReader(new InputStreamReader(groundStationSocket.getInputStream()))) {
                String command;
                while ((command = gsReader.readLine()) != null) {
                    System.out.println("Command from ground station: " + command);
                    processGroundStationCommand(command);
                }
            } catch (IOException e) {
                System.out.println("Error in ground station communication: " + e.getMessage());
            }
        });
        groundStationListener.start();
    }

    /**
     * Verarbeitet eingehende Befehle der Bodenstation.
     */
    private void processGroundStationCommand(String command) {
        try {
            switch (command.toLowerCase()) {
                case "scan":
                    scan();
                    break;
                case "move":
                    move();
                    break;
                case "explore":
                    exploreAutonomously();
                    break;
                case "disconnect":
                    disconnect();
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    break;
            }
        } catch (IOException e) {
            System.out.println("Error processing ground station command: " + e.getMessage());
        }
    }

  
    public static void main(String[] args) {
        RemoteRobot robot = new RemoteRobot("bob", "localhost", 8150);
        try {
            robot.connect();             // Verbindung herstellen & Orbit-Befehl senden
            robot.land(0, 1, "EAST");      // Landebefehl senden

            // Starte autonomes Erkunden
            robot.exploreAutonomously();

            // Socket groundStationSocket = new Socket("localhost", 9000);
            // robot.waitForGroundStationCommands(groundStationSocket);

            // robot.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
