package com.nisovin.shopkeepers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang.Validate;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nisovin.shopkeepers.api.ShopCreationData;
import com.nisovin.shopkeepers.api.ShopCreationData.PlayerShopCreationData;
import com.nisovin.shopkeepers.api.Shopkeeper;
import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.ShopkeepersPlugin;
import com.nisovin.shopkeepers.api.events.CreatePlayerShopkeeperEvent;
import com.nisovin.shopkeepers.api.events.ShopkeeperCreatedEvent;
import com.nisovin.shopkeepers.api.shoptypes.PlayerShopType;
import com.nisovin.shopkeepers.api.shoptypes.PlayerShopkeeper;
import com.nisovin.shopkeepers.api.shoptypes.ShopType;
import com.nisovin.shopkeepers.api.util.TradingRecipe;
import com.nisovin.shopkeepers.compat.NMSManager;
import com.nisovin.shopkeepers.metrics.CitizensChart;
import com.nisovin.shopkeepers.metrics.FeaturesChart;
import com.nisovin.shopkeepers.metrics.GringottsChart;
import com.nisovin.shopkeepers.metrics.PlayerShopsChart;
import com.nisovin.shopkeepers.metrics.ShopkeepersCountChart;
import com.nisovin.shopkeepers.metrics.TownyChart;
import com.nisovin.shopkeepers.metrics.VaultEconomyChart;
import com.nisovin.shopkeepers.metrics.WorldGuardChart;
import com.nisovin.shopkeepers.metrics.WorldsChart;
import com.nisovin.shopkeepers.pluginhandlers.CitizensHandler;
import com.nisovin.shopkeepers.pluginhandlers.TownyHandler;
import com.nisovin.shopkeepers.pluginhandlers.WorldGuardHandler;
import com.nisovin.shopkeepers.registry.SKShopkeeperRegistry;
import com.nisovin.shopkeepers.shopobjects.AbstractShopObjectType;
import com.nisovin.shopkeepers.shopobjects.SKDefaultShopObjectTypes;
import com.nisovin.shopkeepers.shopobjects.SKShopObjectTypesRegistry;
import com.nisovin.shopkeepers.shopobjects.living.LivingEntityAI;
import com.nisovin.shopkeepers.shoptypes.AbstractPlayerShopType;
import com.nisovin.shopkeepers.shoptypes.AbstractShopType;
import com.nisovin.shopkeepers.shoptypes.SKDefaultShopTypes;
import com.nisovin.shopkeepers.shoptypes.SKShopTypesRegistry;
import com.nisovin.shopkeepers.storage.SKShopkeeperStorage;
import com.nisovin.shopkeepers.tradelogging.TradeFileLogger;
import com.nisovin.shopkeepers.ui.SKUIRegistry;
import com.nisovin.shopkeepers.ui.defaults.SKDefaultUITypes;
import com.nisovin.shopkeepers.util.ItemUtils;
import com.nisovin.shopkeepers.util.Log;
import com.nisovin.shopkeepers.util.SKTradingRecipe;
import com.nisovin.shopkeepers.util.SchedulerUtils;
import com.nisovin.shopkeepers.util.Utils;

public class SKShopkeepersPlugin extends JavaPlugin implements ShopkeepersPlugin {

	private static final int ASYNC_TASKS_TIMEOUT_SECONDS = 10;

	private static SKShopkeepersPlugin plugin;

	public static SKShopkeepersPlugin getInstance() {
		return plugin;
	}

	// shop types and shop object types registry:
	private final SKShopTypesRegistry shopTypesRegistry = new SKShopTypesRegistry();
	private final SKShopObjectTypesRegistry shopObjectTypesRegistry = new SKShopObjectTypesRegistry();

	// default shop and shop object types:
	private SKDefaultShopTypes defaultShopTypes;
	private SKDefaultShopObjectTypes defaultShopObjectTypes;

	// ui registry:
	private final SKUIRegistry uiRegistry = new SKUIRegistry(this);
	private final SKDefaultUITypes defaultUITypes = new SKDefaultUITypes();

	private final Map<String, ConfirmEntry> confirming = new HashMap<>();
	private final Map<String, AbstractShopkeeper> naming = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, List<String>> recentlyPlacedChests = new HashMap<>();
	private final Map<String, Block> selectedChest = new HashMap<>();

	// protected chests:
	private final ProtectedChests protectedChests = new ProtectedChests();
	private final LivingEntityAI livingEntityAI = new LivingEntityAI(this);

