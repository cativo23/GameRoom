package com.gameroom.ui.control.button.gamebutton;

import com.gameroom.data.game.entry.GameEntry;
import com.gameroom.data.game.entry.Platform;
import com.gameroom.data.http.images.ImageUtils;
import com.gameroom.system.application.settings.PredefinedSetting;
import com.gameroom.ui.GeneralToast;
import com.gameroom.ui.Main;
import com.gameroom.ui.UIValues;
import com.gameroom.ui.control.button.ImageButton;
import com.gameroom.ui.dialog.GameRoomAlert;
import com.gameroom.ui.dialog.selector.AppSelectorDialog;
import com.gameroom.ui.scene.BaseScene;
import com.gameroom.ui.scene.GameInfoScene;
import com.gameroom.ui.scene.MainScene;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Optional;

import static com.gameroom.system.application.settings.GeneralSettings.settings;
import static com.gameroom.ui.Main.*;
import static javafx.scene.input.MouseEvent.*;

/**
 * Created by LM on 12/07/2016.
 */
public abstract class GameButton extends BorderPane {
    private static HashMap<String, Image> DEFAULT_IMAGES = new HashMap<>();
    private static Image DEFAULT_PLAY_IMAGE;
    private static Image DEFAULT_INFO_IMAGE;

    private final static long SECOND_START_DELAY = 2000;

    private final static double RATIO_NOTINSTALLEDIMAGE_COVER = 1 / 3.0;
    private final static double RATIO_PLAYBUTTON_COVER = 2 / 3.0;
    private final static double RATIO_INFOBUTTON_COVER = 1 / 6.0;

    public final static double FADE_IN_OUT_TIME = 0.1;

    public final static double COVER_HEIGHT_WIDTH_RATIO = 127.0 / 90.0;

    public static double COVER_SCALE_EFFECT_FACTOR = 1.1;
    private final static double COVER_BLUR_EFFECT_RADIUS = 10;
    private final static double COVER_BRIGHTNESS_EFFECT_FACTOR = 0.1;
    private final static double DISABLED_COVER_BRIGHTNESS = -0.6;

    private BaseScene parentScene;

    StackPane coverPane;
    HBox titleBox;
    private ImageView titleLogoView;
    private Label titleLabel;
    Label playTimeLabel;
    private Label ratingLabel;
    private Label releaseDateLabel;
    private boolean keepTimeLabelVisible = false;

    private ContextMenu contextMenu;
    ImageView coverView;
    private ImageView defaultCoverView;
    private ImageView notInstalledImage = new ImageView();

    ImageButton playButton;
    ImageButton infoButton;
    ImageButton warningButton;


    private GameEntry entry;
    private boolean inContextMenu = false;

    private ChangeListener<Boolean> monitoredChangeListener;

    private long lastGameStart = 0;

    //COVER EFFECTS
    DropShadow dropShadowBG = new DropShadow();
    ColorAdjust colorAdjustBG = new ColorAdjust();
    GaussianBlur blurBG = new GaussianBlur(0.0);
    ColorAdjust colorAdjustIMG = new ColorAdjust();
    GaussianBlur blurIMG = new GaussianBlur(0.0);


    GameButton(GameEntry entry, BaseScene scene, Pane parent) {
        super();
        this.entry = entry;
        this.parentScene = scene;

        initAll();
        if (parent instanceof TilePane) {
            ((TilePane) parent).prefTileWidthProperty().addListener((observable, oldValue, newValue) -> updateAllOnTileWidth(newValue.doubleValue()));
            ((TilePane) parent).prefTileHeightProperty().addListener((observable, oldValue, newValue) -> updateAllOnTileHeight(newValue.doubleValue()));
        }
    }

    public void reloadWith(GameEntry entry) {
        if (monitoredChangeListener != null) {
            this.entry.monitoredProperty().removeListener(monitoredChangeListener);
        }

        this.entry = entry;
        playTimeLabel.setText(entry.getPlayTimeFormatted(GameEntry.TIME_FORMAT_ROUNDED_HMS));
        ratingLabel.setText(Integer.toString(entry.getAggregated_rating()));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.yyyy");
        releaseDateLabel.setText(entry.getReleaseDate() != null ? formatter.format(entry.getReleaseDate()) : "-");

        titleLabel.setText(entry.getName());
        titleLabel.setTooltip(new Tooltip(entry.getName()));
        entry.monitoredProperty().addListener(monitoredChangeListener);


        setLauncherLogo();
        ImageUtils.getExecutorService().submit(this::showCover);

        initNotInstalled();
        checkFileExists();
    }

