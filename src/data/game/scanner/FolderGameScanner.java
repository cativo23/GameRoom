package data.game.scanner;

import data.game.GameWatcher;
import data.game.entry.AllGameEntries;
import data.game.entry.GameEntry;
import system.application.settings.PredefinedSetting;
import ui.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static data.game.GameWatcher.cleanNameForCompareason;
import static ui.Main.GENERAL_SETTINGS;
import static ui.Main.MAIN_SCENE;

/**
 * Created by LM on 19/08/2016.
 */
public class FolderGameScanner extends GameScanner {
    public final static String[] EXCLUDED_FILE_NAMES = new String[]{"Steam Library","SteamLibrary","SteamVR","!Downloads"};
    private final static String[] VALID_EXECUTABLE_EXTENSION = new String[]{".exe", ".lnk", ".jar"};


    public FolderGameScanner(GameWatcher parentLooker) {
        super(parentLooker);
    }

    @Override
    public void scanForGames() {
        foundGames.clear();

        ArrayList<GameEntry> potentialEntries = getPotentialEntries();

        for (GameEntry potentialEntry : potentialEntries) {
            boolean gameAlreadyInLibrary = gameAlreadyInLibrary(potentialEntry);
            boolean folderGameIgnored = folderGameIgnored(potentialEntry);
            boolean alreadyWaitingToBeAdded = parentLooker.alreadyWaitingToBeAdded(potentialEntry);
            if (!gameAlreadyInLibrary
                    && !folderGameIgnored
                    && !alreadyWaitingToBeAdded) {
                addGameEntryFound(potentialEntry);
            }else if(gameAlreadyInLibrary || alreadyWaitingToBeAdded){
                compareAndSetLauncherId(potentialEntry);
            }
        }
        if (foundGames.size() > 0) {
            Main.LOGGER.info(this.getClass().getName() + " : found = " + foundGames.size());
        }
    }

    private boolean folderGameIgnored(GameEntry entry) {
        boolean ignored = false;
        for (File ignoredFile : Main.GENERAL_SETTINGS.getFiles(PredefinedSetting.IGNORED_GAME_FOLDERS)) {
            ignored = ignoredFile.toPath().toAbsolutePath().toString().toLowerCase().contains(entry.getPath().toLowerCase());
            if (ignored) {
                return true;
            }
        }
        return false;
    }

    ArrayList<GameEntry> getPotentialEntries() {
        File gamesFolder = new File(GENERAL_SETTINGS.getString(PredefinedSetting.GAMES_FOLDER));
        ArrayList<GameEntry> entriesFound = new ArrayList<>();
        if (!gamesFolder.exists() || !gamesFolder.isDirectory()) {
            return entriesFound;
        }
        for (File file : gamesFolder.listFiles()) {
            if (isPotentiallyAGame(file)) {
                GameEntry potentialEntry = new GameEntry(file.getName());
                potentialEntry.setPath(file.getAbsolutePath());
                potentialEntry.setNotInstalled(false);
                entriesFound.add(potentialEntry);
            }
        }
        return entriesFound;
    }

    public static boolean isPotentiallyAGame(File file) {
        for (String excludedName : EXCLUDED_FILE_NAMES) {
            if (file.getName().equals(excludedName)) {
                return false;
            }
        }
        if (file.isDirectory()) {
            boolean potentialGame = false;
            for (File subFile : file.listFiles()) {
                potentialGame = potentialGame || isPotentiallyAGame(subFile);
            }
            return potentialGame;
        } else {
            return fileHasValidExtension(file);
        }
    }

    public static boolean fileHasValidExtension(File file) {
        boolean hasAValidExtension = false;
        for (String validExtension : VALID_EXECUTABLE_EXTENSION) {
            hasAValidExtension = hasAValidExtension || file.getAbsolutePath().toLowerCase().endsWith(validExtension.toLowerCase());
        }
        return hasAValidExtension;
    }

    private boolean gameAlreadyInLibrary(GameEntry foundEntry) {
        boolean alreadyAddedToLibrary = false;
        for (GameEntry entry : AllGameEntries.ENTRIES_LIST) {
            alreadyAddedToLibrary = entry.getPath().toLowerCase().trim().contains(foundEntry.getPath().trim().toLowerCase())
                    || foundEntry.getPath().trim().toLowerCase().contains(entry.getPath().trim().toLowerCase())
                    || cleanNameForCompareason(foundEntry.getName()).equals(cleanNameForCompareason(entry.getName())); //cannot use UUID as they are different at this pre-add-time
            if (alreadyAddedToLibrary) {
                break;
            }
        }
        return alreadyAddedToLibrary;
    }

    private void compareAndSetLauncherId(GameEntry foundEntry){
        List<GameEntry> toAddAndLibEntries = AllGameEntries.ENTRIES_LIST;
        toAddAndLibEntries.addAll(parentLooker.getEntriesToAdd());

        for (GameEntry entry : toAddAndLibEntries) {
            boolean alreadyAddedToLibrary = entry.getPath().toLowerCase().trim().contains(foundEntry.getPath().trim().toLowerCase())
                    || foundEntry.getPath().trim().toLowerCase().contains(entry.getPath().trim().toLowerCase())
                    || cleanNameForCompareason(foundEntry.getName()).equals(cleanNameForCompareason(entry.getName())); //cannot use UUID as they are different at this pre-add-time
            if (alreadyAddedToLibrary) {
                //TODO replace launchers with an enum and an id ?
                boolean needRefresh = false;
                if(!entry.isSteamGame() && foundEntry.isSteamGame()){
                    entry.setSteam_id(foundEntry.getSteam_id());
                    needRefresh = true;
                }
                if(!entry.isBattlenetGame() && foundEntry.isBattlenetGame()){
                    entry.setBattlenet_id(foundEntry.getBattlenet_id());
                    needRefresh = true;
                }
                if(!entry.isGoGGame() && foundEntry.isGoGGame()){
                    entry.setGog_id(foundEntry.getGog_id());
                    needRefresh = true;
                }
                if(!entry.isOriginGame() && foundEntry.isOriginGame()){
                    entry.setOrigin_id(foundEntry.getOrigin_id());
                    needRefresh = true;
                }
                if(!entry.isUplayGame() && foundEntry.isUplayGame()){
                    entry.setUplay_id(foundEntry.getUplay_id());
                    needRefresh = true;
                }
                if(needRefresh){
                    if(MAIN_SCENE!=null){
                        MAIN_SCENE.updateGame(entry);
                    }
                }
                break;
            }
        }
    }
}
