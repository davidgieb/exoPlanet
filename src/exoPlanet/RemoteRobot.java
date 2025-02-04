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
	private BufferedReader groundStationReader; // Für Bodenstationsbefehle
	private boolean running = true; // Steuerung für Autonomes Erkunden

	public RemoteRobot(String robotName, String serverAddress, int serverPort) {
		this.robotName = robotName;
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
	}

	public void connect() throws IOException {
		socket = new Socket(serverAddress, serverPort);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		writer = new PrintWriter(socket.getOutputStream(), true);
		System.out.println("Connected to ExoPlanet server.");

		sendOrbitCommand();
	}

	private String sendOrbitCommand() throws IOException {
		String command = "{\"CMD\":\"orbit\",\"NAME\":\"" + robotName + "\"}";
		writer.println(command);

		String response = reader.readLine();
		System.out.println("Server: " + response);

		if (response.contains("\"CMD\":\"init\"")) {
			return response;
		} else {
			throw new IOException("Unexpected response from server: " + response);
		}
	}

	public String land(int x, int y, String direction) throws IOException {
		String command = "{\"CMD\":\"land\",\"POSITION\":{\"X\":" + x + ",\"Y\":" + y + ",\"DIRECTION\":\"" + direction
				+ "\"}}";
		writer.println(command);

		String response = reader.readLine();
		System.out.println("Server: " + response);

		if (response.contains("\"CMD\":\"landed\"")) {
			return response;
		} else {
			throw new IOException("Unexpected response from server: " + response);
		}
	}

	public String move() throws IOException {
		String command = "{\"CMD\":\"move\"}";
		writer.println(command);

		String response = reader.readLine();
		System.out.println("Server: " + response);

		if (response.contains("\"CMD\":\"moved\"")) {
			return response;
		} else {
			throw new IOException("Unexpected response from server: " + response);
		}
	}

	public String scan() throws IOException {
		String command = "{\"CMD\":\"scan\"}";
		writer.println(command);

		String response = reader.readLine();
		System.out.println("Server: " + response);

		if (response.contains("\"CMD\":\"scaned\"")) {
			return response;
		} else {
			throw new IOException("Unexpected response from server: " + response);
		}
	}

	public void disconnect() throws IOException {
		String command = "{\"CMD\":\"exit\"}";
		writer.println(command);
		running = false; // Beendet autonomes Erkunden
		socket.close();
		System.out.println("Disconnected from server.");
	}

	// Thread für Bodenstation-Befehle
	public void waitForGroundStationCommands(Socket groundStationSocket) {
		Thread groundStationListener = new Thread(() -> {
			try (BufferedReader gsReader = new BufferedReader(
					new InputStreamReader(groundStationSocket.getInputStream()))) {
				groundStationReader = gsReader; // Speichert den Reader für späteren Zugriff
				String command;
				while ((command = groundStationReader.readLine()) != null) {
					System.out.println("Command from ground station: " + command);
					processGroundStationCommand(command);
				}
			} catch (IOException e) {
				System.out.println("Error in ground station communication: " + e.getMessage());
			}
		});

		groundStationListener.start();
	}

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
			System.out.println("Error processing command: " + e.getMessage());
		}
	}

	public void exploreAutonomously() {
		Thread autoExploreThread = new Thread(() -> {
			Random random = new Random();

			while (running) {
				try {
					String scanResult = scan();
					if (scanResult.contains("\"GROUND\":\"LAVA\"") || scanResult.contains("\"GROUND\":\"NICHTS\"")) {
						// Drehe zufällig nach links oder rechts, wenn das Feld gefährlich ist
						String rotation = random.nextBoolean() ? "LEFT" : "RIGHT";
						writer.println("{\"CMD\":\"rotate\",\"ROTATION\":\"" + rotation + "\"}");
						System.out.println("Avoiding dangerous terrain, rotating " + rotation);
					} else {

						move();
					}

					Thread.sleep(1000);
				} catch (IOException | InterruptedException e) {
					System.out.println("Error during autonomous exploration: " + e.getMessage());
				}
			}
		});

		autoExploreThread.start();
	}

	public static void main(String[] args) {
		RemoteRobot robot = new RemoteRobot("Bob", "localhost", 8150);

		try (Socket groundStationSocket = new Socket("localhost", 9000)) { // Bodenstation auf Port 9000
			robot.connect();
			robot.land(0, 0, "EAST");

			// Starte den Thread, um Befehle der Bodenstation zu empfangen
			robot.waitForGroundStationCommands(groundStationSocket);

			// Starte autonomes Erkunden
			robot.exploreAutonomously();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
