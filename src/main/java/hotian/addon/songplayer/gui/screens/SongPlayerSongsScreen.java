package hotian.addon.songplayer.gui.screens;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import hotian.addon.songplayer.songplayer.SongPlayer;
import hotian.addon.songplayer.songplayer.playing.SongHandler;
import hotian.addon.songplayer.songplayer.Util;
import hotian.addon.songplayer.songplayer.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

public class SongPlayerSongsScreen extends WindowScreen {
    private WTextBox filter;
    private String filterText = "";
    private WTable table;
    
    // Current directory path
    private Path currentDir;
    
    // Directory navigation stack
    private Stack<Path> dirStack = new Stack<>();

    public SongPlayerSongsScreen(GuiTheme theme) {
        super(theme, "SongPlayer Songs");
        this.currentDir = SongPlayer.SONG_DIR;
    }

    @Override
    public void initWidgets() {
        // Back button
        if (!currentDir.equals(SongPlayer.SONG_DIR)) {
            WButton back = add(theme.button("Back")).widget();
            back.action = this::goBack;
            add(theme.horizontalSeparator()).expandX();
        }
        
        // Current directory label
        add(theme.label("Current directory: " + getCurrentDirName())).expandX();
        add(theme.horizontalSeparator()).expandX();

        // Filter
        filter = add(theme.textBox("", "Search for the songs...")).minWidth(400).expandX().widget();
        filter.setFocused(true);
        filter.action = () -> {
            filterText = filter.get().trim();

            table.clear();
            initSongsTable();
        };

        table = add(theme.table()).widget();

        initSongsTable();
        
        // Stop button
        add(theme.horizontalSeparator()).expandX();
        WButton stop = add(theme.button("Stop")).expandX().widget();
        stop.action = this::stopPlayback;
    }
    
    // Go back to parent directory
    private void goBack() {
        if (!currentDir.equals(SongPlayer.SONG_DIR)) {
            currentDir = currentDir.getParent();
            reloadScreen();
        }
    }
    
    // Reload the screen
    private void reloadScreen() {
        clear();
        initWidgets();
    }
    
    // Get current directory name
    private String getCurrentDirName() {
        if (currentDir.equals(SongPlayer.SONG_DIR)) {
            return "/";
        }
        return currentDir.getFileName().toString();
    }

    private void initSongsTable() {
        AtomicBoolean noSongsFound = new AtomicBoolean(true);
        try {
            Files.list(currentDir).forEach(path -> {
                if (Files.isRegularFile(path)) {
                    String name = path.getFileName().toString();

                    if (name.toLowerCase().contains(filterText.toLowerCase())) {
                        addPath(path);
                        noSongsFound.set(false);
                    }
                } else if (Files.isDirectory(path)) {
                    // Add directory entry
                    addDirectoryPath(path);
                    noSongsFound.set(false);
                }
            });
        } catch (IOException e) {
            table.add(theme.label("Error reading directory.")).expandCellX();
            table.row();
        }

        if (noSongsFound.get() && filterText.isEmpty()) {
            table.add(theme.label("No songs found.")).expandCellX().center();
        } else if (noSongsFound.get()) {
            table.add(theme.label("No songs found for \"" + filterText + "\".")).expandCellX().center();
        }
    }

    private void addDirectoryPath(Path path) {
        table.add(theme.horizontalSeparator()).expandX().minWidth(400);
        table.row();

        table.add(theme.label("📁 " + path.getFileName().toString() + "/")).expandCellX();
        WButton open = table.add(theme.button("Open")).right().widget();
        open.action = () -> {
            currentDir = path;
            reloadScreen();
        };

        table.row();
    }

    private void addPath(Path path) {
        table.add(theme.horizontalSeparator()).expandX().minWidth(400);
        table.row();

        table.add(theme.label(path.getFileName().toString())).expandCellX();
        WButton play = table.add(theme.button("Play")).right().widget();
        play.action = () -> {
            // Get the file name relative to the songs directory
            try {
                Path songsDir = SongPlayer.SONG_DIR.toAbsolutePath();
                Path absolutePath = path.toAbsolutePath();
                Path relativePath = songsDir.relativize(absolutePath);
                SongHandler.getInstance().loadSong(relativePath.toString());
                Util.showChatMessage("§6Playing song: §3" + relativePath.toString());
            } catch (Exception e) {
                // Fallback to using the file name directly
                String fileName = path.getFileName().toString();
                SongHandler.getInstance().loadSong(fileName);
                Util.showChatMessage("§6Playing song: §3" + fileName);
            }
        };

        table.row();
    }
    
    // Stop playback using the same logic as the "stop" command
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
}