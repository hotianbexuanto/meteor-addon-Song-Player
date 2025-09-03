package hotian.addon.songplayer.modules;

import hotian.addon.songplayer.SongPlayerAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.CommandProcessor;
import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Config;

public class SongPlayerModule extends Module {
    public SongPlayerModule() {
        super(SongPlayerAddon.CATEGORY, "Song Player", "A module to play songs with noteblocks.");
    }

    @Override
    public void onActivate() {
        // Initialize SongPlayer when the module is activated
        // This ensures the SongPlayer system is ready to handle play requests
    }

    @Override
    public void onDeactivate() {
        // Clean up when the module is deactivated
        SongHandler.getInstance().reset();
    }
}