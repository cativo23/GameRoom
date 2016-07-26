import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import system.device.ControllerButtonListener;
import system.device.XboxController;
import ui.Main;
import ui.scene.MainScene;
import ui.scene.SettingsScene;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

import static ui.Main.*;

/**
 * Created by LM on 15/07/2016.
 */
public class Launcher extends Application {
    private int trayMessageCount = 0;

    public static void main(String[] args) {
        //setCurrentProcessExplicitAppUserModelID("GameRoom");

        if (args.length > 0) {
            Main.DEV_MODE = args[0].equals("dev");
        }
        Main.main(args);
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        MAIN_SCENE = new MainScene(primaryStage);
        initIcons(primaryStage);

        primaryStage.setTitle("GameRoom");
        primaryStage.setScene(MAIN_SCENE);
        primaryStage.setFullScreen(GENERAL_SETTINGS.isFullScreen());

        //TODO replace false by setting "start minimized"
        if (GENERAL_SETTINGS.isMinimizeOnStart()) {
            primaryStage.setOpacity(0);
        }
        primaryStage.show();
        if (GENERAL_SETTINGS.isMinimizeOnStart()) {
            primaryStage.hide();
            primaryStage.setOpacity(1);
        }
        Platform.runLater(() -> {
            primaryStage.setWidth(primaryStage.getWidth());
            primaryStage.setHeight(primaryStage.getHeight());
        });
    }

    @Override
    public void stop() {
        Main.logger.info("Closing app, saving settings.");
        for (int i = 0; i < CACHE_FOLDER.listFiles().length; i++) {
            File temp = CACHE_FOLDER.listFiles()[i];
            temp.delete();
        }
        GENERAL_SETTINGS.saveSettings();

        System.exit(0);
    }

    private void initIcons(Stage stage) {

        for (int i = 32; i < 513; i *= 2) {
            stage.getIcons().add(new Image("res/ui/icon/icon" + i + ".png"));
        }

        //Check the SystemTray is supported
        if (!SystemTray.isSupported()) {
            Main.logger.error("SystemTray not supported");
            return;
        }

        final PopupMenu popup = new PopupMenu();
        Image fxImage = new Image("res/ui/icon/icon16.png");
        TRAY_ICON = new TrayIcon(SwingFXUtils.fromFXImage(fxImage, null));
        TRAY_ICON.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    open(stage);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        TRAY_ICON.setImageAutoSize(true);
        Platform.setImplicitExit(DEV_MODE);
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                if (event.getEventType().equals(WindowEvent.WINDOW_CLOSE_REQUEST)) {
                    if (!DEV_MODE) {
                        stage.hide();
                        if (trayMessageCount < 2 && !GENERAL_SETTINGS.isNoMoreTrayMessage() && !GENERAL_SETTINGS.isDisableAllNotifications()) {
                            TRAY_ICON.displayMessage("GameRoom"
                                    , RESSOURCE_BUNDLE.getString("tray_icon_still_running_1")
                                            + RESSOURCE_BUNDLE.getString("always_in_background")
                                            + RESSOURCE_BUNDLE.getString("tray_icon_still_running_2"), TrayIcon.MessageType.INFO);
                            trayMessageCount++;
                        } else {
                            if (!GENERAL_SETTINGS.isNoMoreTrayMessage()) {
                                GENERAL_SETTINGS.setNoMoreTrayMessage(true);
                            }
                        }
                    }
                }
            }
        });
        final SystemTray tray = SystemTray.getSystemTray();

        // Create a pop-up menu components
        MenuItem openItem = new MenuItem(RESSOURCE_BUNDLE.getString("open"));
        openItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                open(stage);
            }
        });
        MenuItem settingsItem = new MenuItem(RESSOURCE_BUNDLE.getString("Settings"));
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MAIN_SCENE.fadeTransitionTo(new SettingsScene(new StackPane(), stage, MAIN_SCENE), stage);
                open(stage);
            }
        });
        //CheckboxMenuItem cb1 = new CheckboxMenuItem("Set auto size");
        //CheckboxMenuItem cb2 = new CheckboxMenuItem("Set tooltip");
        START_TRAY_MENU.setLabel(RESSOURCE_BUNDLE.getString("start"));
        MenuItem exitItem = new MenuItem(RESSOURCE_BUNDLE.getString("exit"));

        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                forceStop(stage);
            }
        });

        //Add components to pop-up menu
        popup.add(openItem);
        popup.add(START_TRAY_MENU);
        popup.addSeparator();
        popup.add(settingsItem);
        popup.addSeparator();
        popup.add(exitItem);

        TRAY_ICON.setPopupMenu(popup);

        try {
            tray.add(TRAY_ICON);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }

    /*public static void setCurrentProcessExplicitAppUserModelID(final String appID) {
        if (SetCurrentProcessExplicitAppUserModelID(new WString(appID)).longValue() != 0)
            throw new RuntimeException("unable to set current process explicit AppUserModelID to: " + appID);
    }

    private static native NativeLong SetCurrentProcessExplicitAppUserModelID(WString appID);

    static {
        Native.register("shell32");
    }*/

}