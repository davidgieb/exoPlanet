package exoPlanet;

import java.awt.Point;
import java.io.*;
import java.net.Socket;
import java.util.Stack;

/**
 * Ein Roboter-Client, der per DFS den Planeten erkundet, ohne jemals in LAVA /
 * NICHTS zu stürzen. Nach dem Rotieren wird immer erst gescannt und der Boden
 * ausgegeben. Wird Gefahr erkannt, geht der Roboter über den bereits bekannten
 * Weg zurück (Backtracking), bis sich neue sichere Felder finden.
 */
public class WorkingRobot {

	// Roboter- und Verbindungsdaten
	private final String robotName;
	private final String planetServerAddress;
	private final int planetServerPort;

	// Socket-Kommunikation
	private Socket planetSocket;
	private BufferedReader planetReader;
	private PrintWriter planetWriter;

	// Planeten-Abmessungen
	private int planetWidth;
	private int planetHeight;

	// Aktueller Roboterstatus
	private int currentRobotPositionX;
	private int currentRobotPositionY;
	private Direction currentRobotDirection;

	// Verwaltung von besuchten Feldern und Gefahren
	private boolean[][] visitedFields; // Schon besuchte Felder
	private boolean[][] dangerFields; // Felder mit LAVA/NICHTS (gefährlich)

	public WorkingRobot(String robotName, String planetServerAddress, int planetServerPort) {
		this.robotName = robotName;
		this.planetServerAddress = planetServerAddress;
		this.planetServerPort = planetServerPort;
	}

	/**
	 * Stellt die Verbindung zum ExoPlanet-Server her, sendet das orbit-Kommando und
	 * liest die Planetengröße.
	 */
	public void connectToPlanet() throws IOException {
		planetSocket = new Socket(planetServerAddress, planetServerPort);
		planetReader = new BufferedReader(new InputStreamReader(planetSocket.getInputStream()));
		planetWriter = new PrintWriter(planetSocket.getOutputStream(), true);

		System.out.println("Connected to ExoPlanet server.");
		String orbitCommand = "{\"CMD\":\"orbit\",\"NAME\":\"" + robotName + "\"}";
		String orbitResponse = sendJsonCommand(orbitCommand);

		if (orbitResponse != null && orbitResponse.contains("\"CMD\":\"init\"")) {
			// Beispiel: {"CMD":"init","SIZE":{"WIDTH":10,"HEIGHT":6}}
			String widthString = orbitResponse.replaceAll(".*\"WIDTH\":(\\d+).*", "$1");
			String heightString = orbitResponse.replaceAll(".*\"HEIGHT\":(\\d+).*", "$1");
			planetWidth = Integer.parseInt(widthString);
			planetHeight = Integer.parseInt(heightString);
			System.out.println("Planet size: " + planetWidth + " x " + planetHeight);

			visitedFields = new boolean[planetWidth][planetHeight];
			dangerFields = new boolean[planetWidth][planetHeight];
		} else {
			throw new IOException("Missing init response: " + orbitResponse);
		}
	}

	/**
	 * Trennt die Verbindung zum Planeten-Server.
	 */
	public void disconnect() throws IOException {
		sendJsonCommand("{\"CMD\":\"exit\"}");
		planetSocket.close();
		System.out.println("Disconnected.");
	}

	/**
	 * Hilfsmethode zum Senden von JSON-Kommandos und Empfangen der Antwort.
	 */
	private String sendJsonCommand(String jsonCommand) throws IOException {
		planetWriter.println(jsonCommand);
		planetWriter.flush();
		String jsonResponse = planetReader.readLine();
		System.out.println(" -> " + jsonCommand);
		System.out.println(" <- " + jsonResponse);
		return jsonResponse;
	}

	// ------------------------------------------------------
	// LANDEN
	// ------------------------------------------------------

	/**
	 * Lässt den Roboter auf dem Planeten bei (x,y) landen.
	 */
	public void landOnPlanet(int x, int y, Direction direction) throws IOException {
		if (x < 0 || x >= planetWidth || y < 0 || y >= planetHeight) {
			throw new IOException("Invalid landing position outside planet bounds!");
		}
		String landCommand = String.format("{\"CMD\":\"land\",\"POSITION\":{\"X\":%d,\"Y\":%d,\"DIRECTION\":\"%s\"}}",
				x, y, direction.name());
		String landResponse = sendJsonCommand(landCommand);

		if (landResponse != null && landResponse.contains("\"CMD\":\"landed\"")) {
			currentRobotPositionX = x;
			currentRobotPositionY = y;
			currentRobotDirection = direction;
			visitedFields[x][y] = true; // Startfeld
			System.out.println("Landed on (" + x + "," + y + ") facing " + direction);
		} else {
			throw new IOException("Landing failed: " + landResponse);
		}
	}

	// ------------------------------------------------------
	// DFS (Stack), ohne Crash
	// ------------------------------------------------------

