package LoLip_p.blockDisplayVanillaMaker;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabExecutor;

import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BlockDisplayVanillaMaker extends JavaPlugin implements TabExecutor, Listener {

    private String requiredTitle;
    private boolean useRequiredTitle;
    private double maxDistance;
    private double defaultDistance;

    private static final String PERM_CREATE = "bdvm.create";
    private static final String PERM_REMOVE = "bdvm.remove";
    private static final String PERM_RELOAD = "bdvm.reload";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        useRequiredTitle = getConfig().getBoolean("useRequiredTitle", true);
        requiredTitle = getConfig().getString("requiredTitle", "BDModel");
        maxDistance = getConfig().getDouble("maxDistance", 0.5);
        defaultDistance = getConfig().getDouble("defaultDistance", 2.0);

        Objects.requireNonNull(this.getCommand("bdvm")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("bdvm")).setTabCompleter(this);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BDVanillaMaker enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BDVanillaMaker disabled.");
    }

    private void loadSettings() {
        useRequiredTitle = getConfig().getBoolean("useRequiredTitle", true);
        requiredTitle = getConfig().getString("requiredTitle", "BDModel");
        maxDistance = getConfig().getDouble("maxDistance", 3.0);
        defaultDistance = getConfig().getDouble("defaultDistance", 2.0);
    }

    private String getMsg(String key, Object... args) {
        String raw = getConfig().getString("messages." + key, key);
        return args.length > 0 ? String.format(raw, args) : raw;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMsg("onlyPlayers"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(getMsg("usage"));
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "create" -> {
                if (!player.hasPermission(PERM_CREATE)) {
                    player.sendMessage(getMsg("noPermissionCreate"));
                    yield true;
                }
                yield handleCreate(player);
            }
            case "remove" -> {
                if (!player.hasPermission(PERM_REMOVE)) {
                    player.sendMessage(getMsg("noPermissionRemove"));
                    yield true;
                }
                yield handleRemove(player, args);
            }
            case "reload" -> {
                if (!player.hasPermission(PERM_RELOAD)) {
                    player.sendMessage(getMsg("noPermissionReload"));
                    yield true;
                }
                yield handleReload(player);
            }
            default -> {
                player.sendMessage(getMsg("unknownSubcommand"));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("bdvm")) return null;
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return Arrays.asList(
                    String.format(Locale.US, "%.2f", maxDistance/4),
                    String.format(Locale.US, "%.2f", maxDistance/2),
                    String.format(Locale.US, "%.2f", maxDistance)
            );
        }
        return List.of();
    }

    private boolean handleCreate(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.WRITTEN_BOOK) {
            player.sendMessage(getMsg("mustHoldBook"));
            return true;
        }

        BookMeta meta = (BookMeta) item.getItemMeta();
        if (useRequiredTitle) {
            if (meta == null || !requiredTitle.equals(meta.getTitle())) {
                player.sendMessage(getMsg("invalidBookTitle", requiredTitle));
                return true;
            }
        }

        StringBuilder fullText = new StringBuilder();
        for (String page : meta.getPages()) {
            fullText.append(page);
        }

        String commandLine = fullText.toString().trim();
        commandLine = commandLine.replaceAll("[\\r\\n\\t]+", " ").trim();

        if (!commandLine.startsWith("/summon block_display")) {
            player.sendMessage(getMsg("invalidCommandStart"));
            return true;
        }

        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        String coordString = String.format("%.2f %.2f %.2f", x, y, z);
        commandLine = commandLine.replace("~-0.5 ~-0.5 ~-0.5", coordString);

        String cmdToRun = commandLine.startsWith("/") ? commandLine.substring(1) : commandLine;
        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdToRun);
        if (!success) {
            player.sendMessage(getMsg("failedToRun", commandLine));
        } else {
            player.sendMessage(getMsg("successfullyCreated"));
        }
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        double dist = defaultDistance;
        if (args.length >= 2) {
            try {
                double requested = Double.parseDouble(args[1]);
                if (requested <= maxDistance && requested >= 0) {
                    dist = requested;
                } else {
                    player.sendMessage(getMsg("maxDistanceExceeded", maxDistance));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(getMsg("invalidNumber"));
                return true;
            }
        }

        String d = String.format(Locale.US, "%.2f", dist);
        String playerName = player.getName();

        // Выполняем три команды и отслеживаем, сработала ли хоть одна
        boolean anySuccess = false;
        anySuccess |= Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "execute at " + playerName + " run kill @e[type=minecraft:block_display,distance=.." + d + "]");
        anySuccess |= Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "execute at " + playerName + " run kill @e[type=minecraft:text_display,distance=.." + d + "]");
        anySuccess |= Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "execute at " + playerName + " run kill @e[type=minecraft:item_display,distance=.." + d + "]");

        // Если хотя бы одна команда удалила сущности — показываем сообщение об успехе
        if (anySuccess) {
            player.sendMessage(getMsg("removeSuccess"));
        }
        return true;
    }

    private boolean handleReload(Player player) {
        reloadConfig();
        loadSettings();
        player.sendMessage(getMsg("configReloaded"));
        getLogger().info("Config reloaded by " + player.getName());
        return true;
    }
}