    public void setLauncherLogo() {
        double width = 20 * Main.SCREEN_WIDTH / 1920;
        double height = 20 * Main.SCREEN_HEIGHT / 1080;

        if (!entry.getPlatform().isPC() || (entry.getPlatform().isPC() && settings().getBoolean(PredefinedSetting.SHOW_PC_ICON))) {
            titleLogoView.setSmooth(false);
            titleLogoView.setPreserveRatio(true);
            titleLogoView.setFitWidth(width);
            titleLogoView.setFitHeight(height);
            entry.getPlatform().setCSSIcon(titleLogoView, false);
        } else {
            titleLogoView.setStyle("");
        }
    }

    private void initAll() {
        if (coverPane != null) {
            coverPane.getChildren().clear();
        }

        initCoverPane();

        //initContextMenu();
        initNameText();

        setOnKeyPressed(ke -> {
            switch (ke.getCode()) {
                case ENTER:
                    ke.consume();
                    if (!playButton.isDisabled())
                        playButton.fireEvent(new MouseEvent(MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, true, true, true, true, true, true, true, true, true, true, null));
                    break;
                case SPACE:
                    ke.consume();
                    if (!infoButton.isDisabled())
                        infoButton.fireEvent(new MouseEvent(MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, true, true, true, true, true, true, true, true, true, true, null));
                    break;
                default:
                    break;

            }
        });
        setCenter(coverPane);
        setBottom(titleBox);
        checkFileExists();
    }

    private void initNameText() {
        titleBox = new HBox();
        titleBox.setSpacing(3 * Main.SCREEN_WIDTH / 1920);
        BorderPane.setMargin(titleBox, new Insets(10 * settings().getWindowHeight() / 1080, 0, 0, 0));
        setAlignment(titleBox, Pos.CENTER);

        titleLabel = new Label(entry.getName());
        titleLabel.setTooltip(new Tooltip(entry.getName()));

        titleLogoView = new ImageView();
        titleLogoView.setFocusTraversable(false);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.setFocusTraversable(false);
        setLauncherLogo();

        titleLabel.setScaleX(0.90f);
        titleLabel.setScaleY(0.90f);

        titleBox.getChildren().addAll(titleLogoView, titleLabel);

        monitoredChangeListener = (observable, oldValue, newValue) -> {
            if (settings().getBoolean(PredefinedSetting.DEBUG_MODE)) {
                if (!oldValue && newValue) {
                    titleLabel.setId("advanced-setting-label");
                    titleLabel.setTooltip(new Tooltip(Main.getString("click_to_stop_monitor")));
                    titleLabel.setOnMouseClicked(event -> {
                        event.consume();
                        entry.setMonitored(false);
                    });
                } else if (!newValue) {
                    titleLabel.setId("");
                }
            }
        };
        entry.monitoredProperty().addListener(monitoredChangeListener);
    }

    private void initContextMenu() {
        contextMenu = new ContextMenu();
        MenuItem cmItem1 = new MenuItem(Main.getString("Play"));
        cmItem1.setOnAction(e -> playButton.fireEvent(new MouseEvent(MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, true, true, true, true, true, true, true, true, true, true, null)));
        contextMenu.getItems().add(cmItem1);
        MenuItem cmItem2 = new MenuItem(Main.getString("edit"));
        cmItem2.setOnAction(eh -> {
        });
        contextMenu.getItems().add(cmItem2);
        MenuItem cmItem3 = new MenuItem(Main.getString("About"));
        cmItem3.setOnAction(nh -> {
            infoButton.fireEvent(new MouseEvent(MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, true, true, true, true, true, true, true, true, true, true, null));
        });
        contextMenu.getItems().add(cmItem3);
        contextMenu.setOnShowing(event -> inContextMenu = true);
        contextMenu.setOnHiding(event -> {
            inContextMenu = false;
            coverPane.fireEvent(new MouseEvent(MOUSE_EXITED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, true, true, true, true, true, true, true, true, true, true, null));
        });
        coverPane.setOnMouseClicked(event -> {
            if (event.isSecondaryButtonDown()) {
                event.consume();
                contextMenu.show(coverPane, event.getScreenX(), event.getScreenY());
            }
        });
    }

