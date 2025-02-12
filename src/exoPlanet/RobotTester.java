package exoPlanet;

import java.awt.Point;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class RobotTester {
	private String robotName;
	private String serverAddress;
	private int serverPort;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	private boolean running = true;

	// DFS- und Positionsverwaltung
	private Stack<Point> dfsStack = new Stack<>();
	private boolean[][] visited;
	private int currentX;
	private int currentY;
	private Direction currentDirection;
	private int mapWidth;
	private int mapHeight;

	public RobotTester(String robotName, String serverAddress, int serverPort) {
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

		// Sende Orbit-Befehl
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
			// Extrahiere Planetengröße
			String json = response.replaceAll(
					".*\\{\"CMD\":\"init\",\"SIZE\":\\{\"WIDTH\":(\\d+),\"HEIGHT\":(\\d+)\\}\\}.*",
					"WIDTH=$1,HEIGHT=$2");
			String[] parts = json.split(",");
			mapWidth = Integer.parseInt(parts[0].split("=")[1]);
			mapHeight = Integer.parseInt(parts[1].split("=")[1]);

			// Initialisiere die Karte
			visited = new boolean[mapWidth][mapHeight];
			return response;
		} else {
			throw new IOException("Unexpected response: " + response);
		}
	}

	/**
	 * Sendet den Land-Befehl.
	 */
	public String land(int x, int y, String direction) throws IOException {
		String command = "{\"CMD\":\"land\",\"POSITION\":{\"X\":" + x + ",\"Y\":" + y + ",\"DIRECTION\":\"" + direction
				+ "\"}}";
		String response = sendCommand(command);
		if (response != null && response.contains("\"CMD\":\"landed\"")) {
			currentX = x;
			currentY = y;
			currentDirection = Direction.valueOf(direction);
			dfsStack.push(new Point(currentX, currentY)); // Startposition hinzufügen
			return response;
		} else {
			throw new IOException("Landing failed: " + response);
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
			throw new IOException("Move failed: " + response);
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
			throw new IOException("Scan failed: " + response);
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
	 * AUTONOME ERKUNDUNG MIT TIEFENSUCHE (DFS)
	 */
	/**
	 * AUTONOME ERKUNDUNG MIT TIEFENSUCHE (DFS)
	 */
	public void exploreAutonomously() {
	    Thread autoExploreThread = new Thread(() -> {
	        try {
	            while (!dfsStack.isEmpty() && running) {
	                Point current = dfsStack.pop();
	                currentX = current.x;
	                currentY = current.y;

	                if (visited[currentX][currentY]) continue;

	                String scanResponse = scan();
	                System.out.println("Scan at (" + currentX + "," + currentY + "): " + scanResponse);

	                // Markiere das Feld als besucht
	                visited[currentX][currentY] = true;
	                System.out.println("Visited: (" + currentX + "," + currentY + ")");

	                // Prüfe auf gefährliches Gelände
	                if (isDangerous(scanResponse)) {
	                    System.out.println("Danger detected at: (" + currentX + "," + currentY + ")");
	                    continue;
	                }

	                // Füge Nachbarn in natürlicher Reihenfolge hinzu
	                List<Point> neighbors = getNeighbors(currentX, currentY);
	                for (Point neighbor : neighbors) {
	                    if (!visited[neighbor.x][neighbor.y]) {
	                        dfsStack.push(neighbor);
	                    }
	                }

	                if (!dfsStack.isEmpty()) {
	                    Point next = dfsStack.peek();
	                    if (!moveTo(next.x, next.y)) {
	                        dfsStack.pop();
	                    }
	                }

	                Thread.sleep(1000);
	            }
	            System.out.println("Exploration complete!");
	        } catch (IOException | InterruptedException e) {
	            System.out.println("Error: " + e.getMessage());
	            running = false;
	        }
	    });
	    autoExploreThread.start();
	}


	/**
	 * Bewegt den Roboter zu einem Ziel (inkl. Drehlogik)
	 */
	private boolean moveTo(int targetX, int targetY) throws IOException {
		int dx = targetX - currentX;
		int dy = targetY - currentY;
		Direction targetDir = getDirectionFromDelta(dx, dy);

		// Drehe in Zielrichtung
		while (currentDirection != targetDir) {
			rotate("RIGHT");
		}

		String moveResponse = move();
	    if (moveResponse.contains("\"CMD\":\"moved\"")) {
	        currentX = targetX;
	        currentY = targetY;
	        return true;
	    } else if (moveResponse.contains("\"CMD\":\"crashed\"")) {
	        visited[targetX][targetY] = true; // Markiere Ziel als blockiert
	        return false;
	    }
	    return false;
	}

	/**
	 * Gibt die benachbarten Felder zurück.
	 */
	private List<Point> getNeighbors(int x, int y) {
	    List<Point> neighbors = new ArrayList<>();
	    // Norden
	    if (y > 0) neighbors.add(new Point(x, y - 1));
	    // Süden
	    if (y < mapHeight - 1) neighbors.add(new Point(x, y + 1));
	    // Westen
	    if (x > 0) neighbors.add(new Point(x - 1, y));
	    // Osten
	    if (x < mapWidth - 1) neighbors.add(new Point(x + 1, y));
	    return neighbors;
	}

	/**
	 * Prüft, ob ein Feld gefährlich ist.
	 */
	private boolean isDangerous(String scanResponse) {
		return scanResponse.contains("\"GROUND\":\"LAVA\"") || scanResponse.contains("\"GROUND\":\"NICHTS\"");
	}

	/**
	 * Dreht den Roboter.
	 */
	private void rotate(String rotation) throws IOException {
		String command = "{\"CMD\":\"rotate\",\"ROTATION\":\"" + rotation + "\"}";
		String response = sendCommand(command);
		if (response.contains("\"CMD\":\"rotated\"")) {
			currentDirection = currentDirection.rotate(rotation.equals("RIGHT") ? Rotation.RIGHT : Rotation.LEFT);
		}
	}

	/**
	 * Gibt die Richtung basierend auf Delta X und Y zurück.
	 */
	private Direction getDirectionFromDelta(int dx, int dy) {
		if (dx == 1)
			return Direction.EAST;
		if (dx == -1)
			return Direction.WEST;
		if (dy == 1)
			return Direction.SOUTH;
		return Direction.NORTH;
	}

	/**
	 * Hauptmethode zum Testen.
	 */
	public static void main(String[] args) {
		RobotTester robot = new RobotTester("TestRoboter", "localhost", 8150);
		try {
			robot.connect();
			robot.land(0, 0, "EAST"); // Starte an Position (0,0)
			robot.exploreAutonomously();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

// ENUMs für Richtung und Rotation
enum Direction {
	NORTH, EAST, SOUTH, WEST;

	public Direction rotate(Rotation r) {
		int idx = this.ordinal();
		return values()[(idx + (r == Rotation.RIGHT ? 1 : 3)) % 4];
	}
}

enum Rotation {
	LEFT, RIGHT
}