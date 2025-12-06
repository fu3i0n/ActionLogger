package carnage.actionLogger.models;

public class ContainerTransaction {
    public final int time = (int)(System.currentTimeMillis()/1000L);
    public final String playerName, containerType, material, world;
    public final byte action; // 0 = took, 1 = placed
    public final short amount;
    public final int x, y, z;

    public ContainerTransaction(String playerName, byte action, String containerType,
                                String material, int amount, String world, int x, int y, int z) {
        this.playerName = playerName;
        this.action = action;
        this.containerType = containerType;
        this.material = material;
        this.amount = (short) Math.max(1, amount);
        this.world = world;
        this.x = x; this.y = y; this.z = z;
    }
}