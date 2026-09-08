import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.command.*;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.plugin.java.JavaPlugin;

public class ValidationPlugin extends JavaPlugin implements Listener {
    private ServerPlayer player;
    private int places, breaks;
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        org.bukkit.World world = getServer().getWorlds().get(0);
        world.setTime(6000);
        setRule(world, "advance_time", "doDaylightCycle", false);
        setRule(world, "random_tick_speed", "randomTickSpeed", 0);
    }
    @SuppressWarnings("unchecked")
    private static <T> void setRule(org.bukkit.World world, String current, String legacy, T value) {
        org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName(current);
        if (rule == null) rule = org.bukkit.GameRule.getByName(legacy);
        if (rule == null || !rule.getType().isInstance(value) || !world.setGameRule((org.bukkit.GameRule<T>)rule, value))
            throw new IllegalStateException("Cannot set game rule " + current);
    }
    // Keep waterlogged samples independent; flowing water would obscure adjacent dry samples.
    @EventHandler public void fluid(BlockFromToEvent event) { event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void placed(BlockPlaceEvent event) { if (event.getPlayer().getName().equals("DynmapValidator")) places++; }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void broken(BlockBreakEvent event) { if (event.getPlayer().getName().equals("DynmapValidator")) breaks++; }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return false;
        try {
            ServerLevel level = ((CraftWorld)getServer().getWorlds().get(0)).getHandle();
            if (player == null) {
                MinecraftServer server = ((CraftServer)getServer()).getServer();
                GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:DynmapValidator".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "DynmapValidator");
                player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
                player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
                player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                player.setPos(6, 2, 2);
            }
            if (args[0].equals("pose")) {
                String old = level.getWorld().getBlockAt(12,1,12).getBlockData().getAsString();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(12.5,1.5,12), Direction.NORTH, new BlockPos(12,1,12), false));
                String changed = level.getWorld().getBlockAt(12,1,12).getBlockData().getAsString();
                if (old.equals(changed)) throw new IllegalStateException("Pose did not change");
                getLogger().info("VALIDATION pose " + changed);
                return true;
            }
            int before = args[0].equals("place") ? places : breaks;
            for (int x=4; x<8; x++) for (int z=4; z<8; z++) {
                if (args[0].equals("place")) {
                    ItemStack stack = new ItemStack(Items.DIAMOND_BLOCK, 64);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND,
                            new BlockHitResult(new Vec3(x+.5, 1, z+.5), Direction.UP, new BlockPos(x, 0, z), false));
                } else player.gameMode.destroyBlock(new BlockPos(x,1,z));
            }
            int events = (args[0].equals("place") ? places : breaks) - before;
            if (events != 16) throw new IllegalStateException("Expected 16 events, got " + events);
            getLogger().info("VALIDATION " + args[0] + " events=" + events);
        } catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"VALIDATION FAILED",error); }
        return true;
    }
}
