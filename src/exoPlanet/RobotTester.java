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
public class RobotTester {

	private final String robotName;
	private final String serverAddress;
	private final int serverPort;

	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;

	// Größe des Planeten
	private int mapWidth;
	private int mapHeight;

	// Derzeitiger Standort & Blickrichtung
	private int currentX;
	private int currentY;
	private Direction currentDir;

	// Verwaltung
	private boolean[][] visited; // Schon besucht?
	private boolean[][] knownDanger; // LAVA/NICHTS bekannt

	public RobotTester(String name, String address, int port) {
		this.robotName = name;
		this.serverAddress = address;
		this.serverPort = port;
	}

	/**
	 * Verbindet sich zum Server, schickt orbit, liest Planetengröße
	 */
	public void connect() throws IOException {
		socket = new Socket(serverAddress, serverPort);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		writer = new PrintWriter(socket.getOutputStream(), true);

		System.out.println("Connected to ExoPlanet server.");
		String orbitCmd = "{\"CMD\":\"orbit\",\"NAME\":\"" + robotName + "\"}";
		String resp = sendCommand(orbitCmd);

		if (resp != null && resp.contains("\"CMD\":\"init\"")) {
			// z.B. {"CMD":"init","SIZE":{"WIDTH":10,"HEIGHT":6}}
			String w = resp.replaceAll(".*\"WIDTH\":(\\d+).*", "$1");
			String h = resp.replaceAll(".*\"HEIGHT\":(\\d+).*", "$1");
			mapWidth = Integer.parseInt(w);
			mapHeight = Integer.parseInt(h);
			System.out.println("Planet size: " + mapWidth + " x " + mapHeight);

			visited = new boolean[mapWidth][mapHeight];
			knownDanger = new boolean[mapWidth][mapHeight];
		} else {
			throw new IOException("Missing init response: " + resp);
		}
	}

	/**
	 * Trennen
	 */
	public void disconnect() throws IOException {
		sendCommand("{\"CMD\":\"exit\"}");
		socket.close();
		System.out.println("Disconnected.");
	}

	/**
	 * JSON-Befehl an Server + Antwort einlesen
	 */
	private String sendCommand(String json) throws IOException {
		writer.println(json);
		writer.flush();
		String response = reader.readLine();
		System.out.println(" -> " + json);
		System.out.println(" <- " + response);
		return response;
	}

	public void land(int x, int y, Direction dir) throws IOException {
		if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) {
			throw new IOException("Invalid landing position outside planet bounds!");
		}
		String cmd = String.format("{\"CMD\":\"land\",\"POSITION\":{\"X\":%d,\"Y\":%d,\"DIRECTION\":\"%s\"}}", x, y,
				dir.name());
		String resp = sendCommand(cmd);

