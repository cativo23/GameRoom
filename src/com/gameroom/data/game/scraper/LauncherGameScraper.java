package com.gameroom.data.game.scraper;

import com.gameroom.data.game.GameWatcher;
import com.gameroom.data.game.entry.GameEntry;
import com.gameroom.data.game.entry.GameEntryUtils;
import com.gameroom.data.game.entry.Platform;
import com.gameroom.data.game.scanner.FolderGameScanner;
import com.gameroom.data.game.scanner.GameScanner;
import com.gameroom.data.game.scanner.ScanTask;
import com.gameroom.data.io.FileUtils;
import com.gameroom.system.os.Terminal;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.gameroom.ui.Main.LOGGER;

/**
 * Created by LM on 29/08/2016.
 */
public class LauncherGameScraper {

    public static void scanInstalledGames(GameScanner scanner) {
        if (scanner.getScannerProfile() == null) {
            return;
        }
        switch (scanner.getScannerProfile()) {
            case GOG:
                scanGOGGames(scanner);
                break;
            case UPLAY:
                scanUplayGames(scanner);
                break;
            case ORIGIN:
                scanOriginGames(scanner);
                break;
            case BATTLE_NET:
                scanBattleNetGames(scanner);
                break;
            case STEAM:
                scanSteamGames(scanner);
                break;
            case STEAM_ONLINE:
                SteamOnlineScraper.checkIfCanScanSteam(false);
                scanSteamOnlineGames(scanner);
                break;
            case MICROSOFT_STORE:
                scanMSStoreGames(scanner);
                break;
            default:
                break;
        }
    }

    private static void scanSteamGames(GameScanner scanner) {
        SteamLocalScraper.scanSteamGames(scanner);
    }

    private static void scanSteamOnlineGames(GameScanner scanner) {
        SteamOnlineScraper.scanSteamOnlineGames(scanner);
    }

