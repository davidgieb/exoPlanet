package exoPlanet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import org.json.JSONObject;

public class RobotListener extends RemoteRobot implements Runnable {

	private final CountDownLatch latch; // Used for signaling initialization
	//private boolean isInitialized = false;
	//private Socket groundStationSocket;
	//private BufferedReader groundStationReader;
	//private PrintWriter groundStationWriter;
	
	/*public RobotListener(String robotName, String planetServerAddress, int planetServerPort) {
		super(robotName, planetServerAddress, planetServerPort);
	}*/
	
	public RobotListener(String groundStationHost, int groundStationPort, CountDownLatch latch) throws IOException {
        super(null, "localhost", 8150); // Name will be assigned later
        this.groundStationSocket = new Socket(groundStationHost, groundStationPort);
        this.groundStationReader = new BufferedReader(new InputStreamReader(groundStationSocket.getInputStream()));
        this.groundStationWriter = new PrintWriter(groundStationSocket.getOutputStream(), true);
        this.latch = latch;
    }
	

	@Override
	public void run() {
		try {
			System.out.println("Connected to GroundStation. Waiting for robot name...");

			// 1) Send registration request to GroundStation
            JSONObject initRequest = new JSONObject();
            initRequest.put("CMD", "register");
            if (groundStationWriter == null) {
            	System.out.println("Writer is null");
            }
            if (groundStationReader == null) {
            	System.out.println("Reader is null");
            }
            groundStationWriter.println(initRequest.toString());
            groundStationWriter.flush();

			String jsonLine = groundStationReader.readLine();
			if (jsonLine == null || jsonLine.trim().isEmpty()) {
				System.err.println("No JSON received from GroundStation. Exiting...");
				return;
			}

			JSONObject json = new JSONObject(jsonLine);
			this.robotName = json.optString("name", "").trim();

			if (robotName.isEmpty()) {
				System.err.println("Invalid robot name received. Exiting...");
				return;
			}

			System.out.println("Assigned robot name: " + robotName);

			// Connect to planet
			connectToPlanet();
			
			// Signal that initialization is complete
            latch.countDown();

			// 4) Start listening for commands from GroundStation
            listenForGroundStationCommands();

		} catch (IOException e) {
			System.err.println("Error in RobotListener: " + e.getMessage());
		}
	}

	
	// Startet einen Listener-Thread, der auf Bodenstationsbefehle wartet
	public void listenForGroundStationCommands() {
		
			try {
				String command;
				while ((command = groundStationReader.readLine()) != null) {
					System.out.println("Command from ground station: " + command);
					processGroundStationCommand(command);
				}
			} catch (IOException e) {
				System.out.println("Error in ground station communication: " + e.getMessage());
			}
		};		
	
	private void processGroundStationCommand(String command) {
		try {
			JSONObject jsonCommand = new JSONObject(command);
			String cmdType = jsonCommand.getString("CMD").toLowerCase();

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