		if (resp != null && resp.contains("\"CMD\":\"landed\"")) {
			currentX = x;
			currentY = y;
			currentDir = dir;
			visited[x][y] = true; // Startfeld
			System.out.println("Landed on (" + x + "," + y + ") facing " + dir);
		} else {
			throw new IOException("Landing failed: " + resp);
		}
	}

	public void exploreAutonomously() throws IOException {
		Stack<Point> path = new Stack<>();
		path.push(new Point(currentX, currentY));

		while (!path.isEmpty()) {
			Point top = path.peek();
			int cx = top.x;
			int cy = top.y;

			// Nach unbesuchtem, sicherem Nachbar suchen
			Point next = findNextNeighbor(cx, cy);

			if (next != null) {
				// Versuch, dorthin zu gehen
				if (stepTo(next.x, next.y)) {
					visited[next.x][next.y] = true;
					path.push(next);
				} else {
					knownDanger[next.x][next.y] = true;
				}
			} else {
				// keine Nachbarn => Backtrack
				path.pop();
				if (!path.isEmpty()) {
					Point back = path.peek();
					stepTo(back.x, back.y);
				}
			}
		}
		System.out.println("Exploration finished - no crash, all reachable fields visited!");
	}

	/**
	 * Sucht Nachbarn (N/E/S/W) von (x,y), der unbesucht und nicht gefährlich ist.
	 */
	private Point findNextNeighbor(int x, int y) {
		// NORTH
		if (y > 0 && !visited[x][y - 1] && !knownDanger[x][y - 1]) {
			return new Point(x, y - 1);
		}
		// EAST
		if (x < mapWidth - 1 && !visited[x + 1][y] && !knownDanger[x + 1][y]) {
			return new Point(x + 1, y);
		}
		// SOUTH
		if (y < mapHeight - 1 && !visited[x][y + 1] && !knownDanger[x][y + 1]) {
			return new Point(x, y + 1);
		}
		// WEST
		if (x > 0 && !visited[x - 1][y] && !knownDanger[x - 1][y]) {
			return new Point(x - 1, y);
		}
		return null;
	}

	/**
	 * Geht (falls sicher) vom aktuellen Feld (currentX,currentY) ein Feld zu
	 * (targetX, targetY) - max 1 Nachbar. Nach dem Rotieren wird zuerst gescannt
	 * und das Ergebnis ausgegeben. Ist dort LAVA/NICHTS, wird abgebrochen.
	 */
	private boolean stepTo(int targetX, int targetY) throws IOException {
		// 1) Bounds-Check
		if (targetX < 0 || targetX >= mapWidth || targetY < 0 || targetY >= mapHeight) {
			System.out.println("stepTo out of bounds => mark dangerous");
			return false;
		}

		// 2) Ermitteln, wie weit wir uns bewegen wollen (dx,dy)
		int dx = targetX - currentX;
		int dy = targetY - currentY;
		if (Math.abs(dx) + Math.abs(dy) > 1) {
			throw new IOException("stepTo used for non-adjacent cells!");
		}

		// 3) Drehen
		Direction neededDir = directionForDelta(dx, dy);
		rotateTo(neededDir);

		// 4) Scannen NACH dem Drehen, ausgeben, Gefahr checken
		String scanResp = doScan();
		// Auslesen des Bodentyps (optional, zum Ausgeben)
		String ground = parseGround(scanResp);
		System.out.println("Scanned field in front: " + ground);

		if (isDangerous(ground)) {
			// -> NICHTS / LAVA => nicht bewegen
			System.out.println("Danger in front => do not move");
			return false;
		}

		// 5) move
		String moveResp = doMove();
		if (moveResp.contains("\"CMD\":\"moved\"")) {
			// wir stehen jetzt dort
			currentX = targetX;
			currentY = targetY;
			return true;
		} else if (moveResp.contains("\"CMD\":\"crashed\"")) {
			System.out.println("Unexpected crash");
			return false;
		}
		// unbekannte Antwort
		return false;
	}

	private String doScan() throws IOException {
		String cmd = "{\"CMD\":\"scan\"}";
		String resp = sendCommand(cmd);
		if (resp == null || !resp.contains("\"CMD\":\"scaned\"")) {
			throw new IOException("Scan failed or no response");
		}
		return resp;
	}

	/**
	 * Parst den "GROUND" aus z.B.
	 * {"CMD":"scaned","MEASURE":{"GROUND":"SAND","TEMP":12.3}}
	 */
	private String parseGround(String scanResp) {
		// GROB per Regex oder simpler:
		// "GROUND":"WASSER"
		// => WASSER
		// Man kann das raffinierter mit JSON-Library tun,
		// aber hier als quick&dirty:
		return scanResp.replaceAll(".*\"GROUND\":\"([A-Z]+)\".*", "$1");
	}

	private boolean isDangerous(String ground) {
		return ground.equals("LAVA") || ground.equals("NICHTS");
	}

	private String doMove() throws IOException {
		String cmd = "{\"CMD\":\"move\"}";
		String resp = sendCommand(cmd);
		return resp;
	}

	private Direction directionForDelta(int dx, int dy) {
		if (dx == 1)
			return Direction.EAST;
		if (dx == -1)
			return Direction.WEST;
		if (dy == 1)
			return Direction.SOUTH;
		if (dy == -1)
			return Direction.NORTH;
		return currentDir;
	}

	private void rotateTo(Direction targetDir) throws IOException {
		// Aktueller Index, Zielindex
		int currentIdx = currentDir.ordinal();
		int targetIdx = targetDir.ordinal();
		int diff = (targetIdx - currentIdx + 4) % 4;
		// diff ∈ {0,1,2,3}

		if (diff == 0) {
			// already facing targetDir
			return;
		} else if (diff == 1) {
			// eine Drehung nach RECHTS reicht
			rotateRight();
		} else if (diff == 2) {
			// 180°-Drehung (zweimal rotateRight, oder rotateLeft 2x)
			rotateRight();
			rotateRight();
		} else if (diff == 3) {
			// eine Drehung nach LINKS ist kürzer als 3x RIGHT
			rotateLeft();
		}
	}

	private void rotateRight() throws IOException {
		String cmd = "{\"CMD\":\"rotate\",\"ROTATION\":\"RIGHT\"}";
		String resp = sendCommand(cmd);
		if (resp != null && resp.contains("\"CMD\":\"rotated\"")) {
			currentDir = currentDir.rotate(Rotation.RIGHT);
		}
	}

	private void rotateLeft() throws IOException {
		String cmd = "{\"CMD\":\"rotate\",\"ROTATION\":\"LEFT\"}";
		String resp = sendCommand(cmd);
		if (resp != null && resp.contains("\"CMD\":\"rotated\"")) {
			currentDir = currentDir.rotate(Rotation.LEFT);
		}
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
                    doScan();
                    break;
                case "move":
                    doMove();
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
	
	// ---- TEST / MAIN ----
	public static void main(String[] args) {
		RobotTester bot = new RobotTester("MegaSafeBot", "localhost", 8150);
		try {
			bot.connect();
			// Landen auf (0,0), Richtung EAST
			bot.land(0, 0, Direction.EAST);
			// Vollständige DFS-Erkundung
			bot.exploreAutonomously();
			// Verbindung beenden
			bot.disconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

/**
 * Himmelsrichtung
 */
enum Direction {
	NORTH, EAST, SOUTH, WEST;

	public Direction rotate(Rotation r) {
		int idx = this.ordinal();
		int len = values().length; // =4
		// RIGHT => +1, LEFT => -1
		int shift = (r == Rotation.RIGHT ? 1 : -1);
		return values()[(idx + shift + len) % len];
	}
}

/**
 * 90°-Rotation
 */
enum Rotation {
	LEFT, RIGHT
}