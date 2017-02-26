package namecasenormalizer;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedGameProfile;

public class NameCaseNormalizer extends JavaPlugin implements Listener {

	//the latch that makes packet handling code wait for plugin full load
	protected final CountDownLatch fullEnableLock = new CountDownLatch(1);

	//map that contans lowercase name to player data refs
	protected final ConcurrentHashMap<String, PlayerData> realNames = new ConcurrentHashMap<>();

	@Override
	public void onEnable() {
		//fill real names map (if there is multiple players with the lowercase name player with latest play time is used)
		Arrays.stream(Bukkit.getOfflinePlayers())
		.forEach(offpl -> {
			String lcname = offpl.getName().toLowerCase(Locale.getDefault());
			PlayerData existing = realNames.get(lcname);
			if (existing == null || existing.lastPlayed < offpl.getLastPlayed()) {
				realNames.put(lcname, new PlayerData(offpl));
			}
		});
		//register packet listener (will get the real name for the map, or add an entry if map didn't have it before)
		ProtocolLibrary.getProtocolManager().getAsynchronousManager().registerAsyncHandler(
			new PacketAdapter(PacketAdapter.params(this, PacketType.Login.Client.START).listenerPriority(ListenerPriority.LOWEST)) {
				public void onPacketReceiving(PacketEvent event) {
					PacketContainer container = event.getPacket();
					try {
						fullEnableLock.await();
					} catch (InterruptedException e) {
					}
					StructureModifier<WrappedGameProfile> gameprofiles = container.getGameProfiles();
					String name = gameprofiles.read(0).getName();
					String lcname = name.toLowerCase(Locale.getDefault());
					String realName = realNames.compute(lcname, (mlcname, playerdata) -> {
						return playerdata != null ? playerdata : new PlayerData(name, System.currentTimeMillis());
					}).name;
					event.getPacket().getGameProfiles().write(0, new WrappedGameProfile((UUID) null, realName));
				};
			}
		).start();
		//allow packet handling
		fullEnableLock.countDown();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		return true;
	}

	protected static class PlayerData {
		protected final String name;
		protected final long lastPlayed;

		public PlayerData(String name, long lastPlayed) {
			this.name = name;
			this.lastPlayed = lastPlayed;
		}

		public PlayerData(OfflinePlayer player) {
			this(player.getName(), player.getLastPlayed());
		}
	}

}
