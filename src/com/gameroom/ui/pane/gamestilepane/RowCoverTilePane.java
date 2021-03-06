package com.gameroom.ui.pane.gamestilepane;

import com.gameroom.data.game.entry.GameEntry;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import com.gameroom.ui.Main;
import com.gameroom.ui.control.button.DualImageButton;
import com.gameroom.ui.control.button.OnActionHandler;
import com.gameroom.ui.control.button.gamebutton.GameButton;
import com.gameroom.ui.control.button.gamebutton.TileGameButton;
import com.gameroom.ui.scene.MainScene;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;

import static com.gameroom.ui.Main.SCREEN_HEIGHT;
import static com.gameroom.ui.Main.SCREEN_WIDTH;
import static com.gameroom.ui.control.button.gamebutton.GameButton.COVER_HEIGHT_WIDTH_RATIO;
import static com.gameroom.ui.control.button.gamebutton.GameButton.FADE_IN_OUT_TIME;

/**
 * Created by LM on 16/08/2016.
 */
public class RowCoverTilePane extends CoverTilePane {
    public final static String TYPE_LAST_PLAYED = "last_played";
    public final static String TYPE_RECENTLY_ADDED = "recently_added";
    public final static Comparator<GameEntry> LAST_PLAYED_COMPARATOR = (o1, o2) -> {
        int result = 0;
        LocalDateTime date1 = o1.getLastPlayedDate();
        LocalDateTime date2 = o2.getLastPlayedDate();

        if (date1 == null && date2 != null) {
            return 1;
        } else if (date2 == null && date1 != null) {
            return -1;
        } else if (date1 == null && date2 == null) {
            result = 0;
        } else {
            result = date2.compareTo(date1);
        }
        if (result == 0) {
            String name1 = o1.getName();
            String name2 = o2.getName();
            result = name1.compareToIgnoreCase(name2);
        }
        return result;
    };

    public final static Comparator<GameEntry> RECENTLY_ADDED_COMPARATOR = (o1, o2) -> {
        int result = 0;
        LocalDateTime date1 = o1.getAddedDate();
        LocalDateTime date2 = o2.getAddedDate();

        if (date1 == null && date2 != null) {
            return 1;
        } else if (date2 == null && date1 != null) {
            return -1;
        } else if (date1 == null && date2 == null) {
            result = 0;
        } else {
            result = date2.compareTo(date1);
        }
        if (result == 0) {
            String name1 = o1.getName();
            String name2 = o2.getName();
            result = name1.compareToIgnoreCase(name2);
        }

        return result;
    };
    final static String TYPE_NAME = "name";

    private Comparator<GameEntry> entriesComparator;
    int maxColumn = 5; //TODO implement modularized column number selection
    private Separator separator = new Separator();
    private boolean folded = false;
    //private ScrollPane horizontalScrollPane;
    private DualImageButton foldToggleButton;

    private ArrayList<ChangeListener<Boolean>> onFoldedListeners = new ArrayList<>();

    public RowCoverTilePane(MainScene parentScene, String type) {
        super(parentScene, Main.getString(type));

        tilePane.setPadding(new Insets(30 * SCREEN_HEIGHT / 1080, 20 * SCREEN_WIDTH / 1920, 30 * SCREEN_HEIGHT / 1080, 20 * SCREEN_WIDTH / 1920));
        tilePane.setOrientation(Orientation.HORIZONTAL);

        tilePane.setPrefRows(1);
        tilePane.setPrefColumns(Integer.MAX_VALUE);
        this.entriesComparator = null;
        switch (type) {
            case TYPE_LAST_PLAYED:
                this.entriesComparator = LAST_PLAYED_COMPARATOR;
                break;
            case TYPE_RECENTLY_ADDED:
                this.entriesComparator = RECENTLY_ADDED_COMPARATOR;
                break;
            case TYPE_NAME:
                this.entriesComparator = new Comparator<GameEntry>() {
                    @Override
                    public int compare(GameEntry o1, GameEntry o2) {
                        String name1 = o1.getName();
                        String name2 = o2.getName();
                        return name1.compareToIgnoreCase(name2);
                    }
                };
                break;
            default:
                break;
        }

        tilesList.addListener(new ListChangeListener<GameButton>() {
            @Override
            public void onChanged(Change<? extends GameButton> c) {
                boolean orderMayChange = false;
                while (c.next() && !orderMayChange) {
                    orderMayChange = orderMayChange || c.wasAdded() || c.wasRemoved() || c.wasReplaced() || c.wasUpdated();
                }
                if (orderMayChange) {
                    ArrayList<Integer> ids = new ArrayList<Integer>();
                    for (GameButton button : tilesList) {
                        ids.add(button.getEntry().getId());
                    }
                    tilesList.sort(new Comparator<Node>() {
                        @Override
                        public int compare(Node o1, Node o2) {
                            return entriesComparator.compare(((GameButton) o1).getEntry(), ((GameButton) o2).getEntry());
                        }
                    });
                    boolean hideTilePane = true;
                    int shownCount = 0;
                    for (int i = 0; i < tilesList.size(); i++) {
                        boolean hide = false;

                        switch (type) {
                            case TYPE_LAST_PLAYED:
                                hide = tilesList.get(i).getEntry().getLastPlayedDate() == null;
                                break;
                            case TYPE_RECENTLY_ADDED:
                                hide = tilesList.get(i).getEntry().getAddedDate() == null;
                                break;
                            case TYPE_NAME:
                                hide = tilesList.get(i).getEntry().getName() == null;
                                break;
                            default:
                                break;
                        }
                        boolean visible = shownCount < maxColumn && !hide;
                        setGameButtonVisible(tilesList.get(i), visible);
                        hideTilePane = hideTilePane && !visible;
                        if(visible){
                            shownCount++;
                        }
                    }
                    if(hideTilePane){
                        hide();
                    }
                    if (!hideTilePane) {
                        boolean changedOrder = false;
                        for (int i = 0; i < ids.size() && !changedOrder; i++) {
                            changedOrder = ids.get(i) == tilesList.get(i).getEntry().getId();
                        }
                        if (changedOrder || hidden) {
                            show();
                        }
                        if(changedOrder && automaticSort){
                            sort();
                        }
                    }
                }
            }
        });
        StackPane wrappingPane = new StackPane();
        wrappingPane.getChildren().add(tilePane);
        setCenter(wrappingPane);
        separator.setPadding(titleLabel.getPadding());
        separator.maxWidthProperty().bind(titleLabel.widthProperty());

        //Image arrowUpImage = new Image("res/com.gameroom.ui/arrowUp.png", SCREEN_WIDTH / 70, SCREEN_WIDTH / 70, true, true);
        //Image arrowDownImage = new Image("res/com.gameroom.ui/arrowDown.png", SCREEN_WIDTH / 70, SCREEN_WIDTH / 70, true, true);

        foldToggleButton = new DualImageButton("arrow-right-button", "arrow-down-button",SCREEN_WIDTH / 70,SCREEN_WIDTH / 70);
        foldToggleButton.setFocusTraversable(false);
        foldToggleButton.setOnDualAction(new OnActionHandler() {
            @Override
            public void handle(ActionEvent me) {
                if (foldToggleButton.inFirstState()) {
                    folded = false;
                    openTilePane();
                } else {
                    folded = true;
                    closeTilePane();
                }
            }
        });
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.getChildren().clear();
        topBox.getChildren().addAll(iconButton,titleLabel, foldToggleButton);
        setTop(topBox);

        setBottom(separator);
    }

