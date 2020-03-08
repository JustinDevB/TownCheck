package JustinDevB.TownCheck;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.db.TownyDataSource;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

public class Core extends JavaPlugin {

	private File file;
	private YamlConfiguration townyData;

	private List<String> flaggedTowns = new ArrayList<>();

	private Towny towny;

	private int inactiveLimit;

	private long DAY = 86400000; // Milliseconds in a day

	@Override
	public void onEnable() {
		saveDefaultConfig();

		if (getServer().getPluginManager().getPlugin("TownyNameUpdater") != null) {
			Plugin plugin = Bukkit.getPluginManager().getPlugin("TownyNameUpdater");
			File fi = new File(plugin.getDataFolder() + "/playermap.yml");

			if (!fi.canRead())
				log("Error opening file " + fi, true);

			townyData = YamlConfiguration.loadConfiguration(fi);

			file = fi;
		} else {
			log("TownyNameUpdater not found! Disabling plugin...", true);
			getServer().getPluginManager().disablePlugin(this);
		}

		if (getServer().getPluginManager().getPlugin("Towny") != null)
			towny = Towny.getPlugin();
		else {
			log("Towny is not installed! Disabling...", true);
			getServer().getPluginManager().disablePlugin(this);
		}

		// Get Inactive Limit
		inactiveLimit = getConfig().getInt("Main.Inactive-Limit");

	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		int towns = 0;
		Player p = (Player) sender;
		if (cmd.getName().equalsIgnoreCase("towncheck")) {
			if (p.hasPermission("towncheck.check")) {
				try {
					towns = this.inactiveTownChecker();
				} catch (NotRegisteredException e) {
					e.printStackTrace();
				}

				if (towns > 0)
					p.sendMessage(
							ChatColor.RED + "There are " + ChatColor.RED + towns + ChatColor.RED + " inactive towns!");
				else if (towns == 0)
					p.sendMessage(ChatColor.DARK_GREEN + "There are 0 inactive towns!");
				else
					p.sendMessage(ChatColor.RED
							+ " Something went seriously wrong. Report to forum with any related error logs. Inactive towns: "
							+ towns);

				for (int i = 0; i < this.getFlaggedTowns().size(); i++)
					p.sendMessage("{" + (i + 1) + "} " + this.getFlaggedTowns().get(i));

				return true;
			} else
				p.sendMessage(ChatColor.DARK_RED + "You do not have permission to use that command!");
		}

		return false;
	}

	/**
	 * Log a message to the console
	 *
	 * @param msg    Message to log
	 * @param severe Boolean True is SEVERE, false is INFO
	 */
	public void log(String msg, boolean severe) {
		if (severe)
			Bukkit.getLogger().log(Level.SEVERE, msg);
		else
			Bukkit.getLogger().log(Level.INFO, msg);
	}