	// shopkeeper registry:
	private final SKShopkeeperRegistry shopkeeperRegistry = new SKShopkeeperRegistry(this);

	// storage
	private final SKShopkeeperStorage shopkeeperStorage = new SKShopkeeperStorage(this);

	// listeners:
	private CreatureForceSpawnListener creatureForceSpawnListener = null;
	private SignShopListener signShopListener = null;

	@Override
	public void onEnable() {
		plugin = this;
		ShopkeepersAPI.enable(this);

		// reset a bunch of variables:
		shopkeeperStorage.reset();

		// try to load suitable NMS code:
		NMSManager.load(this);
		if (NMSManager.getProvider() == null) {
			Log.severe("Incompatible server version: Shopkeepers cannot be enabled.");
			this.setEnabled(false);
			return;
		}

		// load config:
		File file = new File(this.getDataFolder(), "config.yml");
		if (!file.exists()) {
			this.saveDefaultConfig();
		}
		this.reloadConfig();
		Configuration config = this.getConfig();
		boolean settingsMissing = Settings.loadConfiguration(config);
		if (settingsMissing) {
			// if settings were missing -> add those to the config and save it
			// TODO persist comments somehow
			this.saveConfig();
		}

		// load lang config:
		String lang = Settings.language;
		File langFile = new File(this.getDataFolder(), "language-" + lang + ".yml");
		if (!langFile.exists() && this.getResource("language-" + lang + ".yml") != null) {
			this.saveResource("language-" + lang + ".yml", false);
		}
		if (langFile.exists()) {
			try {
				YamlConfiguration langConfig = new YamlConfiguration();
				langConfig.load(langFile);
				Settings.loadLanguageConfiguration(langConfig);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// process additional permissions
		String[] perms = Settings.maxShopsPermOptions.replace(" ", "").split(",");
		for (String perm : perms) {
			if (Bukkit.getPluginManager().getPermission("shopkeeper.maxshops." + perm) == null) {
				Bukkit.getPluginManager().addPermission(new Permission("shopkeeper.maxshops." + perm, PermissionDefault.FALSE));
			}
		}

		// enable shopkeeper registry:
		shopkeeperRegistry.onEnable();

		// initialize default shop and shop object types (after config has been loaded):
		defaultShopTypes = new SKDefaultShopTypes();
		defaultShopObjectTypes = new SKDefaultShopObjectTypes();

		// register default stuff:

		shopTypesRegistry.registerAll(defaultShopTypes.getAllShopTypes());
		shopObjectTypesRegistry.registerAll(defaultShopObjectTypes.getAllObjectTypes());
		uiRegistry.registerAll(defaultUITypes.getAllUITypes());

		// inform ui registry (registers ui event handlers):
		uiRegistry.onEnable();

		// inform ProtectedChests:
		protectedChests.onEnable();

		// register events
		PluginManager pm = Bukkit.getPluginManager();
		pm.registerEvents(new PluginListener(), this);
		pm.registerEvents(new WorldListener(this), this);
		pm.registerEvents(new PlayerJoinQuitListener(this), this);
		pm.registerEvents(new ShopNamingListener(this), this);
		pm.registerEvents(new ChestListener(this), this);
		pm.registerEvents(new CreateListener(this), this);
		pm.registerEvents(new VillagerInteractionListener(this), this);
		pm.registerEvents(new LivingEntityShopListener(shopkeeperRegistry), this);
		pm.registerEvents(new TradingCountListener(this), this);
		pm.registerEvents(new TradeFileLogger(this.getDataFolder()), this);

		if (Settings.enableSignShops) {
			this.signShopListener = new SignShopListener(this);
			pm.registerEvents(signShopListener, this);
		}

		// enable citizens handler:
		CitizensHandler.enable();

		if (Settings.blockVillagerSpawns) {
			pm.registerEvents(new BlockVillagerSpawnListener(), this);
		}

		if (Settings.protectChests) {
			pm.registerEvents(new ChestProtectListener(this), this);
		}
		if (Settings.deleteShopkeeperOnBreakChest) {
			pm.registerEvents(new RemoveShopOnChestBreakListener(this), this);
		}

		// register force-creature-spawn event handler:
		if (Settings.bypassSpawnBlocking) {
			creatureForceSpawnListener = new CreatureForceSpawnListener();
			pm.registerEvents(creatureForceSpawnListener, this);
		}

		// register command handler:
		CommandManager commandManager = new CommandManager(this);
		this.getCommand("shopkeeper").setExecutor(commandManager);

		// load shopkeeper saved data:
		boolean loadingSuccessful = shopkeeperStorage.load();
		if (!loadingSuccessful) {
			// detected an issue during loading
			// disabling the plugin without saving, to prevent loss of shopkeeper data:
			Log.severe("Detected an issue during the loading of the shopkeepers data! Disabling the plugin!");
			shopkeeperStorage.disableSaving();
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		// activate (spawn) shopkeepers in loaded chunks:
		shopkeeperRegistry.loadShopkeepersInLoadedWorlds();

		Bukkit.getScheduler().runTaskLater(this, () -> {
			// remove invalid citizens shopkeepers:
			CitizensHandler.removeInvalidCitizensShopkeepers();
			// remove inactive player shopkeepers:
			removeInactivePlayerShops();
		}, 5L);

		// start save task:
		if (!Settings.saveInstantly) {
			Bukkit.getScheduler().runTaskTimer(this, () -> {
				if (shopkeeperStorage.isDirty()) {
					shopkeeperStorage.saveReal();
				}
			}, 6000, 6000); // 5 minutes
		}

		// let's update the shopkeepers for all already online players:
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (Utils.isNPC(player)) continue;
			this.updateShopkeepersForPlayer(player.getUniqueId(), player.getName());
		}

		// setup metrics:
		if (Settings.enableMetrics) {
			this.setupMetrics();
		}
	}

	@Override
	public void onDisable() {
		// wait for async tasks to complete:
		SchedulerUtils.awaitAsyncTasksCompletion(this, ASYNC_TASKS_TIMEOUT_SECONDS, this.getLogger());

		// close all open windows:
		uiRegistry.closeAll();
		// inform ui registry about disable:
		uiRegistry.onDisable();

		// despawn all active shopkeepers:
		shopkeeperRegistry.despawnAll();

		// disable citizens handler:
		CitizensHandler.disable();

		// save:
		if (shopkeeperStorage.isDirty()) {
			shopkeeperStorage.saveReal(false); // not async here
		}

		// inform other components:
		livingEntityAI.stop();
		livingEntityAI.reset(); // cleanup, reset timings, etc.
		protectedChests.onDisable();

		// cleanup:
		creatureForceSpawnListener = null;

		shopkeeperRegistry.onDisable();

		shopTypesRegistry.clearAllSelections();
		shopObjectTypesRegistry.clearAllSelections();

		confirming.clear();
		naming.clear();
		selectedChest.clear();

		// clear all types of registers:
		shopTypesRegistry.clearAll();
		shopObjectTypesRegistry.clearAll();
		uiRegistry.clearAll();

		HandlerList.unregisterAll(this);
		Bukkit.getScheduler().cancelTasks(this);

		ShopkeepersAPI.disable();
		plugin = null;
	}

	/**
	 * Reloads the plugin.
	 */
	public void reload() {
		this.onDisable();
		this.onEnable();
	}

	// METRICS

	private void setupMetrics() {
		Metrics metrics = new Metrics(this);
		metrics.addCustomChart(new CitizensChart());
		metrics.addCustomChart(new WorldGuardChart());
		metrics.addCustomChart(new TownyChart());
		metrics.addCustomChart(new VaultEconomyChart());
		metrics.addCustomChart(new GringottsChart());
		metrics.addCustomChart(new ShopkeepersCountChart(shopkeeperRegistry));
		metrics.addCustomChart(new PlayerShopsChart(shopkeeperRegistry));
		metrics.addCustomChart(new FeaturesChart());
		metrics.addCustomChart(new WorldsChart(shopkeeperRegistry));
	}

	// PLAYER CLEANUP

	void onPlayerQuit(Player player) {
		String playerName = player.getName();
		shopTypesRegistry.clearSelection(player);
		shopObjectTypesRegistry.clearSelection(player);
		uiRegistry.onInventoryClose(player);

		selectedChest.remove(playerName);
		recentlyPlacedChests.remove(playerName);
		naming.remove(playerName);
		this.endConfirmation(player);
	}

	// SHOPKEEPER REGISTRY

	@Override
	public SKShopkeeperRegistry getShopkeeperRegistry() {
		return shopkeeperRegistry;
	}

	// SHOPKEEPER STORAGE

	@Override
	public SKShopkeeperStorage getShopkeeperStorage() {
		return shopkeeperStorage;
	}

	// CREATURE FORCE SPAWN

	// bypassing creature blocking plugins ('region protection' plugins):
	public void forceCreatureSpawn(Location location, EntityType entityType) {
		if (creatureForceSpawnListener != null && Settings.bypassSpawnBlocking) {
			creatureForceSpawnListener.forceCreatureSpawn(location, entityType);
		}
	}

	public void cancelNextBlockPhysics(Location location) {
		if (signShopListener != null) {
			signShopListener.cancelNextBlockPhysics(location);
		}
	}

	// UI

	@Override
	public SKUIRegistry getUIRegistry() {
		return uiRegistry;
	}

	@Override
	public SKDefaultUITypes getDefaultUITypes() {
		return defaultUITypes;
	}

	// PROTECTED CHESTS:

	public ProtectedChests getProtectedChests() {
		return protectedChests;
	}

	// LIVING ENTITY AI

	public LivingEntityAI getLivingEntityAI() {
		return livingEntityAI;
	}

	// SHOP TYPES

	@Override
	public SKShopTypesRegistry getShopTypeRegistry() {
		return shopTypesRegistry;
	}

	@Override
	public SKDefaultShopTypes getDefaultShopTypes() {
		return defaultShopTypes;
	}

	// SHOP OBJECT TYPES

	@Override
	public SKShopObjectTypesRegistry getShopObjectTypeRegistry() {
		return shopObjectTypesRegistry;
	}

	@Override
	public SKDefaultShopObjectTypes getDefaultShopObjectTypes() {
		return defaultShopObjectTypes;
	}

	/**
	 * Gets the default shop object type.
	 * 
	 * <p>
	 * Usually this will be the villager entity shop object type. However, there are no guarantees that this might not
	 * get changed or be configurable in the future.
	 * 
	 * @return the default shop object type
	 */
	public AbstractShopObjectType<?> getDefaultShopObjectType() {
		// default: villager entity shop object type:
		return this.getDefaultShopObjectTypes().getLivingEntityObjectTypes().getObjectType(EntityType.VILLAGER);
	}

	// RECENTLY PLACED CHESTS

	void onChestPlacement(Player player, Block chest) {
		assert player != null && chest != null && ItemUtils.isChest(chest.getType());
		String playerName = player.getName();
		List<String> recentlyPlaced = recentlyPlacedChests.get(playerName);
		if (recentlyPlaced == null) {
			recentlyPlaced = new LinkedList<>();
			recentlyPlacedChests.put(playerName, recentlyPlaced);
		}
		recentlyPlaced.add(Utils.getLocationString(chest));
		if (recentlyPlaced.size() > 5) {
			recentlyPlaced.remove(0);
		}
	}

	public boolean isRecentlyPlaced(Player player, Block chest) {
		assert player != null && chest != null && ItemUtils.isChest(chest.getType());
		String playerName = player.getName();
		List<String> recentlyPlaced = recentlyPlacedChests.get(playerName);
		return recentlyPlaced != null && recentlyPlaced.contains(Utils.getLocationString(chest));
	}

	// SELECTED CHEST

	void selectChest(Player player, Block chest) {
		assert player != null;
		String playerName = player.getName();
		if (chest == null) selectedChest.remove(playerName);
		else {
			assert ItemUtils.isChest(chest.getType());
			selectedChest.put(playerName, chest);
		}
	}

	public Block getSelectedChest(Player player) {
		assert player != null;
		return selectedChest.get(player.getName());
	}

	// COMMAND CONFIRMING

	void waitForConfirm(final Player player, Runnable action, int timeoutTicks) {
		assert player != null && timeoutTicks > 0;
		int taskId = Bukkit.getScheduler().runTaskLater(this, () -> {
			endConfirmation(player);
			Utils.sendMessage(player, Settings.msgConfirmationExpired);
		}, timeoutTicks).getTaskId();

		ConfirmEntry oldEntry = confirming.put(player.getName(), new ConfirmEntry(action, taskId));
		if (oldEntry != null) {
			// end old confirmation task:
			Bukkit.getScheduler().cancelTask(oldEntry.getTaskId());
		}
	}

	Runnable endConfirmation(Player player) {
		ConfirmEntry entry = confirming.remove(player.getName());
		if (entry != null) {
			// end confirmation task:
			Bukkit.getScheduler().cancelTask(entry.getTaskId());

			// return action:
			return entry.getAction();
		}
		return null;
	}

	void onConfirm(Player player) {
		assert player != null;
		Runnable action = this.endConfirmation(player);
		if (action != null) {
			// execute confirmed task:
			action.run();
		} else {
			Utils.sendMessage(player, Settings.msgNothingToConfirm);
		}
	}

	// SHOPKEEPER NAMING

	void onNaming(Player player, AbstractShopkeeper shopkeeper) {
		assert player != null && shopkeeper != null;
		naming.put(player.getName(), shopkeeper);
	}

	AbstractShopkeeper getCurrentlyNamedShopkeeper(Player player) {
		assert player != null;
		return naming.get(player.getName());
	}

	boolean isNaming(Player player) {
		assert player != null;
		return this.getCurrentlyNamedShopkeeper(player) != null;
	}

	AbstractShopkeeper endNaming(Player player) {
		assert player != null;
		return naming.remove(player.getName());
	}

	// SHOPKEEPER CREATION:

	@Override
	public boolean hasCreatePermission(Player player) {
		if (player == null) return false;
		return (shopTypesRegistry.getSelection(player) != null) && (shopObjectTypesRegistry.getSelection(player) != null);
	}

	@Override
	public AbstractShopkeeper createShopkeeper(ShopCreationData creationData) {
		Validate.notNull(creationData, "CreationData is null!");
		try {
			// receives messages, can be null:
			Player creator = creationData.getCreator();
			ShopType<?> rawShopType = creationData.getShopType();
			Validate.isTrue(rawShopType instanceof AbstractShopType,
					"Expecting an AbstractShopType, got " + rawShopType.getClass().getName());
			AbstractShopType<?> shopType = (AbstractShopType<?>) rawShopType;

			// additional checks for player shops:
			// TODO move this into PlayerShopType
			if (shopType instanceof PlayerShopType) {
				Validate.isTrue(shopType instanceof AbstractPlayerShopType,
						"Expecting an AbstractPlayerShopType, got " + shopType.getClass().getName());
				Validate.isTrue(creationData instanceof PlayerShopCreationData,
						"Expecting PlayerShopCreationData, got " + creationData.getClass().getName());
				PlayerShopCreationData playerShopCreationData = (PlayerShopCreationData) creationData;

				// check if this chest is already used by some other shopkeeper:
				if (this.getProtectedChests().isChestProtected(playerShopCreationData.getShopChest(), null)) {
					Utils.sendMessage(creator, Settings.msgShopCreateFail);
					return null;
				}
				Player owner = playerShopCreationData.getOwner();
				Location spawnLocation = creationData.getSpawnLocation();

				// check worldguard:
				if (Settings.enableWorldGuardRestrictions) {
					if (!WorldGuardHandler.isShopAllowed(owner, spawnLocation)) {
						Utils.sendMessage(creator, Settings.msgShopCreateFail);
						return null;
					}
				}

				// check towny:
				if (Settings.enableTownyRestrictions) {
					if (!TownyHandler.isCommercialArea(spawnLocation)) {
						Utils.sendMessage(creator, Settings.msgShopCreateFail);
						return null;
					}
				}

				int maxShops = this.getMaxShops(owner);
				// call event:
				CreatePlayerShopkeeperEvent event = new CreatePlayerShopkeeperEvent(creationData, maxShops);
				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) {
					Log.debug("CreatePlayerShopkeeperEvent was cancelled!");
					return null;
				} else {
					maxShops = event.getMaxShopsForPlayer();
				}

				// count owned shops:
				if (maxShops > 0) {
					int count = shopkeeperRegistry.countShopsOfPlayer(owner);
					if (count >= maxShops) {
						Utils.sendMessage(creator, Settings.msgTooManyShops);
						return null;
					}
				}
			}

			// create and spawn the shopkeeper:
			AbstractShopkeeper shopkeeper = shopType.createShopkeeper(creationData);
			if (shopkeeper == null) {
				throw new ShopkeeperCreateException("ShopType returned null shopkeeper!");
			}
			assert shopkeeper != null;

			// run shopkeeper-created-event:
			Bukkit.getPluginManager().callEvent(new ShopkeeperCreatedEvent(creator, shopkeeper));

			// send creation message to creator:
			Utils.sendMessage(creator, shopType.getCreatedMessage());

			// save:
			shopkeeperStorage.save();

			return shopkeeper;
		} catch (ShopkeeperCreateException e) {
			Log.warning("Couldn't create shopkeeper: " + e.getMessage());
			return null;
		}
	}

	public int getMaxShops(Player player) {
		int maxShops = Settings.maxShopsPerPlayer;
		String[] maxShopsPermOptions = Settings.maxShopsPermOptions.replace(" ", "").split(",");
		for (String perm : maxShopsPermOptions) {
			if (Utils.hasPermission(player, "shopkeeper.maxshops." + perm)) {
				maxShops = Integer.parseInt(perm);
			}
		}
		return maxShops;
	}

	// INACTIVE SHOPS

	private void removeInactivePlayerShops() {
		if (Settings.playerShopkeeperInactiveDays <= 0) return;

		Set<UUID> playerUUIDs = new HashSet<>();
		for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
				playerUUIDs.add(playerShop.getOwnerUUID());
			}
		}
		if (playerUUIDs.isEmpty()) {
			// no player shops found:
			return;
		}

