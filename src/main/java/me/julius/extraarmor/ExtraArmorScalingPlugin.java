package me.julius.extraarmor;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExtraArmorScalingPlugin extends JavaPlugin implements Listener {

    private double vanillaArmorCap;
    private double k;
    private double maxExtraReduction;
    private boolean affectMobs;
    private boolean debug;
    private Set<EntityDamageEvent.DamageCause> applyTo;
    private Set<EntityDamageEvent.DamageCause> ignore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ExtraArmorScaling enabled");
    }

    private void loadSettings() {
        FileConfiguration cfg = getConfig();
        vanillaArmorCap = cfg.getDouble("vanilla_armor_cap", 20.0);
        k = cfg.getDouble("k", 80.0);
        maxExtraReduction = cfg.getDouble("max_extra_reduction", 0.60);
        affectMobs = cfg.getBoolean("affect_mobs", false);
        debug = cfg.getBoolean("debug", false);

        applyTo = parseCauses(cfg.getStringList("apply_to_causes"));
        ignore = parseCauses(cfg.getStringList("ignore_causes"));
    }

    private Set<EntityDamageEvent.DamageCause> parseCauses(List<String> vals) {
        Set<EntityDamageEvent.DamageCause> out = new HashSet<>();
        for (String s : vals) {
            try {
                out.add(EntityDamageEvent.DamageCause.valueOf(s.trim().toUpperCase()));
            } catch (Exception ignored) {
                getLogger().warning("Unknown damage cause in config: " + s);
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }

        if (!affectMobs && !(living instanceof Player)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (ignore.contains(cause)) {
            return;
        }

        if (!applyTo.isEmpty() && !applyTo.contains(cause)) {
            return;
        }

        AttributeInstance armorAttr = living.getAttribute(Attribute.GENERIC_ARMOR);
        if (armorAttr == null) {
            return;
        }

        double totalArmor = armorAttr.getValue();
        double extraArmor = Math.max(0.0, totalArmor - vanillaArmorCap);

        if (extraArmor <= 0.0) {
            return;
        }

        // Diminishing returns extra reduction
        double extraReduction = (extraArmor / (extraArmor + k)) * maxExtraReduction;

        // Safety clamps
        if (extraReduction < 0.0) {
            extraReduction = 0.0;
        }
        if (extraReduction > maxExtraReduction) {
            extraReduction = maxExtraReduction;
        }

        double oldFinal = event.getFinalDamage();
        double newFinal = oldFinal * (1.0 - extraReduction);

        if (newFinal < 0.0) {
            newFinal = 0.0;
        }

        event.setDamage(newFinal);

        if (debug) {
            String name = (living instanceof Player p) ? p.getName() : living.getType().name();
            getLogger().info("Damage cause=" + cause + " target=" + name + " armor=" + totalArmor + " extraArmor=" + extraArmor
                    + " extraReduction=" + extraReduction + " oldFinal=" + oldFinal + " newFinal=" + newFinal);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("extraarmor")) {
            return false;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("extraarmor.admin")) {
                sender.sendMessage("No permission");
                return true;
            }
            reloadConfig();
            loadSettings();
            sender.sendMessage("ExtraArmorScaling reloaded");
            return true;
        }
        sender.sendMessage("Usage: /extraarmor reload");
        return true;
    }
}