	/**
	 * Checks all towns to see if inactive defined by inactiveLimit
	 * 
	 * @throws NotRegisteredException
	 */
	@SuppressWarnings("deprecation")
	public int inactiveTownChecker() throws NotRegisteredException {
		log("Checking inactive towns...", false);
		if (getFlaggedTowns().size() > 0)
			return getFlaggedTowns().size();
		String mostRecentOnline = "";
		long recentOn = Integer.MAX_VALUE;
		for (Town t : getTownyDataSource().getTowns()) {
			Resident r = t.getMayor();

			if (isInactive(r)) {
				if (!getFlaggedTowns().contains(t.getName()))
					getFlaggedTowns().add(t.getName());
				log("Flagged " + t.getName() + " as potentially inactive", false);
			}
		}

		for (int i = 0; i < getFlaggedTowns().size(); i++) {
			log("FlaggedTowns size: " + getFlaggedTowns().size(), true);
			Town t = getTownyDataSource().getTown(getFlaggedTowns().get(i));

			for (Resident assistant : t.getAssistants()) {
				if (isInactive(assistant)) {
					OfflinePlayer op = Bukkit.getOfflinePlayer(searchTownyUUIDS(assistant.getName()));
					long lastOn = lastOnline(op.getName());

					if (lastOn < recentOn) {
						recentOn = lastOn;
						mostRecentOnline = assistant.getName();
					}
//TODO: Town Correction

				} else {
					log(t.getName() + " was unflagged, assistant " + assistant.getName()
							+ " was seen recently. Experiemental feature. Double check data!", false);
					getFlaggedTowns().remove(t.getName());
				}
			}
			if (getFlaggedTowns().contains(t.getName())) {
				messageStaff(ChatColor.DARK_RED + "Town: " + ChatColor.GOLD + t.getName() + ChatColor.DARK_RED
						+ " is inactive. Mayor " + ChatColor.GOLD + t.getMayor().getName() + " is inactive. Last seen "
						+ lastOnline(t.getMayor().getName()) + " days ago. Experiemental feature. Double check data!");
				messageStaff(ChatColor.BLUE + "Most recently active Assistant: " + ChatColor.GOLD + mostRecentOnline
						+ ChatColor.BLUE + " last seen: " + recentOn + " days ago");
			}

			recentOn = Integer.MAX_VALUE;

		}

		log(getFlaggedTowns().size() + " town(s) are possibly inactive", false);

		return getFlaggedTowns().size();

	}

	/**
	 * Checks to see if a towny resident is inactive
	 * 
	 * @param resident to be checked
	 * @return boolean
	 * @throws NotRegisteredException
	 */
	private boolean isInactive(Resident r) throws NotRegisteredException {
		if (Bukkit.getPlayer(r.getName()) != null)
			return false;

		long lastOnline = Bukkit.getOfflinePlayer(searchTownyUUIDS(r.getName())).getLastPlayed(),
				currTime = System.currentTimeMillis();
		if (lastOnline <= DAY)
			return false;

		long inactivePeriod = (currTime - lastOnline);
		log("Town: " + r.getTown().getName() + " resident " + r.getName() + " time: "
				+ TimeUnit.MILLISECONDS.toDays(inactivePeriod), true);

		if (TimeUnit.MILLISECONDS.toDays(inactivePeriod) >= getInactiveLimit())
			return true;
		else
			return false;
	}

	/**
	 * Converts milliseconds to days
	 * 
	 * @param milliseconds
	 * @return days
	 */
	public long lastOnline(String name) {
		if (Bukkit.getPlayer(name) != null)
			return -1;

		long lastOn = Bukkit.getOfflinePlayer(searchTownyUUIDS(name)).getLastPlayed(),
				currTime = System.currentTimeMillis();

		long lastOnDays = (currTime - lastOn);

		return TimeUnit.MILLISECONDS.toDays(lastOnDays);
	}

	public UUID searchTownyUUIDS(String name) {
		Set<String> uuids = getTownyData().getKeys(false);
		for (String s : uuids) {
			if (getTownyData().getString(s).equalsIgnoreCase(name)) {
				return UUID.fromString(s);

			}
		}

		log("Could not find: " + name + " at: " + file.toString() + " ... Pinging Mojang Servers for UUID", true);

		return Bukkit.getOfflinePlayer(name).getUniqueId();

	}

	public YamlConfiguration getTownyData() {
		return townyData;
	}

	public List<String> getFlaggedTowns() {
		return this.flaggedTowns;
	}

	public TownyDataSource getTownyDataSource() {
		return getTowny().getTownyUniverse().getDataSource();
	}

	public Towny getTowny() {
		return this.towny;
	}

	public int getInactiveLimit() {
		return this.inactiveLimit;
	}

	public void messageStaff(String msg) {
		for (final Player p : Bukkit.getOnlinePlayers())
			if (p.hasPermission("townchecker.notify")) {
				p.sendMessage(msg);
			}
	}

}