		// fetch OfflinePlayers async:
		int playerShopkeeperInactiveDays = Settings.playerShopkeeperInactiveDays;
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			List<OfflinePlayer> inactivePlayers = new ArrayList<>();
			long now = System.currentTimeMillis();
			for (UUID uuid : playerUUIDs) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
				if (!offlinePlayer.hasPlayedBefore()) continue;

				long lastPlayed = offlinePlayer.getLastPlayed();
				if ((lastPlayed > 0) && ((now - lastPlayed) / 86400000 > playerShopkeeperInactiveDays)) {
					inactivePlayers.add(offlinePlayer);
				}
			}

			if (inactivePlayers.isEmpty()) {
				// no inactive players found:
				return;
			}

			// continue in main thread:
			SchedulerUtils.runTaskOrOmit(SKShopkeepersPlugin.this, () -> {
				List<PlayerShopkeeper> forRemoval = new ArrayList<>();
				for (OfflinePlayer inactivePlayer : inactivePlayers) {
					// remove all shops of this inactive player:
					UUID playerUUID = inactivePlayer.getUniqueId();

					for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
						if (shopkeeper instanceof PlayerShopkeeper) {
							PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
							UUID ownerUUID = playerShop.getOwnerUUID();
							if (ownerUUID.equals(playerUUID)) {
								forRemoval.add(playerShop);
							}
						}
					}
				}

				// remove those shopkeepers:
				if (!forRemoval.isEmpty()) {
					for (PlayerShopkeeper shopkeeper : forRemoval) {
						shopkeeper.delete();
						Log.info("Shopkeeper owned by " + shopkeeper.getOwnerAsString() + " at "
								+ shopkeeper.getPositionString() + " has been removed for owner inactivity.");
					}

					// save:
					shopkeeperStorage.save();
				}
			});
		});
	}

	// HANDLING PLAYER NAME CHANGES:

	// updates owner names for the shopkeepers of the specified player:
	void updateShopkeepersForPlayer(UUID playerUUID, String playerName) {
		boolean dirty = false;
		for (Shopkeeper shopkeeper : shopkeeperRegistry.getAllShopkeepers()) {
			if (shopkeeper instanceof PlayerShopkeeper) {
				PlayerShopkeeper playerShop = (PlayerShopkeeper) shopkeeper;
				UUID ownerUUID = playerShop.getOwnerUUID();
				String ownerName = playerShop.getOwnerName();

				if (ownerUUID.equals(playerUUID)) {
					if (!ownerName.equals(playerName)) {
						// update the stored name, because the player must have changed it:
						playerShop.setOwner(playerUUID, playerName);
						dirty = true;
					} else {
						// The shop was already updated to uuid based identification and the player's name hasn't
						// changed.
						// If we assume that this is consistent among all shops of this player
						// we can stop checking the other shops here:
						return;
					}
				}
			}
		}

		if (dirty) {
			shopkeeperStorage.save();
		}
	}

	private static class ConfirmEntry {

		private final Runnable action;
		private final int taskId;

		public ConfirmEntry(Runnable action, int taskId) {
			this.taskId = taskId;
			this.action = action;
		}

		public int getTaskId() {
			return taskId;
		}

		public Runnable getAction() {
			return action;
		}
	}

	@Override
	public TradingRecipe createTradingRecipe(ItemStack resultItem, ItemStack item1, ItemStack item2) {
		return new SKTradingRecipe(resultItem, item1, item2);
	}
}