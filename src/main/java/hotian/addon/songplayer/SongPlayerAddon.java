package hotian.addon.songplayer;

import hotian.addon.songplayer.commands.CommandExample;
import hotian.addon.songplayer.commands.SongPlayerCommand;
import hotian.addon.songplayer.hud.HudExample;
import hotian.addon.songplayer.modules.ModuleExample;
import hotian.addon.songplayer.modules.SongPlayerModule;
import hotian.addon.songplayer.songplayer.SongPlayer;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class SongPlayerAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Song Player");
    public static final HudGroup HUD_GROUP = new HudGroup("Song Player");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Song Player Addon");

        // Initialize SongPlayer
        SongPlayer songPlayer = new SongPlayer();
        songPlayer.onInitialize();

        // Modules
        Modules.get().add(new ModuleExample());
        Modules.get().add(new SongPlayerModule());

        // Commands
        Commands.add(new CommandExample());
        Commands.add(new SongPlayerCommand());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "hotian.addon.songplayer";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("hotian", "meteor-addon-songplayer");
    }
}