    public void fold() {
        foldToggleButton.forceState("arrow-right-button");
        foldToggleButton.fireEvent(new ActionEvent());
        folded = true;
    }

    public void unfold() {
        foldToggleButton.forceState("arrow-down-button");
        foldToggleButton.fireEvent(new ActionEvent());
        folded = false;
    }

    private void openTilePane() {
        tilePane.setManaged(true);
        tilePane.setVisible(true);
        separator.setManaged(true);
        separator.setVisible(true);
        Timeline fadeInTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0),
                        new KeyValue(tilePane.opacityProperty(), 0, Interpolator.EASE_IN)),
                new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME * 2),
                        new KeyValue(tilePane.opacityProperty(), 1, Interpolator.EASE_OUT)
                ));
        fadeInTimeline.setCycleCount(1);
        fadeInTimeline.setAutoReverse(false);
        fadeInTimeline.play();

        for (ChangeListener listener : onFoldedListeners) {
            listener.changed(null, false, false);
        }
    }

    private void closeTilePane() {
        Timeline fadeOutTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0),
                        new KeyValue(tilePane.opacityProperty(), tilePane.opacityProperty().getValue(), Interpolator.EASE_IN)),
                new KeyFrame(Duration.seconds(FADE_IN_OUT_TIME * 2),
                        new KeyValue(tilePane.opacityProperty(), 0, Interpolator.EASE_OUT)
                ));
        fadeOutTimeline.setCycleCount(1);
        fadeOutTimeline.setAutoReverse(false);
        fadeOutTimeline.setOnFinished(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                separator.setManaged(false);
                separator.setVisible(false);
                tilePane.setManaged(false);
                tilePane.setVisible(false);
            }
        });
        fadeOutTimeline.play();

        for (ChangeListener listener : onFoldedListeners) {
            listener.changed(null, false, true);
        }
    }

    private void sort(ObservableList<Node> nodes, Comparator<GameEntry> comparator) {
        nodes.sort(new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                return comparator.compare(((GameButton) o1).getEntry(), ((GameButton) o2).getEntry());
            }
        });
    }

    @Override
    public void sort() {
        ObservableList<Node> nodes = FXCollections.observableArrayList(
                tilePane.getChildren()
        );
        sort(nodes, entriesComparator);
        replaceChildrensAfterSort(nodes, new Runnable() {
            @Override
            public void run() {
                for (Node button : nodes) {
                    ((GameButton) button).hidePlaytime();
                    ((GameButton) button).hideReleaseDate();
                    ((GameButton) button).hideRating();
                }
            }
        });
    }

    public void addOnFoldedChangeListener(ChangeListener<Boolean> listener) {
        onFoldedListeners.add(listener);
    }

    @Override
    protected GameButton createGameButton(GameEntry newEntry) {
        return new TileGameButton(newEntry, tilePane, parentScene,SCREEN_WIDTH / 14,SCREEN_WIDTH / 14 * COVER_HEIGHT_WIDTH_RATIO);
    }

}
