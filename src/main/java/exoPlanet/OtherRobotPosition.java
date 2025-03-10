package exoPlanet;

public class OtherRobotPosition {
	private String name;
	private int x;
	private int y;
	
	public OtherRobotPosition(String name, int x, int y) {
		super();
		this.name = name;
		this.x = x;
		this.y = y;
	}
	
	public String getName() {
		return name;
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public void updatePosition(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }
}
