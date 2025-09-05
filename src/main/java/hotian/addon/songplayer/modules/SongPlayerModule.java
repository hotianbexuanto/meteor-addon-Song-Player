package hotian.addon.songplayer.modules;

import hotian.addon.songplayer.SongPlayerAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.CommandProcessor;
import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Config;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import hotian.addon.songplayer.gui.screens.SongPlayerSongsScreen;
import hotian.addon.songplayer.songplayer.Util;

public class SongPlayerModule extends Module {
    public SongPlayerModule() {
        super(SongPlayerAddon.CATEGORY, "song-player", "A module to play songs with noteblocks.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();
        
        // Button to open song selection GUI
        WButton openSongGUI = list.add(theme.button("Open Song GUI")).widget();
        openSongGUI.action = () -> {
            mc.setScreen(new SongPlayerSongsScreen(theme));
        };
        
        // Button to stop the currently playing song
        WButton stopButton = list.add(theme.button("Stop")).widget();
        stopButton.action = this::stopPlayback;
        
        return list;
    }
    
    // Stop the currently playing song using the same logic as the "stop" command
    private void stopPlayback() {
        if (!SongHandler.getInstance().isIdle()) {
            // This replicates the logic from CommandProcessor.stopCommand
            if (SongHandler.getInstance().cleaningUp) {
                SongHandler.getInstance().restoreStateAndReset();
                Util.showChatMessage("§6Stopped cleanup");
            } else if (Config.getConfig().autoCleanup && SongHandler.getInstance().originalBlocks.size() != 0) {
                SongHandler.getInstance().partialResetAndCleanup();
                Util.showChatMessage("§6Stopped playback and switched to cleanup mode");
            } else {
                SongHandler.getInstance().restoreStateAndReset();
                Util.showChatMessage("§6Stopped playback");
            }
        } else {
            Util.showChatMessage("§6No song is currently playing");
        }
    }

    @Override
    public void onActivate() {
        // No action needed when activating the module
    }

    @Override
    public void onDeactivate() {
        // Stop the song when the module is deactivated using the same logic as the "stop" command
        if (!SongHandler.getInstance().isIdle()) {
            // This replicates the logic from CommandProcessor.stopCommand
            if (SongHandler.getInstance().cleaningUp) {
                SongHandler.getInstance().restoreStateAndReset();
            } else if (Config.getConfig().autoCleanup && SongHandler.getInstance().originalBlocks.size() != 0) {
                SongHandler.getInstance().partialResetAndCleanup();
            } else {
                SongHandler.getInstance().restoreStateAndReset();
            }
        }
    }
}