    private static void scanInstalledGames(String regFolder, String installDirPrefix, String namePrefix, GameScanner scanner) {
        Terminal terminal = new Terminal(false);
        String[] output = new String[0];
        try {
            output = terminal.execute("reg", "query", '"' + regFolder + '"');
            for (String s : output) {
                if (s.contains(regFolder)) {
                    ScanTask task = new ScanTask(scanner, () -> {
                        int index = s.indexOf(regFolder) + regFolder.length() + 1;
                        String subFolder = s.substring(index);

                        String[] subOutPut = terminal.execute("reg", "query", '"' + regFolder + "\\" + subFolder + '"');
                        String installDir = null;
                        String name = null;

                        boolean notAGame = false; //this is to detect GOG non games
                        for (String s2 : subOutPut) {
                            if (s2.contains(installDirPrefix)) {
                                int index2 = s2.indexOf(installDirPrefix) + installDirPrefix.length();
                                installDir = s2.substring(index2).replace("/", "\\").replace("\"", "");
                                ;
                            } else if (namePrefix != null && s2.contains(namePrefix)) {
                                int index2 = s2.indexOf(namePrefix) + namePrefix.length();
                                name = s2.substring(index2).replace("®", "").replace("™", "");
                            } else if (s2.contains("DEPENDSON    REG_SZ    ")) {
                                notAGame = !s2.equals("    DEPENDSON    REG_SZ    ");
                            }
                        }
                        if (installDir != null && !notAGame) {
                            File file = new File(installDir);
                            if (file.exists()) {
                                if (name == null) {
                                    name = file.isDirectory() ? file.getName() : new File(file.getParent()).getName();
                                }
                                GameEntry potentialEntry = new GameEntry(name);
                                potentialEntry.setPath(file.getAbsolutePath());
                                potentialEntry.setInstalled(true);

                                int id = 0;
                                try {
                                    id = Integer.parseInt(subFolder);
                                    if (id == -1) {
                                        id = 0;
                                    }
                                } catch (NumberFormatException nfe) {
                                    //no id to scrap!
                                }
                                potentialEntry.setPlatform(scanner.getPlatform());
                                scanner.checkAndAdd(potentialEntry);
                            }
                        }
                        return null;
                    });
                    GameWatcher.getInstance().submitTask(task);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void scanUplayGames(GameScanner scanner) {
        scanInstalledGames("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs", "InstallDir    REG_SZ    ", null, scanner);
    }

    private static void scanGOGGames(GameScanner scanner) {
        scanInstalledGames("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\GOG.com\\Games", "EXE    REG_SZ    ", "GAMENAME    REG_SZ    ", scanner);
        String keyWord = "GOG.com";
        String[] excludedNames = new String[]{"GOG Galaxy"};
        scanUninstallReg(scanner, keyWord, excludedNames);
    }

    private static void scanOriginGames(GameScanner scanner) {
        scanInstalledGames("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\EA Games", "Install Dir    REG_SZ    ", "DisplayName    REG_SZ    ", scanner);
        scanInstalledGames("HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Electronic Arts", "Install Dir    REG_SZ    ", "DisplayName    REG_SZ    ", scanner);
        scanUXGamesForOrigin(scanner);

        String keyWord = "Support\\EA Help";
        String[] excludedNames = new String[]{"Origin"};
        scanUninstallReg(scanner, keyWord, excludedNames);
    }

    private static void scanMSStoreGames(GameScanner scanner) {
        MSStoreScraper.getApps(msStoreEntry -> {
            boolean invalid =  msStoreEntry.isInGameEntryCollection(GameEntryUtils.ENTRIES_LIST)
                    || msStoreEntry.isInGameEntryCollection(GameEntryUtils.IGNORED_ENTRIES);
            if(!invalid){
                ScanTask task = new ScanTask(scanner,() -> {
                    LOGGER.debug("MICROSOFT_STORE potential entry: "+msStoreEntry.getName());
                    GameEntry gameEntry = MSStoreScraper.shouldConsiderGame(msStoreEntry);
                    if (gameEntry != null) {
                        gameEntry.setToAdd(true);
                        gameEntry.setPath(msStoreEntry.getStartCommand());
                        gameEntry.setPlatform(Platform.MICROSOFT_STORE_ID);
                        gameEntry.setMonitorProcess(msStoreEntry.getExecutableFilePath());
                    /*if (msStoreEntry.getIconPath() != null) {
                        try {
                            File tempFile = msStoreEntry.getIconTempCopy();
                            if (tempFile == null) {
                                tempFile = new File(msStoreEntry.getIconPath());
                            }
                            gameEntry.updateImage(0, tempFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }*/
                        scanner.checkAndAdd(gameEntry);
                    }
                    return null;
                });
                GameWatcher.getInstance().submitTask(task);
            }

        });
    }

    private static void scanUXGamesForOrigin(GameScanner scanner) {
        String regFolder = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\GameUX\\Games";
        String pathPrefix = "AppExePath    REG_SZ";
        String namePrefix = "Title    REG_SZ";
        String rootPrefix = "ConfigApplicationPath    REG_SZ";

        try {
            Terminal terminal = new Terminal(false);
            String[] output = terminal.execute("reg", "query", regFolder);
            for (String line : output) {
                if (line.startsWith(regFolder)) {
                    ScanTask task = new ScanTask(scanner, () -> {
                        String[] gameRegOutput = terminal.execute("reg", "query", line);

                        String name = null;
                        String path = null;
                        String root = null;
                        for (String propLine : gameRegOutput) {
                            if (propLine.contains(pathPrefix)) {
                                path = getValue(pathPrefix, propLine).replace("\"", "");
                                ;
                            }
                            if (propLine.contains(namePrefix)) {
                                name = getValue(namePrefix, propLine);
                            }
                            if (propLine.contains(rootPrefix)) {
                                root = getValue(rootPrefix, propLine).replace("\"", "");
                                ;
                            }
                        }
                        if (name != null && path != null) {
                            GameEntry entry = new GameEntry(name);
                            entry.setPath(path);
                            if (gameIsOrigin(root)) {
                                entry.setPlatform(Platform.ORIGIN_ID);
                                scanner.checkAndAdd(entry);
                            }
                        }
                        return null;
                    });
                    GameWatcher.getInstance().submitTask(task);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks the existence of the Origin's typical folder "Support\EA Help"
     *
     * @param gameRoot
     * @return
     */
    private static boolean gameIsOrigin(String gameRoot) {
        if (gameRoot == null) {
            return false;
        }

        File root = new File(gameRoot);
        if (!root.exists()) {
            return false;
        }
        File eaHelp = new File(root.getAbsolutePath() + File.separator + "Support" + File.separator + "EA Help");
        return eaHelp.exists();
    }


    private static void scanBattleNetGames(GameScanner scanner) {
        String keyword = "Battle.net\\Agent\\Blizzard Uninstaller.exe";
        String[] excludedNames = new String[]{"Battle.net"};

        scanUninstallReg(scanner, keyword, excludedNames);
    }

    private static void scanUninstallReg(GameScanner scanner, String keyWord, String[] excludedNames) {
        String regFolder = "HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
        Terminal terminal = new Terminal(false);
        try {
            String[] output = terminal.execute("reg", "query", '"' + regFolder + '"', "/s", "/f", '"' + keyWord + '"');
            List<String> linesToProcess = Arrays.stream(output).filter(s -> s.startsWith(regFolder)).collect(Collectors.toList());
            linesToProcess.forEach(line -> {
                ScanTask task = new ScanTask(scanner, () -> {
                    String appCode = line.substring(regFolder.length() + 1); //+1 for the \

                    boolean excluded = false;
                    for (String excludedName : excludedNames) {
                        excluded = appCode.equals(excludedName);
                        if (excluded) {
                            break;
                        }
                    }
                    if (!excluded) {
                        String[] gameRegOutput = terminal.execute("reg", "query", '"' + regFolder + '\\' + appCode + '"');
                        String pathPrefix = "DisplayIcon    REG_SZ";
                        String rootPrefix = "InstallLocation    REG_SZ";
                        String namePrefix = "DisplayName    REG_SZ";
                        String path = null;
                        String name = appCode;

                        for (String s : gameRegOutput) {
                            if (s.contains(pathPrefix)) {
                                String tempPath = getValue(pathPrefix, s).replace("\"", "");
                                if (FolderGameScanner.fileHasValidExtension(new File(tempPath))) {
                                    path = tempPath;
                                }
                                break;
                            }
                            if (s.contains(rootPrefix)) {
                                if (path == null) {
                                    path = getValue(rootPrefix, s).replace("\"", "");
                                }
                            }
                            if (s.contains(namePrefix)) {
                                name = getValue(namePrefix, s);
                            }
                        }
                        String resolvedPath = FileUtils.tryResolveLnk(path);
                        if (resolvedPath != null && FolderGameScanner.isPotentiallyAGame(new File(resolvedPath))) {
                            GameEntry entry = new GameEntry(name);
                            entry.setPath(resolvedPath);

                            entry.setPlatform(scanner.getPlatform());

                            entry.setInstalled(true);
                            scanner.checkAndAdd(entry);
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    return null;
                });
                GameWatcher.getInstance().submitTask(task);

            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getValue(String prefix, String line) {
        if (prefix == null || line == null) {
            return null;
        }
        return line.substring(line.indexOf(prefix) + prefix.length()).trim();
    }
}
