
/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.ui.AbstractVisualizationPane;
import org.sleuthkit.autopsy.timeline.ui.TimeLineChart;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventNodeBase.show;
import static org.sleuthkit.autopsy.timeline.ui.detailview.MultiEventNodeBase.CORNER_RADII_3;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;

/**
 *
 */
public abstract class EventNodeBase<Type extends TimeLineEvent> extends StackPane {

    static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N NON-NLS
    static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N
    static final Image PLUS = new Image("/org/sleuthkit/autopsy/timeline/images/plus-button.png"); // NON-NLS //NOI18N
    static final Image MINUS = new Image("/org/sleuthkit/autopsy/timeline/images/minus-button.png"); // NON-NLS //NOI18N
    static final Image PIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--plus.png"); // NON-NLS //NOI18N
    static final Image UNPIN = new Image("/org/sleuthkit/autopsy/timeline/images/marker--minus.png"); // NON-NLS //NOI18N

    static final Map<EventType, Effect> dropShadowMap = new ConcurrentHashMap<>();

    static void configureActionButton(ButtonBase b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
//        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }

    final Type tlEvent;

    final EventNodeBase<?> parentNode;

    final SimpleObjectProperty<DescriptionLoD> descLOD = new SimpleObjectProperty<>();
    final SimpleObjectProperty<DescriptionVisibility> descVisibility = new SimpleObjectProperty<>();

    final DetailsChart chart;
    final Background highlightedBackground;
    final Background defaultBackground;
    final Color evtColor;

    final Label countLabel = new Label();
    final Label descrLabel = new Label();
    final ImageView hashIV = new ImageView(HASH_PIN);
    final ImageView tagIV = new ImageView(TAG);

    final HBox controlsHBox = new HBox(5);
    final HBox infoHBox = new HBox(5, descrLabel, countLabel, hashIV, tagIV, controlsHBox);

    private final Tooltip tooltip = new Tooltip(Bundle.EventBundleNodeBase_toolTip_loading());

    private Timeline timeline;
    private Button pinButton;
    private final Border SELECTION_BORDER;
    final ImageView eventTypeImageView = new ImageView();

    EventNodeBase(Type ievent, EventNodeBase<?> parent, DetailsChart chart) {
        this.chart = chart;
        this.tlEvent = ievent;
        this.parentNode = parent;
        eventTypeImageView.setImage(getEventType().getFXImage());

        descrLabel.setGraphic(eventTypeImageView);

        if (chart.getController().getEventsModel().getEventTypeZoom() == EventTypeZoomLevel.SUB_TYPE) {
            evtColor = getEventType().getColor();
        } else {
            evtColor = getEventType().getBaseType().getColor();
        }
        SELECTION_BORDER = new Border(new BorderStroke(evtColor.darker().desaturate(), BorderStrokeStyle.SOLID, CORNER_RADII_3, new BorderWidths(2)));

        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        highlightedBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1.1, 1.1, .3), CORNER_RADII_3, Insets.EMPTY));
        descVisibility.addListener(observable -> setDescriptionVisibiltiyImpl(descVisibility.get()));