    private void initCoverPane() {
        coverPane = new StackPane();

        /*if (DEFAULT_PLAY_IMAGE == null) {
            DEFAULT_PLAY_IMAGE = new Image("res/com.gameroom.ui/playButton.png", SCREEN_WIDTH/10, SCREEN_WIDTH/10, true, true, true);
        }
        if (DEFAULT_INFO_IMAGE == null) {
            DEFAULT_INFO_IMAGE = new Image("res/com.gameroom.ui/infoButton.png", SCREEN_WIDTH/20, SCREEN_WIDTH/20, true, true, true);
        }*/
        DropShadow ds = new DropShadow();
        ds.setOffsetY(2.0f);
        ds.setBlurType(BlurType.GAUSSIAN);
        ds.setSpread(0.55);
        //ds.setSpread(10);
        ds.setRadius(10);
        ds.setColor(Color.color(0.2f, 0.2f, 0.2f));

        //playButton = new ImageButton(DEFAULT_PLAY_IMAGE);
        playButton = new ImageButton("tile-play-button", SCREEN_WIDTH / 10, SCREEN_WIDTH / 10);
        //infoButton = new ImageButton(DEFAULT_INFO_IMAGE);
        infoButton = new ImageButton("tile-info-button", SCREEN_WIDTH / 20, SCREEN_WIDTH / 20);
        warningButton = new ImageButton("tile-warning-button", SCREEN_WIDTH / 20, SCREEN_WIDTH / 20);
        disableNode(warningButton);

        playTimeLabel = new Label(entry.getPlayTimeFormatted(GameEntry.TIME_FORMAT_ROUNDED_HMS));
        playTimeLabel.setId("tile-playtime-label");
        playTimeLabel.setEffect(ds);
        playTimeLabel.setOpacity(0);
        playTimeLabel.setFocusTraversable(false);
        playTimeLabel.setMouseTransparent(true);
        ratingLabel = new Label(Integer.toString(entry.getAggregated_rating()));
        ratingLabel.setEffect(ds);
        ratingLabel.setFocusTraversable(false);
        ratingLabel.setMouseTransparent(true);

        DateTimeFormatter buttonDateFormat = DateTimeFormatter.ofPattern("MM.yyyy");
        releaseDateLabel = new Label(entry.getReleaseDate() != null ? buttonDateFormat.format(entry.getReleaseDate()) : "-");
        releaseDateLabel.setEffect(ds);
        releaseDateLabel.setFocusTraversable(false);
        releaseDateLabel.setMouseTransparent(true);

        playButton.setOpacity(0);
        infoButton.setOpacity(0);
        infoButton.setPadding(UIValues.CONTROL_XSMALL.insets());
        warningButton.setPadding(UIValues.CONTROL_XSMALL.insets());
        ratingLabel.setOpacity(0);
        releaseDateLabel.setOpacity(0);
        playButton.setFocusTraversable(false);
        infoButton.setFocusTraversable(false);
        warningButton.setFocusTraversable(false);

        StackPane.setMargin(ratingLabel, new Insets(10 * Main.SCREEN_HEIGHT / 1080
                , 10 * Main.SCREEN_WIDTH / 1920
                , 2 * Main.SCREEN_HEIGHT / 1080
                , 10 * Main.SCREEN_WIDTH / 1920));
        /*ratingLabel.setStyle("-fx-font-family: 'Helvetica Neue';\n" +
                "    -fx-font-size: 38.0px;\n" +
                "    -fx-stroke: black;\n" +
                "    -fx-stroke-width: 1;" +
                "    -fx-font-weight: 200;");*/
        ratingLabel.setId("game-cover-rating-label");
        StackPane.setMargin(releaseDateLabel, new Insets(10 * Main.SCREEN_HEIGHT / 1080
                , 10 * Main.SCREEN_WIDTH / 1920
                , 2 * Main.SCREEN_HEIGHT / 1080
                , 10 * Main.SCREEN_WIDTH / 1920));
        releaseDateLabel.setId("game-cover-year-label");
        /*releaseDateLabel.setStyle("-fx-font-family: 'Helvetica Neue';\n" +
                "    -fx-font-size: 28.0px;\n" +
                "    -fx-stroke: black;\n" +
                "    -fx-stroke-width: 1;" +
                "    -fx-font-weight: 200;");*/

        initCoverView();

        Image defaultCoverImage = DEFAULT_IMAGES.get("cover" + getCoverWidth() + "x" + getCoverHeight());
        if (defaultCoverImage == null) {
            for (int i = 256; i < 1025; i *= 2) {
                if (i > getCoverHeight()) {
                    defaultCoverImage = new Image("res/defaultImages/cover" + i + ".jpg", getCoverWidth(), getCoverHeight(), false, true);
                    break;
                }
            }
            if (defaultCoverImage == null) {
                defaultCoverImage = new Image("res/defaultImages/cover1024.jpg", getCoverWidth(), getCoverHeight(), false, true);
            }
            DEFAULT_IMAGES.put("cover" + getCoverWidth() + "x" + getCoverHeight(), defaultCoverImage);
        }
        defaultCoverView = new ImageView(defaultCoverImage);

        ImageUtils.getExecutorService().submit(this::showCover);

        playButton.setOnMouseClicked(mc -> {
            if (System.currentTimeMillis() - lastGameStart > SECOND_START_DELAY) {
                lastGameStart = System.currentTimeMillis();
                playButton.setMouseTransparent(true);
                Main.getExecutorService().submit(() -> {
                    try {
                        Thread.sleep(SECOND_START_DELAY);
                    } catch (InterruptedException ignored) {
                    }
                    playButton.setMouseTransparent(false);
                });

                if (!entry.isSteamGame() && !entry.isMSStoreGame()) {
                    File gamePath = new File(entry.getPath());
                    if (gamePath.isDirectory()) {
                        try {
                            if (MAIN_SCENE != null) {
                                GeneralToast.displayToast(Main.getString("looking_for_game_exe"), MAIN_SCENE.getParentStage());
                            }
                            AppSelectorDialog selector = new AppSelectorDialog(gamePath, entry.getPlatform().getSupportedExtensions());
                            selector.searchApps();

                            Optional<ButtonType> ignoredOptionnal = selector.showAndWait();
                            ignoredOptionnal.ifPresent(pairs -> {
                                if (pairs.getButtonData().equals(ButtonBar.ButtonData.OK_DONE) && selector.getSelectedFile() != null) {
                                    entry.setPath(selector.getSelectedFile().getAbsolutePath());
                                    setFocused(false);
                                    entry.startGame();
                                }
                            });

                        } catch (IllegalArgumentException e) {
                            GameRoomAlert.error(Main.getString("invalid_path_not_file"));
                        }
                    } else {
                        setFocused(false);
                        entry.startGame();
                    }
                } else {
                    setFocused(false);
                    entry.startGame();
                }
            }
        });
        infoButton.setOnMouseClicked(event -> parentScene.fadeTransitionTo(new GameInfoScene(new StackPane(), parentScene.getParentStage(), parentScene, entry), parentScene.getParentStage()));

        warningButton.setOnMouseClicked(event -> {
            GameRoomAlert.warning(Main.getString("warning_game_moved_deleted"));
        });
        warningButton.setTooltip(new Tooltip(Main.getString("warning_game_moved_deleted")));

        setDefaultCoverEffects();

        setFocusTraversable(true);
        focusedProperty().addListener((observable, oldValue, newValue) -> {
            javafx.application.Platform.runLater(() -> {
                double dropShadowBGX = dropShadowBG.getOffsetX();
                double dropShadowBGY = dropShadowBG.getOffsetY();
                double blurBGRadius = blurBG.getRadius();
                double blurIMGRadius = blurIMG.getRadius();
                double colorAdjustIMGBrightness = colorAdjustIMG.getBrightness();
                double colorAdjustBGBrightness = colorAdjustBG.getBrightness();

                if (newValue) {
                    playButton.setMouseTransparent(false);
                    infoButton.setMouseTransparent(false);

                    Timeline fadeInTimeline = new Timeline(
                            new KeyFrame(Duration.seconds(0),
                                    new KeyValue(dropShadowBG.offsetXProperty(), dropShadowBG.offsetXProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(dropShadowBG.offsetYProperty(), dropShadowBG.offsetYProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(blurBG.radiusProperty(), blurBG.radiusProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(blurIMG.radiusProperty(), blurIMG.radiusProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(scaleXProperty(), scaleXProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(playButton.opacityProperty(), playButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                    new KeyValue(infoButton.opacityProperty(), infoButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                    new KeyValue(scaleYProperty(), scaleYProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(colorAdjustIMG.brightnessProperty(), colorAdjustIMG.brightnessProperty().getValue(), Interpolator.LINEAR),
                                    new KeyValue(colorAdjustBG.brightnessProperty(), colorAdjustBG.brightnessProperty().getValue(), Interpolator.LINEAR)),
                            new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME),
                                    new KeyValue(dropShadowBG.offsetXProperty(), dropShadowBG.offsetXProperty().getValue() / COVER_SCALE_EFFECT_FACTOR, Interpolator.LINEAR),
                                    new KeyValue(dropShadowBG.offsetYProperty(), dropShadowBG.offsetYProperty().getValue() / COVER_SCALE_EFFECT_FACTOR, Interpolator.LINEAR),
                                    new KeyValue(blurBG.radiusProperty(), COVER_BLUR_EFFECT_RADIUS, Interpolator.LINEAR),
                                    new KeyValue(blurIMG.radiusProperty(), COVER_BLUR_EFFECT_RADIUS, Interpolator.LINEAR),
                                    new KeyValue(scaleXProperty(), COVER_SCALE_EFFECT_FACTOR, Interpolator.LINEAR),
                                    new KeyValue(playButton.opacityProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(infoButton.opacityProperty(), 1, Interpolator.EASE_OUT),
                                    new KeyValue(scaleYProperty(), COVER_SCALE_EFFECT_FACTOR, Interpolator.LINEAR),
                                    new KeyValue(colorAdjustIMG.brightnessProperty(), warningButton.isVisible() ? DISABLED_COVER_BRIGHTNESS : -COVER_BRIGHTNESS_EFFECT_FACTOR, Interpolator.LINEAR),
                                    new KeyValue(colorAdjustBG.brightnessProperty(), warningButton.isVisible() ? DISABLED_COVER_BRIGHTNESS : -COVER_BRIGHTNESS_EFFECT_FACTOR, Interpolator.LINEAR)
                            ));
                    fadeInTimeline.setCycleCount(1);
                    fadeInTimeline.setAutoReverse(false);

                    fadeInTimeline.play();

                    //coverPane.fireEvent(new MouseEvent(MOUSE_ENTERED,0,0,0,0, MouseButton.PRIMARY,0,false, false, false, false, false, false, false, false, false, false, null));
                    if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_KEYBOARD) {
                        playButton.fireEvent(new MouseEvent(MOUSE_ENTERED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, false, false, false, false, false, false, false, false, false, false, null));
                    }
                    ImageUtils.getExecutorService().submit(() -> MAIN_SCENE.setImageBackground(entry.getImagePath(1)));

                } else {
                    if (!inContextMenu) {
                        playButton.setMouseTransparent(true);
                        infoButton.setMouseTransparent(true);

                        Timeline fadeOutTimeline;
                        if (warningButton.isVisible()) {
                            fadeOutTimeline = new Timeline(
                                    new KeyFrame(Duration.seconds(0),
                                            new KeyValue(dropShadowBG.offsetXProperty(), dropShadowBG.offsetXProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(dropShadowBG.offsetYProperty(), dropShadowBG.offsetYProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(blurBG.radiusProperty(), blurBG.radiusProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(blurIMG.radiusProperty(), blurIMG.radiusProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(scaleXProperty(), scaleXProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(playButton.opacityProperty(), playButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(infoButton.opacityProperty(), infoButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(playTimeLabel.opacityProperty(), playTimeLabel.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(scaleYProperty(), scaleYProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(colorAdjustIMG.brightnessProperty(), colorAdjustIMG.brightnessProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(colorAdjustBG.brightnessProperty(), colorAdjustBG.brightnessProperty().getValue(), Interpolator.LINEAR)),
                                    new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME),
                                            new KeyValue(dropShadowBG.offsetXProperty(), 6.0 * SCREEN_WIDTH / 1920, Interpolator.LINEAR),
                                            new KeyValue(dropShadowBG.offsetYProperty(), 4.0 * SCREEN_WIDTH / 1080, Interpolator.LINEAR),
                                            new KeyValue(blurBG.radiusProperty(), COVER_BLUR_EFFECT_RADIUS, Interpolator.LINEAR),
                                            new KeyValue(blurIMG.radiusProperty(), COVER_BLUR_EFFECT_RADIUS, Interpolator.LINEAR),
                                            new KeyValue(scaleXProperty(), 1, Interpolator.LINEAR),
                                            new KeyValue(playButton.opacityProperty(), 0, Interpolator.EASE_OUT),
                                            new KeyValue(infoButton.opacityProperty(), 0, Interpolator.EASE_OUT),
                                            new KeyValue(playTimeLabel.opacityProperty(), keepTimeLabelVisible ? 1 : 0, Interpolator.EASE_OUT),
                                            new KeyValue(scaleYProperty(), 1, Interpolator.LINEAR),
                                            new KeyValue(colorAdjustBG.brightnessProperty(), DISABLED_COVER_BRIGHTNESS, Interpolator.LINEAR),
                                            new KeyValue(colorAdjustIMG.brightnessProperty(), DISABLED_COVER_BRIGHTNESS, Interpolator.LINEAR)
                                    ));
                        } else {
                            fadeOutTimeline = new Timeline(
                                    new KeyFrame(Duration.seconds(0),
                                            new KeyValue(dropShadowBG.offsetXProperty(), dropShadowBG.offsetXProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(dropShadowBG.offsetYProperty(), dropShadowBG.offsetYProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(blurBG.radiusProperty(), blurBG.radiusProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(blurIMG.radiusProperty(), blurIMG.radiusProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(scaleXProperty(), scaleXProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(playButton.opacityProperty(), playButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(infoButton.opacityProperty(), infoButton.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(playTimeLabel.opacityProperty(), playTimeLabel.opacityProperty().getValue(), Interpolator.EASE_OUT),
                                            new KeyValue(scaleYProperty(), scaleYProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(colorAdjustIMG.brightnessProperty(), colorAdjustIMG.brightnessProperty().getValue(), Interpolator.LINEAR),
                                            new KeyValue(colorAdjustBG.brightnessProperty(), colorAdjustBG.brightnessProperty().getValue(), Interpolator.LINEAR)),
                                    new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME),
                                            new KeyValue(dropShadowBG.offsetXProperty(), 6.0 * SCREEN_WIDTH / 1920, Interpolator.LINEAR),
                                            new KeyValue(dropShadowBG.offsetYProperty(), 4.0 * SCREEN_WIDTH / 1080, Interpolator.LINEAR),
                                            new KeyValue(blurBG.radiusProperty(), 0, Interpolator.LINEAR),
                                            new KeyValue(blurIMG.radiusProperty(), 0, Interpolator.LINEAR),
                                            new KeyValue(scaleXProperty(), 1, Interpolator.LINEAR),
                                            new KeyValue(playButton.opacityProperty(), 0, Interpolator.EASE_OUT),
                                            new KeyValue(infoButton.opacityProperty(), 0, Interpolator.EASE_OUT),
                                            new KeyValue(playTimeLabel.opacityProperty(), keepTimeLabelVisible ? 1 : 0, Interpolator.EASE_OUT),
                                            new KeyValue(scaleYProperty(), 1, Interpolator.LINEAR),
                                            new KeyValue(colorAdjustBG.brightnessProperty(), 0, Interpolator.LINEAR),
                                            new KeyValue(colorAdjustIMG.brightnessProperty(), 0, Interpolator.LINEAR)
                                    ));
                        }
                        fadeOutTimeline.setCycleCount(1);
                        fadeOutTimeline.setAutoReverse(false);

                        fadeOutTimeline.play();
                    }
                    //coverPane.fireEvent(new MouseEvent(MOUSE_EXITED,0,0,0,0, MouseButton.PRIMARY,0,false, false, false, false, false, false, false, false, false, false, null));
                    if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_KEYBOARD) {
                        playButton.fireEvent(new MouseEvent(MOUSE_EXITED, 0, 0, 0, 0, MouseButton.PRIMARY, 0, false, false, false, false, false, false, false, false, false, false, null));
                    }
                }
            });
        });
        coverPane.setOnMouseMoved(e -> {
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                if (this.contains(new Point2D(e.getX(), e.getY()))) {
                    requestFocus();
                }
            }
        });
        coverPane.setOnMouseEntered(e -> {
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                requestFocus();
            }
        });

        coverPane.setOnMouseExited(e -> {
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                //requestFocus();
                if (!keepTimeLabelVisible) {
                    javafx.application.Platform.runLater(() -> {
                        Timeline fadeOutTimeline = new Timeline(
                                new KeyFrame(Duration.seconds(0),
                                        new KeyValue(playTimeLabel.opacityProperty(), playTimeLabel.opacityProperty().getValue(), Interpolator.EASE_OUT)),
                                new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME),
                                        new KeyValue(playTimeLabel.opacityProperty(), keepTimeLabelVisible ? 1 : 0, Interpolator.EASE_OUT)
                                ));
                        fadeOutTimeline.setCycleCount(1);
                        fadeOutTimeline.setAutoReverse(false);

                        fadeOutTimeline.play();
                    });
                }
            }
        });
        playButton.addMouseEnteredHandler(e -> {
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                requestFocus();
            }
        });
        infoButton.addMouseEnteredHandler(e -> {
            playTimeLabel.setText(entry.getPlayTimeFormatted(GameEntry.TIME_FORMAT_ROUNDED_HMS));
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                requestFocus();
                playTimeLabel.setOpacity(keepTimeLabelVisible ? 1 : 0);
                Timeline fadeInTimeline = new Timeline(
                        new KeyFrame(Duration.seconds(0),
                                new KeyValue(playTimeLabel.opacityProperty(), playTimeLabel.opacityProperty().getValue(), Interpolator.EASE_IN)),
                        new KeyFrame(Duration.seconds(5 * FADE_IN_OUT_TIME),
                                new KeyValue(playTimeLabel.opacityProperty(), 1, Interpolator.EASE_IN)
                        ));
                fadeInTimeline.setCycleCount(1);
                fadeInTimeline.setAutoReverse(false);

                fadeInTimeline.play();
            }
        });
        infoButton.addMouseExitedHandler(e -> {
            if (MAIN_SCENE.getInputMode() == MainScene.INPUT_MODE_MOUSE) {
                if (!keepTimeLabelVisible) {
                    javafx.application.Platform.runLater(() -> {
                        Timeline fadeOutTimeline = new Timeline(
                                new KeyFrame(Duration.seconds(0),
                                        new KeyValue(playTimeLabel.opacityProperty(), playTimeLabel.opacityProperty().getValue(), Interpolator.EASE_OUT)),
                                new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME),
                                        new KeyValue(playTimeLabel.opacityProperty(), keepTimeLabelVisible ? 1 : 0, Interpolator.EASE_OUT)
                                ));
                        fadeOutTimeline.setCycleCount(1);
                        fadeOutTimeline.setAutoReverse(false);

                        fadeOutTimeline.play();

                        playTimeLabel.setOpacity(0);
                    });
                }
            }
        });

        coverPane.getChildren().addAll(
                defaultCoverView,
                coverView,
                playButton,
                infoButton,
                warningButton,
                playTimeLabel,
                ratingLabel,
                releaseDateLabel);
        StackPane.setAlignment(infoButton, Pos.BOTTOM_RIGHT);
        StackPane.setAlignment(warningButton, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(playTimeLabel, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(ratingLabel, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(releaseDateLabel, Pos.BOTTOM_LEFT);

        notInstalledImage.setPreserveRatio(true);
        coverPane.getChildren().add(notInstalledImage);
        StackPane.setAlignment(notInstalledImage, Pos.TOP_RIGHT);
        initNotInstalled();
        setCache(true);
        setCacheHint(CacheHint.SPEED);
    }

    private void setDefaultCoverEffects() {
        //COVER EFFECTS
        dropShadowBG.setOffsetX(6.0 * SCREEN_WIDTH / 1920);
        dropShadowBG.setOffsetY(4.0 * SCREEN_HEIGHT / 1080);
        colorAdjustBG.setBrightness(0.0);
        colorAdjustBG.setInput(dropShadowBG);
        blurBG.setInput(colorAdjustBG);
        blurBG.setRadius(0);
        colorAdjustIMG.setBrightness(0.0);
        blurIMG.setInput(colorAdjustIMG);
        blurIMG.setRadius(0);

        coverView.setEffect(blurIMG);
        defaultCoverView.setEffect(blurBG);
    }

    private void setDisabledCoverEffects() {
        //COVER EFFECTS
        dropShadowBG.setOffsetX(6.0 * SCREEN_WIDTH / 1920);
        dropShadowBG.setOffsetY(4.0 * SCREEN_HEIGHT / 1080);
        colorAdjustBG.setBrightness(DISABLED_COVER_BRIGHTNESS);
        colorAdjustBG.setInput(dropShadowBG);
        blurBG.setInput(colorAdjustBG);
        blurBG.setRadius(COVER_BLUR_EFFECT_RADIUS);
        colorAdjustIMG.setBrightness(DISABLED_COVER_BRIGHTNESS);
        blurIMG.setInput(colorAdjustIMG);
        blurIMG.setRadius(COVER_BLUR_EFFECT_RADIUS);

        coverView.setEffect(blurIMG);
        defaultCoverView.setEffect(blurBG);
    }

    protected abstract int getCoverHeight();

    protected abstract int getCoverWidth();

    protected abstract void initCoverView();

    public void showPlaytime() {
        keepTimeLabelVisible = true;
        playTimeLabel.setOpacity(1);
    }

    public void showRating() {
        ratingLabel.setOpacity(1);
    }

    public void hidePlaytime() {
        keepTimeLabelVisible = false;
        playTimeLabel.setOpacity(0);
    }

    public void hideRating() {
        ratingLabel.setOpacity(0);
    }

    public GameEntry getEntry() {
        return entry;
    }

    void disableNode(Node node) {
        node.setVisible(false);
        node.setDisable(true);
        node.setManaged(false);
        node.setMouseTransparent(true);
    }

    private void enableNode(Node node) {
        node.setVisible(true);
        node.setDisable(false);
        node.setManaged(true);
        node.setMouseTransparent(false);
    }

    public void hideReleaseDate() {
        releaseDateLabel.setOpacity(0);
    }

    public void showReleaseDate() {
        releaseDateLabel.setOpacity(1);
    }

    final void initContentSize(double width, double size) {
        updateAllOnTileWidth(width);
        updateAllOnTileHeight(size);
    }

    final void initContentSize(TilePane pane) {
        updateAllOnTileWidth(pane.getPrefTileWidth());
        updateAllOnTileHeight(pane.getPrefTileHeight());
    }

    private void updateAllOnTileWidth(double width) {
        setPrefWidth(width);
        setWidth(width);
        coverView.setFitWidth(width);
        defaultCoverView.setFitWidth(width);
        playButton.setFitWidth(width * RATIO_PLAYBUTTON_COVER);
        infoButton.setFitWidth(width * RATIO_INFOBUTTON_COVER);
        warningButton.setFitWidth(width * RATIO_INFOBUTTON_COVER);
        notInstalledImage.setFitWidth(width * RATIO_NOTINSTALLEDIMAGE_COVER);

        onNewTileWidth(width);
    }

    private void updateAllOnTileHeight(double height) {
        setPrefHeight(height);
        setHeight(height);
        coverView.setFitHeight(height);
        defaultCoverView.setFitHeight(height);
        playButton.setFitHeight(height * RATIO_PLAYBUTTON_COVER);
        infoButton.setFitHeight(height * RATIO_INFOBUTTON_COVER);
        warningButton.setFitHeight(height * RATIO_INFOBUTTON_COVER);
        notInstalledImage.setFitHeight(height * RATIO_NOTINSTALLEDIMAGE_COVER);

        onNewTileHeight(height);
    }

    protected abstract void onNewTileWidth(double width);

    protected abstract void onNewTileHeight(double height);

    private void initNotInstalled() {
        if (!getEntry().isInstalled()) {

            //Image addImage = new Image("res/com.gameroom.ui/toDownloadIcon.png");
            notInstalledImage.setId("tile-todownload-overlay");
            notInstalledImage.setVisible(true);

            //coverView.setEffect(coverColorAdjust);
        } else {
            notInstalledImage.setImage(null);
            notInstalledImage.setVisible(false);
        }
    }

    public void clearCover() {
        coverView.setImage(null);
    }

    public void showCover() {
        double width = getCoverWidth();
        double height = getCoverHeight();

        ImageUtils.transitionToCover(entry.getImagePath(0), width, height, coverView);
        //ImageUtils.getExecutorService().submit(() -> ImageUtils.transitionToCover(entry.getImagePath(0), width, height, coverView));
    }

    public void checkFileExists() {
        if (settings().getBoolean(PredefinedSetting.REPORT_INVALID_GAMES)) {
            boolean validLauncher = (!entry.isSteamGame()
                    && !entry.isMSStoreGame()
                    && entry.getPlatform().getId() != Platform.STEAM_ONLINE_ID)
                    || (entry.isMSStoreGame() && entry.getMonitorProcess() != null);
            if (validLauncher && entry.getPath() != null) {
                File file = new File(entry.isMSStoreGame() ? entry.getMonitorProcess() : entry.getPath());
                if (!file.exists() && warningButton.isDisabled()) {
                    setDisabledCoverEffects();
                    enableNode(warningButton);
                    disableNode(playButton);
                    LOGGER.debug("Entry " + entry.getName() + " has non-existent file: " + entry.getPath());
                } else if (file.exists() && !warningButton.isDisabled()) {
                    disableNode(warningButton);
                    enableNode(playButton);
                    setDefaultCoverEffects();
                }
            }
        } else {
            if (!warningButton.isDisabled()) {
                disableNode(warningButton);
                enableNode(playButton);
                setDefaultCoverEffects();
            }
        }
    }

}