	/**
	 * Erkundet den Planeten mithilfe einer Tiefensuche, ohne jemals LAVA/NICHTS zu
	 * betreten.
	 */
	public void explorePlanet() throws IOException {
		Stack<Point> pathStack = new Stack<>();
		pathStack.push(new Point(currentRobotPositionX, currentRobotPositionY));

		while (!pathStack.isEmpty()) {
			Point stackTopPoint = pathStack.peek();
			int currentX = stackTopPoint.x;
			int currentY = stackTopPoint.y;

			// Nach einem unbesuchten, sicheren Nachbarn suchen
			Point nextSafeNeighbor = findUnvisitedSafeNeighbor(currentX, currentY);

			if (nextSafeNeighbor != null) {
				// Versuch, zum nächsten Feld zu gehen
				if (moveTo(nextSafeNeighbor.x, nextSafeNeighbor.y)) {
					visitedFields[nextSafeNeighbor.x][nextSafeNeighbor.y] = true;
					pathStack.push(nextSafeNeighbor);
				} else {
					dangerFields[nextSafeNeighbor.x][nextSafeNeighbor.y] = true;
				}
			} else {
				// Keine Nachbarn => Backtracking
				pathStack.pop();
				if (!pathStack.isEmpty()) {
					Point previousPoint = pathStack.peek();
					moveTo(previousPoint.x, previousPoint.y);
				}
			}
		}
		System.out.println("Exploration finished - no crash, all reachable fields visited!");
	}

	/**
	 * Sucht in (x,y) die 4 möglichen Nachbarn (N/E/S/W), die noch nicht besucht und
	 * nicht als gefährlich markiert sind.
	 */
	private Point findUnvisitedSafeNeighbor(int x, int y) {
		// Norden
		if (y > 0 && !visitedFields[x][y - 1] && !dangerFields[x][y - 1]) {
			return new Point(x, y - 1);
		}
		// Osten
		if (x < planetWidth - 1 && !visitedFields[x + 1][y] && !dangerFields[x + 1][y]) {
			return new Point(x + 1, y);
		}
		// Süden
		if (y < planetHeight - 1 && !visitedFields[x][y + 1] && !dangerFields[x][y + 1]) {
			return new Point(x, y + 1);
		}
		// Westen
		if (x > 0 && !visitedFields[x - 1][y] && !dangerFields[x - 1][y]) {
			return new Point(x - 1, y);
		}
		return null;
	}

	// ------------------------------------------------------
	// Schritt-Funktion: drehen, scannen, move
	// ------------------------------------------------------

	/**
	 * Macht einen Schritt (max. 1 Feld) von (currentRobotPositionX,
	 * currentRobotPositionY) zu (targetX,targetY), falls das Feld sicher ist. -
	 * Dreht sich zuerst (rotate) in die passende Richtung, - scannt (und gibt Boden
	 * aus), - wenn sicher -> move
	 */
	private boolean moveTo(int targetX, int targetY) throws IOException {
		// 1) Bounds-Check
		if (targetX < 0 || targetX >= planetWidth || targetY < 0 || targetY >= planetHeight) {
			System.out.println("moveTo out of bounds => mark dangerous");
			return false;
		}

		// 2) Positions-Differenz ermitteln (keine Abkürzung "dx"/"dy"):
		int differenceOnXAxis = targetX - currentRobotPositionX;
		int differenceOnYAxis = targetY - currentRobotPositionY;

		if (Math.abs(differenceOnXAxis) + Math.abs(differenceOnYAxis) > 1) {
			throw new IOException("moveTo used for non-adjacent cells!");
		}

		// 3) Nötige Richtung bestimmen
		Direction neededDirection = determineDirectionFromPositionDifference(differenceOnXAxis, differenceOnYAxis);
		rotateToDirection(neededDirection);

		// 4) Scan, Boden ausgeben, Gefahr prüfen
		String scanJsonResponse = performScan();
		String groundType = extractGroundType(scanJsonResponse);
		System.out.println("Scanned field in front: " + groundType);

		if (isDangerous(groundType)) {
			System.out.println("Danger in front => do not move");
			return false;
		}

		// 5) Move
		String moveJsonResponse = performMove();
		if (moveJsonResponse.contains("\"CMD\":\"moved\"")) {
			// Erfolgreich bewegt
			currentRobotPositionX = targetX;
			currentRobotPositionY = targetY;
			return true;
		} else if (moveJsonResponse.contains("\"CMD\":\"crashed\"")) {
			System.out.println("Unexpected crash (normal mode?)");
			return false;
		}
		// anderes/unerwartetes Ergebnis
		return false;
	}

	// -----------------------------------------------------
	// Scan / Rotate / Move - Hilfsfunktionen
	// -----------------------------------------------------

	/**
	 * Sendet Scan-Befehl und gibt JSON-Antwort zurück.
	 */
	private String performScan() throws IOException {
		String jsonCommand = "{\"CMD\":\"scan\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);
		if (jsonResponse == null || !jsonResponse.contains("\"CMD\":\"scaned\"")) {
			throw new IOException("Scan failed or no response");
		}
		return jsonResponse;
	}

