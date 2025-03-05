package exoPlanet;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;

import org.json.JSONObject;

/**
 * Ein Roboter-Client, der per DFS den Planeten erkundet, ohne jemals in LAVA /
 * NICHTS zu stürzen. Nach dem Rotieren wird immer erst gescannt und der Boden
 * ausgegeben. Wird Gefahr erkannt, geht der Roboter über den bereits bekannten
 * Weg zurück (Backtracking), bis sich neue sichere Felder finden. Roboter kann
 * auch manuell über die Bodenstation gestuert werden.
 */

public class RemoteRobot {

	protected String robotName; // Name wird später durch "init" gesetzt
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

	// Verbindung zur Bodenstation
	Socket groundStationSocket;
	protected BufferedReader groundStationReader;
	protected PrintWriter groundStationWriter;

	public RemoteRobot(String robotName, String planetServerAddress, int planetServerPort) {
		this.robotName = robotName; // Kann zunächst null sein
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
	public void disconnectFromPlanet() {
	    try {
	        if (planetSocket != null && !planetSocket.isClosed()) {
	            sendJsonCommand("{\"CMD\":\"exit\"}");
	            planetSocket.close();
	            planetReader.close();
	            planetWriter.close();
	            System.out.println("Robot " + robotName + " disconnected from planet server.");
	        } else {
	            System.out.println("Robot " + robotName + " is already disconnected.");
	        }
	    } catch (IOException e) {
	        System.err.println("Error while disconnecting robot " + robotName + ": " + e.getMessage());
	    }
	}

	/*
	 * public void connectToGroundStation(String gsAddress, int gsPort) throws
	 * IOException { groundStationSocket = new Socket(gsAddress, gsPort);
	 * this.groundStationReader = new BufferedReader(new
	 * InputStreamReader(groundStationSocket.getInputStream()));
	 * this.groundStationWriter = new
	 * PrintWriter(groundStationSocket.getOutputStream(), true);
	 * 
	 * System.out.println("Connected to Ground Station at " + gsAddress + ":" +
	 * gsPort); }
	 */

	public void disconnectFromGroundStation() throws IOException {
		groundStationSocket.close();
		System.out.println("Disconnected from Ground Station.");
	}

	protected void sendToGroundStation(String msg) {
		if (this.groundStationWriter != null) {
			this.groundStationWriter.println(msg);
			System.out.println("Send to GroundStation" + msg);
			this.groundStationWriter.flush();
		} else {
			System.out.println("Send to GroundStation failed");
		}
	}

	/**
	 * Hilfsmethode zum Senden von JSON-Kommandos und Empfangen der Antwort.
	 */
	private String sendJsonCommand(String jsonCommand) throws IOException {
		// 1) Befehl an den Planeten schicken:
		planetWriter.println(jsonCommand);
		planetWriter.flush();

		// 2) Antwort vom Planeten lesen:
		String jsonResponse = planetReader.readLine();
		System.out.println(" -> " + jsonCommand);
		System.out.println(" <- " + jsonResponse);

		// 3) Forward an die Bodenstation (falls gewünscht)
		sendToGroundStation("[PLANET-RESPONSE] " + jsonResponse);

		// 4) Rückgabe
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

			// Sending data for database insertion
			JSONObject jsonResponse = new JSONObject(landResponse);
			JSONObject measure = jsonResponse.optJSONObject("MEASURE");
			if (measure != null) {
				String ground = measure.optString("GROUND", "unknown");
				double temperature = measure.optDouble("TEMP", -999.0); // Default -999 if missing

				JSONObject data = new JSONObject();
				data.put("CMD", "data");
				data.put("X", x);
				data.put("Y", y);
				data.put("GROUND", ground);
				data.put("TEMP", temperature);

				sendToGroundStation(data.toString());
				System.out.println("Sent data " + data.toString());

			} else {
				throw new IOException("No measurement: " + landResponse);
			}
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

		int differenceOnXAxis = targetX - currentRobotPositionX;
		int differenceOnYAxis = targetY - currentRobotPositionY;

		if (Math.abs(differenceOnXAxis) + Math.abs(differenceOnYAxis) > 1) {
			throw new IOException("moveTo used for non-adjacent cells!");
		}

		// 2) Nötige Richtung bestimmen
		Direction neededDirection = determineDirectionFromPositionDifference(differenceOnXAxis, differenceOnYAxis);
		rotateToDirection(neededDirection);

		// 3) Scan, Boden ausgeben, Gefahr prüfen
		String scanJsonResponse = performScan();
		String groundType = extractGroundType(scanJsonResponse);
		System.out.println("Scanned field in front: " + groundType);

		if (isDangerous(groundType)) {
			System.out.println("Danger in front => do not move");
			return false;
		}

		// 4) Move
		String moveJsonResponse = performMove();
		if (moveJsonResponse.contains("\"CMD\":\"moved\"")) {
			// Erfolgreich bewegt
			currentRobotPositionX = targetX;
			currentRobotPositionY = targetY;
			return true;
		} else if (moveJsonResponse.contains("\"CMD\":\"crashed\"")) {
			System.out.println("Unexpected crash");
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
	protected String performScan() throws IOException {
		String jsonCommand = "{\"CMD\":\"scan\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);

		if (jsonResponse == null || !jsonResponse.contains("\"CMD\":\"scaned\"")) {
			throw new IOException("Scan failed or no response");
		} else {
			// Berechne die korrekten Koordinaten des gescannten Feldes
			Point scannedPos = getScannedPosition();
			int scannedX = scannedPos.x;
			int scannedY = scannedPos.y;

			// Extrahiere den Boden-Typ
			JSONObject scanResponse = new JSONObject(jsonResponse);
			JSONObject measure = scanResponse.optJSONObject("MEASURE");
			if (measure != null) {
				String ground = measure.optString("GROUND", "unknown");
				double temperature = measure.optDouble("TEMP", -999.0); // Default -999 if missing

				// Speichere das gescannte Feld
				visitedFields[scannedX][scannedY] = true; // Markiere das Feld als besucht
				if (isDangerous(ground)) {
					dangerFields[scannedX][scannedY] = true; // Markiere es als gefährlich
				}

				// Daten an die Bodenstation senden
				JSONObject data = new JSONObject();
				data.put("CMD", "data");
				data.put("X", scannedX);
				data.put("Y", scannedY);
				data.put("GROUND", ground);
				data.put("TEMP", temperature);

				sendToGroundStation(data.toString());
				System.out.println("Sent scanned data " + data.toString());

			} else {
				throw new IOException("No measurement: " + jsonResponse);
			}
		}
		return jsonResponse;
	}

	private Point getScannedPosition() {
		int scannedX = currentRobotPositionX;
		int scannedY = currentRobotPositionY;

		switch (currentRobotDirection) {
			case NORTH:
				scannedY -= 1; // Nach Norden → Y wird kleiner
				break;
			case EAST:
				scannedX += 1; // Nach Osten → X wird größer
				break;
			case SOUTH:
				scannedY += 1; // Nach Süden → Y wird größer
				break;
			case WEST:
				scannedX -= 1; // Nach Westen → X wird kleiner
				break;
		}
		return new Point(scannedX, scannedY);
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
	protected String performMove() throws IOException {
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

	protected void performRotateRight() throws IOException {
		String jsonCommand = "{\"CMD\":\"rotate\",\"ROTATION\":\"RIGHT\"}";
		String jsonResponse = sendJsonCommand(jsonCommand);

		if (jsonResponse != null && jsonResponse.contains("\"CMD\":\"rotated\"")) {
			updateDirection(jsonResponse);
		}
	}

	protected void performRotateLeft() throws IOException {
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

	/**
	 * Verarbeitet eingehende Befehle der Bodenstation.
	 */

	public static void main(String[] args) {
		String groundStationHost = "localhost";
		int groundStationPort = 9000;
		int totalRobots = 5; // Adjust the number of robots as needed

		for (int i = 1; i <= totalRobots; i++) {
			try {
				System.out.println("\nStarting Robot  ...");

				CountDownLatch latch = new CountDownLatch(1);
				
				// Create a new RobotListener and assign it a thread
				Thread robotThread = new Thread(new RobotListener(groundStationHost, groundStationPort, latch));

				// Start the robot
				robotThread.start();

				// Wait for the robot to complete its initialization before proceeding
				latch.await();

				System.out.println("Robot initialized successfully.\n");

				System.out.println("All robots initialized successfully.");

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