//        descVisibility.set(DescriptionVisibility.SHOWN); //trigger listener for initial value
        setBackground(defaultBackground);

        //set up mouse hover effect and tooltip
        setOnMouseEntered(mouseEntered -> {
            Tooltip.uninstall(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            showHoverControls(true);
            toFront();
        });
        setOnMouseExited(mouseExited -> {
            showHoverControls(false);
            if (parentNode != null) {
                parentNode.showHoverControls(true);
            } else {
                Tooltip.install(chart.asNode(), AbstractVisualizationPane.getDefaultTooltip());
            }
        });
        setOnMouseClicked(new ClickHandler());
        show(controlsHBox, false);
    }

    public Type getEvent() {
        return tlEvent;
    }

    public abstract TimeLineChart<DateTime> getChart();

    /**
     * @param w the maximum width the description label should have
     */
    public void setMaxDescriptionWidth(double w) {
        descrLabel.setMaxWidth(w);
    }

    public abstract List<EventNodeBase<?>> getSubNodes();

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    public void applySelectionEffect(boolean applied) {
        setBorder(applied ? SELECTION_BORDER : null);
    }

    protected void layoutChildren() {
        super.layoutChildren();
    }

    /**
     * Install whatever buttons are visible on hover for this node. likes
     * tooltips, this had a surprisingly large impact on speed of loading the
     * chart
     */
    void installActionButtons() {
        if (pinButton == null) {
            pinButton = new Button();
            controlsHBox.getChildren().add(pinButton);
            configureActionButton(pinButton);
        }
    }

    void showHoverControls(final boolean showControls) {
        Effect dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(-10, eventType.getColor()));
        setEffect(showControls ? dropShadow : null);
        installTooltip();
        enableTooltip(showControls);
        installActionButtons();

        TimeLineController controller = getChart().getController();

        if (controller.getPinnedEvents().contains(tlEvent)) {
            pinButton.setOnAction(actionEvent -> {
                new UnPinEventAction(controller, tlEvent).handle(actionEvent);
                showHoverControls(true);
            });
            pinButton.setGraphic(new ImageView(UNPIN));
        } else {
            pinButton.setOnAction(actionEvent -> {
                new PinEventAction(controller, tlEvent).handle(actionEvent);
                showHoverControls(true);
            });
            pinButton.setGraphic(new ImageView(PIN));
        }

        show(controlsHBox, showControls);
        if (parentNode != null) {
            parentNode.showHoverControls(false);
        }
    }

    abstract void installTooltip();

    void enableTooltip(boolean toolTipEnabled) {
        if (toolTipEnabled) {
            Tooltip.install(this, tooltip);
        } else {
            Tooltip.uninstall(this, tooltip);
        }
    }

    final EventType getEventType() {
        return tlEvent.getEventType();
    }

    long getStartMillis() {
        return tlEvent.getStartMillis();
    }

    final double getLayoutXCompensation() {
        return parentNode != null
                ? getChart().getXAxis().getDisplayPosition(new DateTime(parentNode.getStartMillis()))
                : 0;
    }

    abstract String getDescription();

    void animateTo(double xLeft, double yTop) {
        if (timeline != null) {
            timeline.stop();
            Platform.runLater(this::requestChartLayout);
        }
        timeline = new Timeline(new KeyFrame(Duration.millis(100),
                new KeyValue(layoutXProperty(), xLeft),
                new KeyValue(layoutYProperty(), yTop))
        );
        timeline.setOnFinished(finished -> Platform.runLater(this::requestChartLayout));
        timeline.play();
    }

    abstract void requestChartLayout();

    void setDescriptionVisibility(DescriptionVisibility get) {
        descVisibility.set(get);
    }

    abstract void setDescriptionVisibiltiyImpl(DescriptionVisibility get);

    boolean hasDescription(String other) {
        return this.getDescription().startsWith(other);
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    synchronized void applyHighlightEffect(boolean applied) {
        if (applied) {
            descrLabel.setStyle("-fx-font-weight: bold;"); // NON-NLS
            setBackground(highlightedBackground);
        } else {
            descrLabel.setStyle("-fx-font-weight: normal;"); // NON-NLS
            setBackground(defaultBackground);
        }
    }

    void applyHighlightEffect() {
        applyHighlightEffect(true);
    }

    void clearHighlightEffect() {
        applyHighlightEffect(false);
    }

    abstract Collection<Long> getEventIDs();

    abstract EventHandler<MouseEvent> getDoubleClickHandler();

    abstract Collection<? extends Action> getActions();

    static class PinEventAction extends Action {

        @NbBundle.Messages({"PinEventAction.text=Pin"})
        PinEventAction(TimeLineController controller, TimeLineEvent event) {
            super(Bundle.PinEventAction_text());
            setEventHandler(actionEvent -> controller.pinEvent(event));
            setGraphic(new ImageView(PIN));
        }
    }

    static class UnPinEventAction extends Action {

        @NbBundle.Messages({"UnPinEventAction.text=Unpin"})
        UnPinEventAction(TimeLineController controller, TimeLineEvent event) {
            super(Bundle.UnPinEventAction_text());
            setEventHandler(actionEvent -> controller.unPinEvent(event));
            setGraphic(new ImageView(UNPIN));
        }
    }

    /**
     * event handler used for mouse events on {@link EventNodeBase}s
     */
    class ClickHandler implements EventHandler<MouseEvent> {

        private ContextMenu contextMenu;

        @Override
        public void handle(MouseEvent t) {
            if (t.getButton() == MouseButton.PRIMARY) {
                if (t.getClickCount() > 1) {
                    getDoubleClickHandler().handle(t);
                } else if (t.isShiftDown()) {
                    chart.getSelectedNodes().add(EventNodeBase.this);
                } else if (t.isShortcutDown()) {
                    chart.getSelectedNodes().removeAll(EventNodeBase.this);
                } else {
                    chart.getSelectedNodes().setAll(EventNodeBase.this);
                }
                t.consume();
            } else if (t.getButton() == MouseButton.SECONDARY) {
                ContextMenu chartContextMenu = chart.getChartContextMenu(t);
                if (contextMenu == null) {
                    contextMenu = new ContextMenu();
                    contextMenu.setAutoHide(true);

                    contextMenu.getItems().addAll(ActionUtils.createContextMenu(getActions()).getItems());

                    contextMenu.getItems().add(new SeparatorMenuItem());
                    contextMenu.getItems().addAll(chartContextMenu.getItems());
                }
                contextMenu.show(EventNodeBase.this, t.getScreenX(), t.getScreenY());
                t.consume();
            }
        }

    }

}