	/**
	 * Extrahiert den "GROUND" aus dem JSON-Scan, z.B. "GROUND":"SAND" => SAND
	 */
	private String extractGroundType(String scanJsonResponse) {
		return scanJsonResponse.replaceAll(".*\"GROUND\":\"([A-Z]+)\".*", "$1");
	}

	private boolean isDangerous(String groundType) {
		return groundType.equals("LAVA") || groundType.equals("NICHTS");
	}

	/**
	 * Sendet Move-Befehl an den Server und gibt die Antwort zurück.
	 */
	private String performMove() throws IOException {
		String jsonCommand = "{\"CMD\":\"move\"}";
		return sendJsonCommand(jsonCommand);
	}

	/**
	 * Ermittelt aus (differenceOnXAxis, differenceOnYAxis) die Richtung (N/E/S/W).
	 */
	private Direction determineDirectionFromPositionDifference(int differenceOnXAxis, int differenceOnYAxis) {
		if (differenceOnXAxis == 1)
			return Direction.EAST;
		if (differenceOnXAxis == -1)
			return Direction.WEST;
		if (differenceOnYAxis == 1)
			return Direction.SOUTH;
		if (differenceOnYAxis == -1)
			return Direction.NORTH;
		return currentRobotDirection;
	}

	/**
	 * Dreht minimal (links/rechts) zur benötigten Richtung.
	 */
	private void rotateToDirection(Direction targetDirection) throws IOException {
		int currentDirectionIndex = currentRobotDirection.ordinal();
		int targetDirectionIndex = targetDirection.ordinal();
		int totalDirections = Direction.values().length;

		// Wie viele 90°-Drehungen vom aktuellen zum Ziel?
		int directionDifference = (targetDirectionIndex - currentDirectionIndex + totalDirections) % totalDirections;

		if (directionDifference == 0) {
			// Roboter zeigt bereits in die richtige Richtung
			return;
		} else if (directionDifference == 1) {
			performRotateRight();
		} else if (directionDifference == 2) {
			// 180° (2x rotate right)
			performRotateRight();
			performRotateRight();
		} else if (directionDifference == 3) {
			performRotateLeft();
		}
	}

	public void getPos() throws IOException {
		// JSON-Befehl zum Abfragen der Position senden
		String jsonCommand = "{\"CMD\":\"getpos\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);

		// Überprüfen der Antwort und Verarbeiten
		if (jsonResponse != null && jsonResponse.contains("\"CMD\":\"pos\"")) {
			// Extrahieren der Position und Richtung
			int x = Integer.parseInt(jsonResponse.replaceAll(".*\"X\":(\\d+).*", "$1"));
			int y = Integer.parseInt(jsonResponse.replaceAll(".*\"Y\":(\\d+).*", "$1"));
			String directionString = jsonResponse.replaceAll(".*\"DIRECTION\":\"([A-Z]+)\".*", "$1");

			// Aktualisieren der internen Position und Richtung des Roboters
			currentRobotPositionX = x;
			currentRobotPositionY = y;
			currentRobotDirection = Direction.valueOf(directionString);

			// Ausgabe zur Bestätigung
			System.out.println("Current Position: (" + x + ", " + y + "), Facing: " + currentRobotDirection);
		} else {
			throw new IOException("Failed to get position: " + jsonResponse);
		}
	}

	private void performRotateRight() throws IOException {
		String jsonCommand = "{\"CMD\":\"rotate\",\"ROTATION\":\"RIGHT\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);

		if (jsonResponse != null && jsonResponse.contains("\"CMD\":\"rotated\"")) {
			updateDirection(jsonResponse);
		}
	}

	private void performRotateLeft() throws IOException {
		String jsonCommand = "{\"CMD\":\"rotate\",\"ROTATION\":\"LEFT\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);

		if (jsonResponse != null && jsonResponse.contains("\"CMD\":\"rotated\"")) {
			updateDirection(jsonResponse);
		}
	}

	private void updateDirection(String jsonResponse) {
		String newDirectionString = jsonResponse.replaceAll(".*\"DIRECTION\":\"([A-Z]+)\".*", "$1");
		currentRobotDirection = Direction.valueOf(newDirectionString);
	}

	public void waitForGroundStationCommands(Socket groundStationSocket) {
		Thread groundStationListener = new Thread(() -> {
			try (BufferedReader gsReader = new BufferedReader(
					new InputStreamReader(groundStationSocket.getInputStream()))) {
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
				performScan();
				break;
			case "move":
				performMove();
				break;
			case "rotateright":
				performRotateRight();
				break;
			case "rotateleft":
				performRotateLeft();
				break;
			case "explore":
				explorePlanet();
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

	// ---- Hauptmethode zum Starten / Testen ----
	public static void main(String[] args) {
		WorkingRobot robot = new WorkingRobot("WorkingBot", "localhost", 8150);
		try {
			robot.connectToPlanet();
			// Beispielhaft landen auf (0,0), Richtung EAST
			robot.landOnPlanet(0, 0, Direction.EAST);
			// Vollständige DFS-Erkundung
			robot.explorePlanet();
			// Verbindung beenden
			robot.disconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
