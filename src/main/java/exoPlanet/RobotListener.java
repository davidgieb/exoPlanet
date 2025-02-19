package exoPlanet;

import java.io.IOException;
import org.json.JSONObject;


public class RobotListener extends RemoteRobot {

	public RobotListener(String robotName, String planetServerAddress, int planetServerPort) {
		super(robotName, planetServerAddress, planetServerPort);
		// TODO Auto-generated constructor stub
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

			switch (cmdType) {
			case "land":
				int x = jsonCommand.getJSONObject("POSITION").getInt("X");
				int y = jsonCommand.getJSONObject("POSITION").getInt("Y");
				String dirString = jsonCommand.getJSONObject("POSITION").getString("DIRECTION");
				Direction direction = Direction.valueOf(dirString.toUpperCase());
				landOnPlanet(x, y, direction);
				break;

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
				disconnectFromPlanet();
				break;

			default:
				System.out.println("Unknown command: " + cmdType);
				break;
			}
		} catch (Exception e) {
			System.out.println("Error processing ground station command: " + e.getMessage());
		}
	}

}