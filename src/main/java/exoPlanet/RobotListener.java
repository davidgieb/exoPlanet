package exoPlanet;

import java.io.IOException;
import org.json.JSONObject;

public class RobotListener extends RemoteRobot {

	public RobotListener(String robotName, String planetServerAddress, int planetServerPort) {
		super(robotName, planetServerAddress, planetServerPort);
	}

	// Startet einen Listener-Thread, der auf Bodenstationsbefehle wartet
	public void waitForGroundStationCommands() {
		Thread gsListener = new Thread(() -> {
			try {
				String command;
				while ((command = groundStationReader.readLine()) != null) {
					System.out.println("Command from ground station: " + command);
					processGroundStationCommand(command);
				}
			} catch (IOException e) {
				System.out.println("Error in ground station communication: " + e.getMessage());
			}
		});
		gsListener.start();
	}

	private void processGroundStationCommand(String command) {
		try {
			JSONObject jsonCommand = new JSONObject(command);
			String cmdType = jsonCommand.getString("CMD").toLowerCase();

			// **Roboter-Initialisierung**
			if (!isInitialized && cmdType.equals("init")) {
				String robotName = jsonCommand.getString("NAME");
				System.out.println("Initializing robot: " + robotName);
				this.robotName = robotName; // Setzt den Namen für die Verbindung
				isInitialized = true;
				connectToPlanet(); // Erst jetzt mit dem Planeten verbinden
				return;
			}

			if (!isInitialized) {
				System.out.println("Robot not initialized. Waiting for 'init' command...");
				return;
			}

			switch (cmdType) {
			case "land":
				String[] parts = jsonCommand.getString("MESSAGE").split("\\|");

				int x = Integer.parseInt(parts[1]);
				int y = Integer.parseInt(parts[2]);
				Direction direction = Direction.valueOf(parts[3].toUpperCase());

				landOnPlanet(x, y, direction);
				break;
			case "scan":
				System.out.println("Executing scan command");
				performScan();
				break;

			case "move":
				System.out.println("Executing move command");
				performMove();
				break;

			case "rotateright":
				System.out.println("Executing rotate right command");
				performRotateRight();
				break;

			case "rotateleft":
				System.out.println("Executing rotate left command");
				performRotateLeft();
				break;

			case "explore":
				System.out.println("Executing explore command");
				explorePlanet();
				break;

			case "disconnect":
				System.out.println("Executing disconnect command");
				disconnectFromPlanet();
				break;

			default:
				System.out.println("Unknown command received: " + cmdType);
				break;
			}
		} catch (Exception e) {
			System.out.println("Error processing ground station command: " + e.getMessage());
		}
	}
}