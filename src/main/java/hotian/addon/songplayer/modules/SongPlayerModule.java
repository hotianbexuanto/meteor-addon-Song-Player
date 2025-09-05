package hotian.addon.songplayer.modules;

import hotian.addon.songplayer.SongPlayerAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.CommandProcessor;
import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.Config;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import hotian.addon.songplayer.gui.screens.SongPlayerSongsScreen;

public class SongPlayerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> songName = sgGeneral.add(new ProvidedStringSetting.Builder()
            .name("song-name")
            .description("The name of the song to play")
            .defaultValue("")
            .supplier(this::getAvailableSongsArray)
            .build()
    );

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
        
        // Button to play the selected song
        WButton playButton = list.add(theme.button("Play")).widget();
        playButton.action = this::playSelectedSong;
        
        return list;
    }
    
    // Play the selected song
    private void playSelectedSong() {
        if (!songName.get().isEmpty()) {
            SongHandler.getInstance().loadSong(songName.get());
        }
    }

    @Override
    public void onActivate() {
        // Play the song when the module is activated
        if (!songName.get().isEmpty()) {
            SongHandler.getInstance().loadSong(songName.get());
        }
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
    
    // Get list of available songs from the songs directory as an array
    private String[] getAvailableSongsArray() {
        return getAvailableSongs().toArray(new String[0]);
    }
    
    // Get list of available songs from the songs directory
    public static java.util.List<String> getAvailableSongs() {
        try {
            if (!java.nio.file.Files.exists(SongPlayer.SONG_DIR)) {
                return new java.util.ArrayList<>();
            }
            
            return java.nio.file.Files.list(SongPlayer.SONG_DIR)
                    .filter(java.nio.file.Files::isRegularFile)
                    .map(java.nio.file.Path::getFileName)
                    .map(java.nio.file.Path::toString)
                    .collect(java.util.stream.Collectors.toList());
        } catch (java.io.IOException e) {
            return new java.util.ArrayList<>();
        }
    }
}