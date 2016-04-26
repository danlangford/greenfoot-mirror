/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2012,2013,2014,2015  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.editor.stride;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bluej.parser.AssistContent;
import bluej.parser.AssistContent.ParamInfo;
import bluej.parser.ConstructorCompletion;
import bluej.prefmgr.PrefMgr;
import bluej.stride.framedjava.ast.links.PossibleLink;
import bluej.stride.framedjava.ast.links.PossibleKnownMethodLink;
import bluej.stride.framedjava.ast.links.PossibleMethodUseLink;
import bluej.stride.framedjava.ast.links.PossibleTypeLink;
import bluej.stride.framedjava.ast.links.PossibleVarLink;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableStringValue;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.swing.SwingUtilities;

import bluej.utility.javafx.SharedTransition;
import bluej.utility.javafx.binding.ViewportHeightBinding;
import threadchecker.OnThread;
import threadchecker.Tag;
import bluej.Config;
import bluej.editor.stride.ErrorOverviewBar.ErrorInfo;
import bluej.editor.stride.ErrorOverviewBar.ErrorState;
import bluej.editor.stride.FXTabbedEditor.CodeCompletionState;
import bluej.parser.AssistContent.CompletionKind;
import bluej.parser.PrimitiveTypeCompletion;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.PackageOrClass;
import bluej.pkgmgr.JavadocResolver;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.Target;
import bluej.stride.framedjava.ast.ASTUtility;
import bluej.stride.framedjava.ast.JavaFragment.PosInSourceDoc;
import bluej.stride.slots.LinkedIdentifier;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.elements.ClassElement;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.MethodWithBodyElement;
import bluej.stride.framedjava.elements.NormalMethodElement;
import bluej.stride.framedjava.elements.TopLevelCodeElement;
import bluej.stride.framedjava.errors.CodeError;
import bluej.stride.framedjava.errors.ErrorAndFixDisplay;
import bluej.stride.framedjava.frames.CallFrame;
import bluej.stride.framedjava.frames.CodeFrame;
import bluej.stride.framedjava.frames.ConstructorFrame;
import bluej.stride.framedjava.frames.GreenfootFrameCategory;
import bluej.stride.framedjava.frames.GreenfootFrameDictionary;
import bluej.stride.framedjava.frames.GreenfootFrameUtil;
import bluej.stride.framedjava.frames.MethodFrameWithBody;
import bluej.stride.framedjava.frames.NormalMethodFrame;
import bluej.stride.framedjava.frames.TopLevelFrame;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.generic.AssistContentThreadSafe;
import bluej.stride.generic.CanvasParent;
import bluej.stride.generic.Frame;
import bluej.stride.generic.Frame.ShowReason;
import bluej.stride.generic.Frame.View;
import bluej.stride.generic.FrameCanvas;
import bluej.stride.generic.FrameCursor;
import bluej.stride.generic.FrameDictionary;
import bluej.stride.generic.FrameState;
import bluej.stride.generic.InteractionManager;
import bluej.stride.generic.RecallableFocus;
import bluej.stride.generic.SuggestedFollowUpDisplay;
import bluej.stride.operations.UndoRedoManager;
import bluej.stride.slots.EditableSlot;
import bluej.utility.Debug;
import bluej.utility.ImportHelper;
import bluej.utility.Utility;
import bluej.utility.javafx.FXConsumer;
import bluej.utility.javafx.FXRunnable;
import bluej.utility.javafx.JavaFXUtil;

/**
 * The big central editor class for the frame editor.  The frames analogue of MoeEditor.
 *
 * This class contains all the coordinating functionality for each frame editor.  It is exposed to sub-elements
 * (frames, slots, etc) via the InteractionManager interface, so that class is a good place
 * to understand the public interface of this class.
 */
public @OnThread(Tag.FX) class FrameEditorTab extends Tab implements InteractionManager
{
    private final static List<Future<List<AssistContentThreadSafe>>> popularImports = new ArrayList<>();
    private static Future<List<AssistContentThreadSafe>> javaLangImports;
    private static List<AssistContentThreadSafe> prims;
    // We keep track ourselves of which item is focused.  Only focusable things in the editor
    // should be frame cursors and slots:
    private final SimpleObjectProperty<CursorOrSlot> focusedItem = new SimpleObjectProperty<>(null);
    private final TopLevelCodeElement initialSource;
    // Name of the top-level class/interface
    private final StringProperty nameProperty = new SimpleStringProperty(); 
    private final List<Future<List<AssistContentThreadSafe>>> importedTypes;
    private final FrameSelection selection = new FrameSelection(this);
    private final FrameEditor editor;
    private final UndoRedoManager undoRedoManager;
    private final ObjectProperty<View> viewProperty = new SimpleObjectProperty<>(View.NORMAL);
    private final EntityResolver projectResolver;
    private final FrameMenuManager menuManager = new FrameMenuManager(this);
    // The overlays (see individual class documentation)
    private WindowOverlayPane windowOverlayPane;
    private CodeOverlayPane codeOverlayPane;
    // A property to observe for when the scroll value changes on scroll pane:
    private Observable observableScroll;
    private ViewportHeightBinding viewportHeight;
    // Keeps track of whether the user has scrolled since there was last a focus change
    // (ignoring focus becoming null)
    private boolean manualScrolledSinceLastFocusChange = false;
    private CursorOrSlot focusOwnerDuringLastManualScroll = null;
    private TopLevelFrame<? extends TopLevelCodeElement> topLevelFrame;
    private ContextMenu menu;
    // The debugger controls (currently unused):
    private HBox controlPanel;
    private FrameCursor dragTarget;
    private ContentBorderPane contentRoot;
    private StackPane scrollAndOverlays;
    private StackPane scrollContent;
    private FXTabbedEditor parent;
    private ScrollPane scroll;
    private boolean selectingByDrag;
    private BirdseyeManager birdseyeManager;
    private Rectangle birdseyeSelection;
    private Pane birdseyeSelectionPane;
    // If escape is pressed, focus returns to where it was beforehand,
    // which is saved in these variables:
    private Node birdseyeDefaultFocusAfter;
    private FXRunnable birdseyeDefaultRequestFocusAfter;
    private Iterator<CodeError> errors;
    private SimpleBooleanProperty initialised = new SimpleBooleanProperty(false);
    private Frame stackHighlight;
    private EditableSlot showingUnderlinesFor = null;
    private ErrorOverviewBar errorOverviewBar;
    private boolean loading = false;
    // True when we are part way through an animation to set the scroll value:
    private boolean animatingScroll = false;
    private boolean anyButtonsPressed = false;
    private SharedTransition viewChange;
    private ErrorAndFixDisplay cursorErrorDisplay;
    private boolean inScrollTo = false;
    
    public FrameEditorTab(FXTabbedEditor parent, EntityResolver resolver, FrameEditor editor, TopLevelCodeElement initialSource)
    {
        this.parent = parent;
        this.projectResolver = resolver;
        this.importedTypes = new ArrayList<>();
        this.editor = editor;
        this.initialSource = initialSource;
        this.undoRedoManager = new UndoRedoManager(new FrameState(initialSource));

        if (javaLangImports == null)
            javaLangImports = importsUpdated("java.lang.*");

        if (popularImports.isEmpty())
        {

            popularImports.addAll(Arrays.asList(
                    "java.io.*",
                    "java.math.*",
                    "java.net.*",
                    "java.time.*",
                    "java.util.*",
                    "java.util.concurrent.*",
                    "java.util.function.*",
                    "java.util.stream.*",
                    Config.isGreenfoot() ? "greenfoot.*" : null
                    ).stream().filter(i -> i != null).map(this::importsUpdated).collect(Collectors.toList()));
        }
    }

    public static String blockSkipModifierLabel()
    {
        return Config.isMacOS() ? "\u2325" : "^";
    }

    private static boolean hasBlockSkipModifierPressed(KeyEvent event)
    {
        if (Config.isMacOS()) {
            return event.isAltDown();
        }
        else {
            return event.isControlDown();
        }
    }

    private static boolean isUselessDrag(FrameCursor dragTarget, List<Frame> dragging, boolean copying)
    {
        return !copying && (dragging.contains(dragTarget.getFrameAfter()) || dragging.contains(dragTarget.getFrameBefore()));
    }

    // Exception-safe wrapper for Future.get
    @OnThread(Tag.Any)
    private static <T> List<T> getFutureList(Future<List<T>> f)
    {
        try
        {
            return f.get();
        }
        catch (Exception e) {
            Debug.reportError("Problem looking up types", e);
            return Collections.emptyList();
        }
    }

    @OnThread(Tag.FX)
    private Future<List<AssistContentThreadSafe>> importsUpdated(final String x)
    {
        JavadocResolver javadocResolver = parent.getProject().getJavadocResolver();
        CompletableFuture<List<AssistContentThreadSafe>> f = new CompletableFuture<>();
        Utility.runBackground(() -> {
            try
            {
                f.complete(ImportHelper.getImportedTypes(x, javadocResolver));
            }
            catch (Throwable t)
            {
                f.complete(Collections.emptyList());
            }
        });
        return f;
    }

    // Must be run on FX thread
    // @Override
    @OnThread(Tag.FX)
    public void initialiseFX(final Scene scene)
    {
        if (initialised.get())
            return;

        // We put all the info in the graphic, so that we can use the graphic as a drag target:
        setText("");
        Label titleLabel = new Label(initialSource.getName());
        titleLabel.textProperty().bind(nameProperty);
        HBox tabHeader = new HBox(titleLabel);
        tabHeader.setAlignment(Pos.CENTER);
        tabHeader.setSpacing(3.0);
        tabHeader.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.MIDDLE)
            {
                setWindowVisible(false, false);
            }
        });
        setGraphic(tabHeader);
        JavaFXUtil.addStyleClass(this, "frame-editor-tab", initialSource.getStylePrefix() + "frame-editor-tab");

        JavaFXUtil.addChangeListener(focusedItem, focused -> {
            menuManager.setMenuItems(focused != null ? focused.getMenuItems(false) : selection != null ? selection.getEditMenuItems(false) : Collections.emptyMap());

            // Reset whether we've scrolled since last focus change, if this is a new owner:
            if (focused != null && !focused.equals(focusOwnerDuringLastManualScroll))
                manualScrolledSinceLastFocusChange = false;
        });

        selection.addChangeListener(c -> menuManager.setMenuItems(focusedItem.get() != null ? focusedItem.get().getMenuItems(false) : Collections.emptyMap()));

        contentRoot = new ContentBorderPane();
        JavaFXUtil.addStyleClass(contentRoot, "frame-editor-tab-content", initialSource.getStylePrefix() + "frame-editor-tab-content");
        scrollAndOverlays = new StackPane();
        windowOverlayPane = new WindowOverlayPane();
        scroll = new ScrollPane();
        scroll.getStyleClass().add("frame-editor-scroll-pane");
        scroll.setFitToWidth(true);
        observableScroll = scroll.vvalueProperty();
        viewportHeight = new ViewportHeightBinding(scroll);

        scrollAndOverlays.getChildren().addAll(scroll, windowOverlayPane.getNode());

        // Make class block fill window width:
        scroll.setFitToWidth(true);

        JavaFXUtil.addChangeListener(scroll.vvalueProperty(), v -> {
            if (!animatingScroll)
            {
                manualScrolledSinceLastFocusChange = true;
                // Focus owner may be null, if user is scrolling window on mouse over
                // without focusing.  In this case, we want to keep track of previous
                // focus owner
                if (focusedItem.get() != null)
                    focusOwnerDuringLastManualScroll = focusedItem.get();
            }
        });

        scrollAndOverlays.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            final FrameCursor focusedCursor = getFocusedCursor();
            boolean blockCursorFocused = focusedCursor != null;

                switch (event.getCode()) {
                case UP:
                    if (blockCursorFocused) {
                        FrameCursor c;
                        if (event.isShiftDown() && viewProperty.get() != View.JAVA_PREVIEW) {
                            c = focusedCursor.getPrevSkip();
                            selection.toggleSelectUp(focusedCursor.getFrameBefore());
                        }
                        else if (hasBlockSkipModifierPressed(event) || (viewProperty.get() == View.JAVA_PREVIEW && focusedCursor.getFrameBefore() != null && !focusedCursor.getFrameBefore().isFrameEnabled())) {
                            selection.clear();
                            c = focusedCursor.getPrevSkip();
                        }
                        else {
                            selection.clear();
                            c = focusedCursor.getParentCanvas().getPrevCursor(focusedCursor, true);
                        }

                        if (c != null) {
                            c.requestFocus();
                        }
                        event.consume();
                    }
                    break;
                case DOWN:
                    if (blockCursorFocused) {
                        FrameCursor c;
                        if (event.isShiftDown() && viewProperty.get() != View.JAVA_PREVIEW) {
                            c = focusedCursor.getNextSkip();
                            selection.toggleSelectDown(focusedCursor.getFrameAfter());
                        }
                        else if (hasBlockSkipModifierPressed(event) || (viewProperty.get() == View.JAVA_PREVIEW && focusedCursor.getFrameAfter() != null && !focusedCursor.getFrameAfter().isFrameEnabled())) {
                            selection.clear();
                            c = focusedCursor.getNextSkip();
                        }
                        else {
                            selection.clear();
                            c = focusedCursor.getParentCanvas().getNextCursor(focusedCursor, true);
                        }

                        if (c != null) {
                            c.requestFocus();
                        }
                        event.consume();
                    }
                    break;
                case HOME:
                    if (blockCursorFocused) {
                        topLevelFrame.focusOnBody(TopLevelFrame.BodyFocus.TOP);
                        selection.clear();
                        event.consume();
                    }
                    break;
                case END:
                    if (blockCursorFocused) {
                        topLevelFrame.focusOnBody(TopLevelFrame.BodyFocus.BOTTOM);
                        selection.clear();
                        event.consume();
                    }
                    break;
                case LEFT:
                    if (blockCursorFocused) {
                        // Is there a frame above the cursor within the same canvas?
                        Frame frameBefore = focusedCursor.getFrameBefore();
                        if (frameBefore != null) {
                            // Yes; see if it can be focused
                            if ( !frameBefore.focusFrameEnd(false) ) {
                                // If it can't (e.g. blank frame), go for the cursor above it:
                                focusedCursor.getUp().requestFocus();
                            }
                        }
                        else {
                            // We must be top cursor in the canvas; so go to end of item before
                            // us within our enclosing frame:
                            Frame enclosingFrame = focusedCursor.getEnclosingFrame();
                            if (enclosingFrame != null) {
                                enclosingFrame.focusLeft(focusedCursor.getParentCanvas());
                            }
                            else
                            {
                                // Nowhere to go; don't think this should even be possible?
                                Debug.message("No enclosing frame on cursor");
                            }
                        }
                        selection.clear();
                        event.consume();
                    }
                    break;
                    // Block right key when focused on BlockCursor:
                case RIGHT:
                    if (blockCursorFocused) {
                        // Is there a frame below the cursor?
                        Frame frame = focusedCursor.getFrameAfter();
                        if (frame != null) {
                            if ( !frame.focusFrameStart() ) {
                                // If nothing to focus on in the frame (e.g. blank),
                                // focus the cursor afterwards:
                                focusedCursor.getParentCanvas().getNextCursor(focusedCursor, true).requestFocus();
                            }
                        }
                        else {
                            // No frame beneath the cursor, we must be bottom cursor in canvas
                            Frame enclosingFrame = focusedCursor.getEnclosingFrame();
                            if (enclosingFrame != null) {
                                enclosingFrame.focusRight(focusedCursor.getParentCanvas());
                            }
                            else
                            {
                                // Nowhere to go; don't think this should even be possible?
                                Debug.message("No enclosing frame on cursor");
                            }
                        }
                        selection.clear();
                        event.consume();
                    }
                    break;
                default:
                    if (event.getCode() == getKey(ShortcutKey.YES_ANYWHERE))
                    {
                        SuggestedFollowUpDisplay.shortcutTyped(this, ShortcutKey.YES_ANYWHERE);
                    }
                    else if (event.getCode() == getKey(ShortcutKey.NO_ANYWHERE))
                    {
                        SuggestedFollowUpDisplay.shortcutTyped(this, ShortcutKey.NO_ANYWHERE);
                    }
                    break;
                }
        });

        scrollAndOverlays.addEventFilter(MouseEvent.ANY, e -> {
            anyButtonsPressed = e.isPrimaryButtonDown() || e.isSecondaryButtonDown() || e.isMiddleButtonDown();
        });

        controlPanel = new HBox();

        Button stepButton = new Button("Step");
        //stepButton.setOnAction(e -> SwingUtilities.invokeLater(() -> editor.step()));

        Button continueButton = new Button("Continue");
        //continueButton.setOnAction(e -> SwingUtilities.invokeLater(() -> editor.cont()));

        controlPanel.getChildren().addAll(stepButton, continueButton);
        controlPanel.setSpacing(10.0);


        // Add all to scene
        // menuBar.setUseSystemMenuBar(true);
        // Remove menu for demo:
        // p.setTop(menuBar);
        // menuHeight = menuBar.heightProperty();
        contentRoot.setCenter(scrollAndOverlays);
        //p.setTop(controlPanel);

        // We must create code overlay before we create the ClassFrame:
        codeOverlayPane = new CodeOverlayPane();

        // Need to create scrollContent before createTopLevelFrame as they will
        // call keepNodeVisibleWhenFocused:
        scrollContent = new StackPane();


        errorOverviewBar = new ErrorOverviewBar(this, scrollContent, this::nextError);
        JavaFXUtil.addChangeListener(errorOverviewBar.showingCount(), count -> {
            JavaFXUtil.setStyleClass(this, count.intValue() > 0, "bj-tab-error");
        });
        contentRoot.setRight(errorOverviewBar);

        loading = true;
        topLevelFrame = initialSource.createTopLevelFrame(this);
        topLevelFrame.regenerateCode();
        TopLevelCodeElement el = topLevelFrame.getCode();
        el.updateSourcePositions();
        loading = false;
        nameProperty.bind(topLevelFrame.nameProperty());
        // Whenever name changes, trigger recompile even without leaving slot:
        JavaFXUtil.addChangeListener(topLevelFrame.nameProperty(), n -> {
            editor.codeModified();
            EventQueue.invokeLater(() -> {
                try {
                    editor.save();
                } catch (IOException e) {
                    Debug.reportError("Problem saving after name change", e);
                }
            });
        });

        JavaFXUtil.addChangeListener(viewProperty, menuManager::notifyView);
        birdseyeSelection = new Rectangle();
        JavaFXUtil.addStyleClass(birdseyeSelection, "birdseye-selection");
        birdseyeSelectionPane = new Pane(birdseyeSelection);
        birdseyeSelectionPane.setVisible(false);
        birdseyeSelectionPane.setMouseTransparent(false);
        birdseyeSelectionPane.setOnMouseClicked(e -> {
            FrameCursor clickTarget = birdseyeManager.getClickedTarget(e.getSceneX(), e.getSceneY());
            // Clicked somewhere outside a selection or has expanded; either way, disable the view
            if (clickTarget == null)
                disableBirdseyeView();
            else
                disableBirdseyeView(clickTarget.getNode(), clickTarget::requestFocus);
            e.consume();
        });
        birdseyeSelectionPane.setOnMouseMoved(e -> {
            birdseyeSelectionPane.setCursor(birdseyeManager.canClick(e.getSceneX(), e.getSceneY()) ? Cursor.HAND : Cursor.DEFAULT);
        });

        scrollContent.getChildren().addAll(topLevelFrame.getNode(), codeOverlayPane.getNode(), birdseyeSelectionPane );
        scroll.setContent(scrollContent);

        setContent(contentRoot);
        // Consume mouse pressed events at the root, to stop them falling to the tab pane,
        // which requests focus on its tab header (not something we ever want):
        contentRoot.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);

        // Make class at least as high as scroll view:

        topLevelFrame.bindMinHeight(viewportHeight);

        // TEMP scaling for taking hi-res screenshots
        // p.getTransforms().add(new Scale(2.0, 2.0));

        contentRoot.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode())
            {
                case Y:
                    if (!Config.isMacOS() && event.isShortcutDown() && !event.isShiftDown())
                    {
                        redo();
                        event.consume();
                    }
                    break;
                case Z:
                    if (event.isShortcutDown())
                    {
                        if (!event.isShiftDown())
                        {
                            if (undoRedoManager.isRecording()) {
                                endRecordingState(focusedItem.get().getRecallableFocus());
                            }
                            undo();
                            event.consume();
                        }
                        else if (Config.isMacOS())
                        {
                            redo();
                            event.consume();
                        }
                    }
                    break;
                case UP:
                    if (viewProperty.get() == View.BIRDSEYE)
                    {
                        birdseyeManager.up();
                        calculateBirdseyeRectangle();
                        event.consume();
                    }
                    break;
                case DOWN:
                    if (viewProperty.get() == View.BIRDSEYE)
                    {
                        birdseyeManager.down();
                        calculateBirdseyeRectangle();
                        event.consume();
                    }
                    break;
                case ENTER:
                    if (viewProperty.get() == View.BIRDSEYE)
                    {
                        FrameCursor target = birdseyeManager.getCursorForCurrent();
                        disableBirdseyeView(target.getNode(), target::requestFocus);
                        event.consume();
                    }
                    break;
                case ESCAPE:
                    if (viewProperty.get() == View.JAVA_PREVIEW)
                    {
                        disableJavaPreview();
                        event.consume();
                    } else if (viewProperty.get() == View.BIRDSEYE)
                    {
                        disableBirdseyeView();
                        event.consume();
                    }
                    break;
            }
        });

        contentRoot.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isShortcutDown())
            {
                if (e.getDeltaY() > 0)
                    increaseFontSize();
                else
                    decreaseFontSize();

                e.consume();
            }
        });

        JavaFXUtil.addChangeListener(PrefMgr.strideFontSizeProperty(), s -> updateFontSize());
        updateFontSize();

        // This will call updateDisplays:
        regenerateAndReparse(null);

        // When imports change, we provide a new future to calculate the types:
        JavaFXUtil.bindMap(this.importedTypes, topLevelFrame.getImports(), this::importsUpdated);

        if (topLevelFrame != null)
        {
            saved();
            // Force generation of early errors on load:
            topLevelFrame.getCode().findEarlyErrors().count();
            Platform.runLater(this::updateDisplays);
        }
        
        initialised.set(true);
    }
    
    // package visible.
    // Sets font size back to default
    void resetFontSize()
    {
        PrefMgr.strideFontSizeProperty().set(PrefMgr.DEFAULT_STRIDE_FONT_SIZE);
    }

    public void searchLink(PossibleLink link, FXConsumer<Optional<LinkedIdentifier>> paramCallback)
    {
        // I know instanceof is nasty, but it makes more sense to have the logic here than
        // in the Possible*Link classes.  Doing this in lieu of algebraic data types:

        Consumer<Optional<LinkedIdentifier>> callback = ol -> Platform.runLater(() -> paramCallback.accept(ol));

        if (link instanceof PossibleTypeLink)
        {
            String name = ((PossibleTypeLink)link).getTypeName();
            SwingUtilities.invokeLater(() -> {
                Project project = parent.getProject();
                bluej.pkgmgr.Package pkg = project.getPackage("");
                if (pkg.getAllClassnamesWithSource().contains(name))
                {
                    Target t = pkg.getTarget(name);
                    if (t instanceof ClassTarget)
                    {
                        callback.accept(Optional.of(new LinkedIdentifier(name, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> {
                            link.getSlot().removeAllUnderlines();
                            SwingUtilities.invokeLater(() -> ((ClassTarget) t).open());
                        })));
                        return;
                    }
                } else
                {
                    TopLevelCodeElement code = topLevelFrame.getCode();
                    if (code != null)
                    {
                        PackageOrClass resolved = code.getResolver().resolvePackageOrClass(name, null);
                        // Slightly hacky way of deciding if it's in the standard API:
                        if (resolved.getName().startsWith("java.") || resolved.getName().startsWith("javax."))
                        {
                            callback.accept(Optional.of(new LinkedIdentifier(name, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> parent.openJavaCoreDocTab(resolved.getName()))));
                            return;
                        } else if (resolved.getName().startsWith("greenfoot."))
                        {
                            callback.accept(Optional.of(new LinkedIdentifier(name, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> parent.openGreenfootDocTab(resolved.getName()))));
                            return;
                        }
                    }
                }
                callback.accept(Optional.empty());
            });
        }
        else if (link instanceof PossibleKnownMethodLink)
        {
            PossibleKnownMethodLink pml = (PossibleKnownMethodLink) link;
            final String qualClassName = pml.getQualClassName();
            final String urlSuffix = pml.getURLMethodSuffix();
            SwingUtilities.invokeLater(() -> searchMethodLink(link, qualClassName, pml.getDisplayName(), urlSuffix, callback));
        }
        else if (link instanceof PossibleMethodUseLink)
        {
            PossibleMethodUseLink pmul = (PossibleMethodUseLink) link;
            SwingUtilities.invokeLater(() -> {
                // Need to find which method it is:
                List<AssistContent> candidates = editor.getAvailableMembers(topLevelFrame.getCode(), pmul.getSourcePositionSupplier().get(), Collections.singleton(CompletionKind.METHOD), true)
                    .stream()
                    .filter(ac -> ac.getName().equals(pmul.getMethodName()))
                    .collect(Collectors.toList());
                if (candidates.size() > 1)
                {
                    // Try to narrow down the list to those with the right number of parameters.
                    // but only if at least one match (otherwise leave them all in):
                    if (candidates.stream().anyMatch(ac -> ac.getParams().size() == pmul.getNumParams()))
                    {
                        candidates.removeIf(ac -> ac.getParams().size() != pmul.getNumParams());
                    }
                }

                // At this point, just pick the first in the list if any are available:
                if (candidates.size() >= 1)
                {
                    AssistContent ac = candidates.get(0);
                    String displayName = ac.getName() + "(" + ac.getParams().stream().map(ParamInfo::getUnqualifiedType).collect(Collectors.joining(", ")) + ")";
                    searchMethodLink(link, ac.getDeclaringClass(), displayName, PossibleKnownMethodLink.encodeSuffix(ac.getName(), Utility.mapList(ac.getParams(), ParamInfo::getQualifiedType)), callback);
                }
                else
                {
                    // Otherwise, can't find it anywhere:
                    callback.accept(Optional.empty());
                }
            });
        }
        else if (link instanceof PossibleVarLink)
        {
            final String name = ((PossibleVarLink)link).getVarName();
            final CodeElement el = ((PossibleVarLink) link).getUsePoint();
            FrameEditorTab ed = (FrameEditorTab)ASTUtility.getTopLevelElement(el).getEditor();
            if (ed == FrameEditorTab.this) {
                callback.accept(Optional.of(new LinkedIdentifier(name, link.getStartPosition(), link.getEndPosition(), link.getSlot(),  () -> el.show(ShowReason.LINK_TARGET))));
            }
            else {
                callback.accept(Optional.of(new LinkedIdentifier(name, link.getStartPosition(), link.getEndPosition(), link.getSlot(),  () -> {
                    parent.setWindowVisible(true, ed);
                    // TODO gets tricky here; what if editor hasn't been loaded yet?
                    el.show(ShowReason.LINK_TARGET);
                })));
            }
        }
    }

    @OnThread(Tag.Swing)
    private void searchMethodLink(PossibleLink link, String qualClassName, String methodDisplayName, String urlSuffix, Consumer<Optional<LinkedIdentifier>> callback)
    {
        Project project = parent.getProject();
        bluej.pkgmgr.Package pkg = project.getPackage("");
        if (pkg.getAllClassnamesWithSource().contains(qualClassName)) {
            Target t = pkg.getTarget(qualClassName);
            if (t instanceof ClassTarget) {
                callback.accept(Optional.of(new LinkedIdentifier(methodDisplayName, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> {
                    link.getSlot().removeAllUnderlines();
                    SwingUtilities.invokeLater(() -> ((ClassTarget) t).open());
                })));
                return;
            }
        }
        else {
            TopLevelCodeElement code = topLevelFrame.getCode();
            if (code != null) {
                PackageOrClass resolved = code.getResolver().resolvePackageOrClass(qualClassName, null);
                // Slightly hacky way of deciding if it's in the standard API:
                if (resolved.getName().startsWith("java.") || resolved.getName().startsWith("javax.")) {
                    callback.accept(Optional.of(new LinkedIdentifier(methodDisplayName, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> parent.openJavaCoreDocTab(resolved.getName(), urlSuffix))));
                    return;
                }
                else if (resolved.getName().startsWith("greenfoot."))
                {
                    callback.accept(Optional.of(new LinkedIdentifier(methodDisplayName, link.getStartPosition(), link.getEndPosition(), link.getSlot(), () -> parent.openGreenfootDocTab(resolved.getName(), urlSuffix))));
                    return;
                }
            }
        }
        callback.accept(Optional.empty());
    }

    void enableBirdseyeView()
    {
        if (viewProperty.get() == View.NORMAL && topLevelFrame.canDoBirdseye())
        {
            if (viewChange != null)
                viewChange.stop();
            viewChange = new SharedTransition();
            viewChange.addOnStopped(() -> JavaFXUtil.runAfter(Duration.millis(50), () -> {
                    birdseyeSelectionPane.setVisible(true);
                    calculateBirdseyeRectangle();
            }));

            birdseyeDefaultFocusAfter = scroll.getScene().getFocusOwner();
            birdseyeDefaultRequestFocusAfter = () -> { if (birdseyeDefaultFocusAfter != null) birdseyeDefaultFocusAfter.requestFocus(); };
            birdseyeManager = topLevelFrame.prepareBirdsEyeView(viewChange);

            viewProperty.set(View.BIRDSEYE);
            setupAnimateViewTo(View.NORMAL, View.BIRDSEYE, viewChange);

            viewChange.animateOver(Duration.millis(500));
        }
        else if (viewProperty.get() == View.JAVA_PREVIEW)
        {
            disableJavaPreview();
            enableBirdseyeView();
        }
    }

    void disableBirdseyeView(Node viewTarget, FXRunnable requestFocus)
    {
        if (viewProperty.get() == View.BIRDSEYE)
        {
            if (viewChange != null)
                viewChange.stop();
            viewChange = new SharedTransition();

            birdseyeSelectionPane.setVisible(false);
            birdseyeManager = null;
            FXRunnable remove = JavaFXUtil.addChangeListener(viewTarget.localToSceneTransformProperty(), t -> {
                if (!inScrollTo)
                    scrollTo(viewTarget, -200.0);
            });
            viewChange.addOnStopped(() -> {
                remove.run();
                if (requestFocus != null)
                {
                    requestFocus.run();
                }
            });
            viewProperty.set(View.NORMAL);
            setupAnimateViewTo(View.BIRDSEYE, View.NORMAL, viewChange);
            viewChange.animateOver(Duration.millis(500));
        }
    }

    void disableBirdseyeView()
    {
        disableBirdseyeView(birdseyeDefaultFocusAfter, birdseyeDefaultRequestFocusAfter);
    }

    void enableJavaPreview()
    {
        if (viewProperty.get() == View.NORMAL)
        {
            selection.clear();
            if (viewChange != null)
                viewChange.stop();
            viewChange = new SharedTransition();
            viewProperty.set(View.JAVA_PREVIEW);
            setupAnimateViewTo(View.NORMAL, View.JAVA_PREVIEW, viewChange);
            viewChange.animateOver(Duration.millis(3000));
        }
        else if (viewProperty.get() == View.BIRDSEYE)
        {
            disableBirdseyeView();
            enableJavaPreview();
        }
    }

    void disableJavaPreview()
    {
        if (viewProperty.get() == View.JAVA_PREVIEW)
        {
            if (viewChange != null)
                viewChange.stop();
            viewChange = new SharedTransition();
            viewProperty.set(View.NORMAL);
            setupAnimateViewTo(View.JAVA_PREVIEW, View.NORMAL, viewChange);
            viewChange.animateOver(Duration.millis(3000));
        }
    }

    private void setupAnimateViewTo(View oldView, View newView, SharedTransition animateProgress)
    {
        FrameCursor fixpoint = getFocusedCursor();
        double y = fixpoint == null ? 0 : (fixpoint.getSceneBounds().getMinY() - scroll.localToScene(scroll.getBoundsInLocal()).getMinY());

        topLevelFrame.getAllFrames().forEach(f -> f.setView(oldView, newView, animateProgress));

        if (fixpoint != null)
        {
            final FrameCursor finalFixpoint = fixpoint;
            FXRunnable remove = JavaFXUtil.addChangeListener(getFocusedCursor().getNode().localToSceneTransformProperty(), ignore -> {
                scrollTo(finalFixpoint.getNode(), -y);
            });
            // runLater, otherwise we have a step change at the end where we don't keep the cursor in the same spot:
            animateProgress.addOnStopped(() -> FXRunnable.runLater(remove));
        }

        parent.scheduleUpdateCatalogue(this, newView == View.NORMAL ? getFocusedCursor() : null, CodeCompletionState.NOT_POSSIBLE, false, newView, Collections.emptyList());
    }

    public void showCatalogue()
    {
        parent.showCatalogue();
    }

    public BooleanProperty cheatSheetShowingProperty()
    {
        return parent.catalogueShowingProperty();
    }
    
    public void focusWhenShown()
    {
        topLevelFrame.focusOnBody(TopLevelFrame.BodyFocus.BEST_PICK);
    }

    public void cancelFreshState()
    {
        topLevelFrame.getAllFrames().forEach(Frame::markNonFresh);
    }
    
    public void blockDragBegin(Frame b, double mouseSceneX, double mouseSceneY)
    {
        if (dragTarget != null)
        {
            throw new IllegalStateException("Drag begun while drag in progress");
        }
        
        if (viewProperty.get() == View.JAVA_PREVIEW)
        {
            // No dragging allowed while showing Java preview:
            return;
        }

        if (selection.contains(b))
        {
            // Drag the whole selection:
            parent.frameDragBegin(selection.getSelected(), mouseSceneX, mouseSceneY);
        }
        else
        {
            parent.frameDragBegin(Arrays.asList(b), mouseSceneX, mouseSceneY);
        }
    }

    public boolean isDragging()
    {
        return parent.isDragging();
    }

    // Called by mouse events
    public void blockDragEnd(boolean copying)
    {
        parent.frameDragEnd(copying);
        
    }

    // Called by TabbedEditor when drag ends on us as selected tab
    // package-visible
    void dragEndTab(List<Frame> dragSourceFrames, boolean copying)
    {
        // First, move the blocks:
        if (dragSourceFrames != null && !dragSourceFrames.isEmpty() && dragTarget != null) {
            // Check all of them can be dragged to new location:
            boolean canMove = true;
            for (Frame src : dragSourceFrames) {
                src.setDragSourceEffect(false);
                canMove &= dragTarget.getParentCanvas().acceptsType(src);
            }

            if (canMove && !isUselessDrag(dragTarget, dragSourceFrames, copying)) {
                beginRecordingState(dragTarget);
                performDrag(dragSourceFrames, copying);
                endRecordingState(dragTarget);
            }
            selection.clear();

            // Then stop showing cursor as drag target:
            dragTarget.stopShowAsDropTarget();
            dragTarget = null;
        }
    }

    private void performDrag(List<Frame> dragSourceFrames, boolean copying)
    {
        boolean shouldDisable = !dragTarget.getParentCanvas().getParent().getFrame().isFrameEnabled();

        // We must add blocks in reverse order after cursor:
        Collections.reverse(dragSourceFrames);
        if (!copying) {
            for (Frame src : dragSourceFrames) {
                src.getParentCanvas().removeBlock(src);
                dragTarget.insertBlockAfter(src);
                if (shouldDisable)
                    src.setFrameEnabled(false);
                modifiedFrame(src);
            }
        }
        else {
            List<CodeElement> elements = GreenfootFrameUtil.getElementsForMultipleFrames(dragSourceFrames);
            for (CodeElement codeElement : elements) {
                final Frame frame = codeElement.createFrame(this);
                dragTarget.insertBlockAfter(frame);
                if (shouldDisable)
                    frame.setFrameEnabled(false);
            }
        }
    }    

    // Called by mouse events
    protected void draggedTo(double sceneX, double sceneY, boolean copying)
    {
        parent.draggedTo(sceneX, sceneY, copying);
    }

    // Called by TabbedEditor when this tab is the selected one during a drag
    //package-visible
    void draggedToTab(List<Frame> dragSourceFrames, double sceneX, double sceneY, boolean copying)
    {
        FrameCursor newDragTarget = topLevelFrame.findCursor(sceneX, sceneY, null, null, dragSourceFrames, true, true);

        if (newDragTarget != null && dragTarget != newDragTarget) {
            if (dragTarget != null) {
                dragTarget.stopShowAsDropTarget();
                dragTarget = null;
            }
            boolean src = isUselessDrag(newDragTarget, dragSourceFrames, copying);
            boolean acceptsAll = true;
            for (Frame srcFrame : dragSourceFrames) {
                acceptsAll &= newDragTarget.getParentCanvas().acceptsType(srcFrame);
            }
            newDragTarget.showAsDropTarget(src, acceptsAll, copying);
            dragTarget = newDragTarget;
        }
        
        if (dragTarget != null)
        {
            dragTarget.updateDragCopyState(copying);
        }
    }

    // Called by TabbedEditor when we are no longer the target tab during a drag
    //package-visible
    void draggedToAnotherTab()
    {
        if (dragTarget != null)
        {
            dragTarget.stopShowAsDropTarget();
            dragTarget = null;
        }
    }
    
    @Override
    public void clickNearestCursor(double sceneX, double sceneY, boolean shiftDown)
    {
        FrameCursor target = topLevelFrame.findCursor(sceneX, sceneY, null, null, null, false, true);
        if (target != null) {
            if (shiftDown && viewProperty.get() != View.JAVA_PREVIEW)
            {
                // We need to calculate the other end of the selection.
                // If there's no selection, it's the currently focused cursor
                // If there is a selection, it's the end which is not the currently focused cursor
                FrameCursor anchor;
                if (selection.getSelected().size() == 0)
                {
                    anchor = getFocusedCursor();
                }
                else
                {
                    anchor = (selection.getCursorAfter() == getFocusedCursor()) ? selection.getCursorBefore() : selection.getCursorAfter();
                }

                if (getFocusedCursor() == null || target.getParentCanvas() != anchor.getParentCanvas())
                {
                    return; // Ignore the click; invalid action
                }

                selection.set(target.getParentCanvas().framesBetween(anchor, target));
                target.requestFocus();
            }
            else
            {
                target.requestFocus();
                // Since it wasn't a shift-click, clear the selection:
                selection.clear();
            }
        }
    }

    public FrameDictionary<GreenfootFrameCategory> getDictionary()
    {
        return GreenfootFrameDictionary.getDictionary();
    }

    @Override
    public void setupFrameCursor(FrameCursor f)
    {
        // We use "simple press-drag-release" here, so the events are all delivered to the original cursor:

        f.getNode().setOnDragDetected(e -> {
            selectingByDrag = true;
            e.consume();
        });

        f.getNode().setOnMouseDragged(e -> {
            if (!selectingByDrag || viewProperty.get() == View.JAVA_PREVIEW)
                return;

            FrameCanvas fCanvas = f.getParentCanvas();

            FrameCursor closest = fCanvas.getParent().findCursor(e.getSceneX(), e.getSceneY(), fCanvas.getFirstCursor(), fCanvas.getLastCursor(), null, true, false);
            if (closest != null)
                selection.set(fCanvas.framesBetween(closest, f));

            e.consume();
        });

        f.getNode().setOnMouseReleased(e -> {
            if (selectingByDrag)
            {
                selectingByDrag = false;
                e.consume();
            }
        });

        JavaFXUtil.addChangeListener(f.getNode().focusedProperty(), new FXConsumer<Boolean>()
        {

            private FXRunnable cancelTimer;

            public void accept(Boolean focused)
            {
                parent.scheduleUpdateCatalogue(FrameEditorTab.this, focused ? f : null, CodeCompletionState.NOT_POSSIBLE, !selection.getSelected().isEmpty(), getView(), Collections.emptyList());

                if (cancelTimer != null)
                {
                    cancelTimer.run();
                    cancelTimer = null;
                }

                if (!focused)
                {
                    hideError();
                } else
                {
                    cancelTimer = JavaFXUtil.runRegular(Duration.millis(1000), this::updateFocusedDisplay);
                }
            }

            private void hideError()
            {
                if (cursorErrorDisplay != null)
                {
                    cursorErrorDisplay.hide();
                    cursorErrorDisplay = null;
                }
            }

            private void updateFocusedDisplay()
            {
                if (!f.getNode().focusedProperty().get())
                {
                    // Must have lost focus while we were being scheduled
                    hideError();
                    return;
                }

                // We favour the frame after the cursor, in the case that they both have errors:
                Optional<CodeError> maybeErr = Optional.ofNullable(f.getFrameAfter()).flatMap(fr -> fr.getCurrentErrors().findFirst());
                if (maybeErr.isPresent())
                {
                    if (cursorErrorDisplay != null && maybeErr.get() == cursorErrorDisplay.getError())
                    {
                        return;
                    } else
                    {
                        hideError();
                    }

                    cursorErrorDisplay = new ErrorAndFixDisplay(FrameEditorTab.this, "Below: ", maybeErr.get(), null);
                    cursorErrorDisplay.showAbove(f.getNode());
                } else
                {
                    // If not after, then check before:
                    maybeErr = Optional.ofNullable(f.getFrameBefore()).flatMap(fr -> fr.getCurrentErrors().findFirst());
                    if (maybeErr.isPresent())
                    {
                        if (cursorErrorDisplay != null && maybeErr.get() == cursorErrorDisplay.getError())
                        {
                            return;
                        } else
                        {
                            hideError();
                        }

                        cursorErrorDisplay = new ErrorAndFixDisplay(FrameEditorTab.this, "Above: ", maybeErr.get(), null);
                        cursorErrorDisplay.showBelow(f.getNode());
                    } else
                    {
                        // And if neither before nor after, check the enclosing frame:
                        maybeErr = Optional.ofNullable(f.getParentCanvas().getParent()).map(CanvasParent::getFrame).flatMap(fr -> fr.getCurrentErrors().findFirst());
                        // Only show the error if it's visible (it may not be, if the enclosing frame is still fresh):
                        if (maybeErr.isPresent() && maybeErr.get().visibleProperty().get())
                        {
                            if (cursorErrorDisplay != null && maybeErr.get() == cursorErrorDisplay.getError())
                            {
                                return;
                            } else
                            {
                                hideError();
                            }

                            cursorErrorDisplay = new ErrorAndFixDisplay(FrameEditorTab.this, "Enclosing frame: ", maybeErr.get(), null);
                            cursorErrorDisplay.showBelow(f.getNode());
                        } else
                        {
                            hideError();
                        }
                    }
                }
            }
        });
        
        // Add same focus listeners as slot component:
        setupFocusable(new CursorOrSlot(f), f.getNode());
    }

    @Override
    public void setupFrame(final Frame f)
    {
        JavaFXUtil.listenForContextMenu(f.getNode(), (x, y) -> {
            if (viewProperty.get() != View.NORMAL)
                return true;

            if (!selection.contains(f)) {
                selection.set(Arrays.asList(f));
            }

            if (menu != null) {
                menu.hide();
            }
            menu = selection.getContextMenu();
            if (menu != null) {
                menu.show(f.getNode(), x, y);
                return true;
            }
            return false;
        });

        // If we don't consume mouse pressed events, then the mouse pressed can get transferred
        // to the scroll pane, which then also messes up the click handling.  So this is needed:
        f.getNode().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (getFocusedCursor() != null && e.isShiftDown())
                e.consume();
        });


        f.getNode().addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress())
            {
                if (f.leftClicked(event.getSceneX(), event.getSceneY(), event.isShiftDown())) {
                    event.consume();
                }
            }
        });
        
        
        // We use "simple press-drag-release" here, so the events are all delivered to the original cursor:
        
        f.getNode().setOnDragDetected(event -> {
            blockDragBegin(f, event.getSceneX(), event.getSceneY());
            event.consume();
        });
        
        f.getNode().setOnMouseDragged(event -> {
            draggedTo(event.getSceneX(), event.getSceneY(), JavaFXUtil.isDragCopyKeyPressed(event));
            event.consume();
        });
        
        f.getNode().setOnMouseReleased(event -> {
            if (!isDragging())
                return;

            // Make sure we're using the latest position:
            draggedTo(event.getSceneX(), event.getSceneY(), JavaFXUtil.isDragCopyKeyPressed(event));
            blockDragEnd(JavaFXUtil.isDragCopyKeyPressed(event));
            event.consume();
        });
    }

    @Override
    public FrameCursor createCursor(FrameCanvas parent)
    {
        return new FrameCursor(this, parent);
    }

    @OnThread(Tag.Any)
    public TopLevelCodeElement getSource()
    {
        if (topLevelFrame == null) {
            // classFrame can be null at points in the loading sequence,
            // so just return null if we are called at that awkward time:
            return null;
        }
        return topLevelFrame.getCode();
    }
    
    private void regenerateCode()
    {
        if (topLevelFrame != null)
            topLevelFrame.regenerateCode();
    }

    // Flag existing errors as old, generally happens just prior to compilation
    // They will be removed by a later call to removeOldErrors
    public void flagErrorsAsOld()
    {
        topLevelFrame.flagErrorsAsOld();
    }

    public void removeOldErrors()
    {
        topLevelFrame.removeOldErrors();
        errors = null;
        // TODO we should only really update the state when late errors arrive: 
        updateErrorOverviewBar(false);
    }

    //package-visible
    void updateErrorOverviewBar(boolean waitingForCompile)
    {
        if (topLevelFrame == null)
            return; // Still loading
        
        List<ErrorInfo> errors = getAllErrors()
                .filter(e -> e.getRelevantNode() != null)
                .map(e -> new ErrorInfo(e.getMessage(), e.getRelevantNode(), e.visibleProperty(), e.focusedProperty(),
                        () -> { if (getView() == View.BIRDSEYE)
                                    disableBirdseyeView(e.getRelevantNode(), () -> e.jumpTo(this));
                                else
                                    e.jumpTo(this); }))
                .collect(Collectors.toList());
        ErrorState state;
        if (waitingForCompile || topLevelFrame.getAllFrames().anyMatch(Frame::isFresh))
        {
            state = ErrorState.EDITING;
        }
        else
        {
            state = errors.stream().filter(e -> e.isVisible()).count()  == 0 ? ErrorState.NO_ERRORS : ErrorState.ERRORS;
        }
        
        errorOverviewBar.update(errors, state);
    }

    private Stream<CodeError> getAllErrors() {
        return Stream.concat(
            topLevelFrame.getEditableSlots().flatMap(EditableSlot::getCurrentErrors)
            , topLevelFrame.getAllFrames().flatMap(Frame::getCurrentErrors));
    }

    @Override
    public void modifiedFrame(Frame f)
    {
        if (f != null)
            f.trackBlank(); // Do this even if loading
        if (isLoading())
            return;
        editor.codeModified();
        registerStackHighlight(null);
        updateErrorOverviewBar(true);
        parent.scheduleCompilation();
        SuggestedFollowUpDisplay.modificationIn(this);
    }

    public void setWindowVisible(boolean vis, boolean bringToFront)
    {
        // Add ourselves, in case we were closed previously (no harm in calling twice)
        if (vis)
            parent.addFrameEditor(this, menuManager::getMenus, vis, bringToFront);
        parent.setWindowVisible(vis, this);
        if (bringToFront)
            parent.bringToFront(this);
    }

    public boolean isWindowVisible()
    {
        return parent.isWindowVisible();
    }

    @Override
    public void withCompletions(PosInSourceDoc pos, ExpressionSlot<?> completing, CodeElement codeEl, FXConsumer<List<AssistContentThreadSafe>> handler)
    {
        TopLevelCodeElement allCode = getSource();
        JavaFXUtil.bindFuture(
            // Over on the swing thread, get the completions and turn into FXAssistContent:
            Utility.swingFuture(() ->
                Utility.mapList(Arrays.asList(
                    editor.getCompletions(allCode, pos, completing, codeEl)
                ), AssistContentThreadSafe::copy)),
            // Then afterwards, back on the FX thread, pass to the handler:
            handler::accept);
    }

    @Override
    public void withSuperConstructors(FXConsumer<List<AssistContentThreadSafe>> handler)
    {
        TopLevelCodeElement codeEl = getSource();
        JavaFXUtil.bindFuture(
            Utility.swingFuture(() -> Utility.mapList(codeEl.getSuperConstructors(), c -> new AssistContentThreadSafe(new ConstructorCompletion(c, Collections.emptyMap(), editor.getJavadocResolver())))),
            handler::accept
        );
    }

    @Override
    public List<AssistContentThreadSafe> getThisConstructors()
    {
        TopLevelCodeElement codeEl = getSource();
        return codeEl.getThisConstructors();
    }
    
    @Override
    public void beginRecordingState(RecallableFocus f)
    {
        undoRedoManager.beginFrameState(getCurrentState(f));
    }

    @Override
    public void endRecordingState(RecallableFocus f)
    {
        undoRedoManager.endFrameState(getCurrentState(f));
    }
    
    private FrameState getCurrentState(RecallableFocus f)
    {
        regenerateCode();
        return new FrameState(topLevelFrame, getSource(), f);
    }

    public void undo()
    {
        undoRedoManager.startRestoring();
        updateClassContents(undoRedoManager.undo());
        undoRedoManager.stopRestoring();
    }

    public void redo()
    {
        undoRedoManager.startRestoring();
        updateClassContents(undoRedoManager.redo());
        undoRedoManager.stopRestoring();
    }

    private void updateClassContents(FrameState state)
    {
        if (state != null) {
            //Debug.time("updateClassContents", () -> {
            final ClassElement classElement = state.getClassElement(projectResolver);
            if (classElement == null)
            {
                return; // Error restoring state, will have been logged already
            }
            topLevelFrame.restoreCast((TopLevelCodeElement) classElement);
            topLevelFrame.regenerateCode();
            Node n = state.recallFocus(topLevelFrame);
            if (n != null)
            {
                ensureNodeVisible(n);
            }
            //});
        }
    }

    @Override
    public void scrollTo(Node n, double yOffsetFromTop, Duration duration /* instant if null */)
    {
        // prevent re-entrance from listeners looking to scroll to keep position:
        if (inScrollTo)
            return;
        inScrollTo = true;

        Bounds totalBound = scroll.getContent().localToScene(scroll.getContent().getBoundsInLocal());
        Bounds targetBound = n.localToScene(n.getBoundsInLocal());
        // Presumably 1.0 means that the bottom edge of the scroll pane is at the end,
        //   and the top edge is viewportHeight from the end, or (totalHeight - viewportHeight) from the beginning
        // While 0.0 means that the top edge of the scroll pane is at the beginning
        // So I figure that the coordinate of the top edge is given by:
        //   vvalue * (totalHeight - viewportHeight)
        //
        // Thus if we want to set the top edge to be at a given Y:
        //   Y = vvalue * (totalHeight - viewportHeight)
        //   vvalue = Y / (totalHeight - viewportHeight)
        
        double totalMinusView = totalBound.getHeight() - scroll.getHeight();
        double targetV = Math.max(0.0, Math.min(1.0, (targetBound.getMinY() + yOffsetFromTop - totalBound.getMinY()) / totalMinusView));
        
        //Debug.message("Scrolling to target: " + targetV + " on the basis of: " + targetBound + " and: " + totalBound + " and: " + scroll.getHeight());
        
        // targetV is a value from 0 to 1.  Technically, the vvalue for a scroll pane
        // can be between vmin and vmax.  Practically, vmin and vmax always seem to be
        // 0 and 1, but in case that changes:
        targetV = scroll.getVmin() + targetV * (scroll.getVmax() - scroll.getVmin());

        if (duration == null)
        {
            // Instant:
            scroll.setVvalue(targetV);
        }
        else
        {
            // Animate:
            animatingScroll  = true;
            Timeline t = new Timeline(new KeyFrame(duration, new KeyValue(scroll.vvalueProperty(), targetV)));
            t.setOnFinished(e -> { animatingScroll = false; });
            t.play();
        }
        inScrollTo = false;
    }

    @Override
    public FrameSelection getSelection()
    {
        return selection;
    }

    @Override
    public Point2D sceneToScreen(Point2D scenePoint)
    {
        Scene scene = scrollAndOverlays.getScene();
        return scenePoint.add(scene.getX(), scene.getY()).add(scene.getWindow().getX(), scene.getWindow().getY());
    }

    public void saved()
    {
        topLevelFrame.saved();
        topLevelFrame.getEditableSlots().forEach(EditableSlot::saved);
    }

    @Override
    public void withAccessibleMembers(PosInSourceDoc pos,
            Set<CompletionKind> kinds, boolean includeOverriden, FXConsumer<List<AssistContentThreadSafe>> handler)
    {
        TopLevelCodeElement allCode = getSource();
        JavaFXUtil.bindFuture(
            // Over on the swing thread, get the completions and turn into FXAssistContent:
            Utility.swingFuture(() -> {
                return
                    Utility.mapList(
                        editor.getAvailableMembers(allCode, pos, kinds, includeOverriden)
                        , AssistContentThreadSafe::copy);
            }),
            // Then afterwards, back on the FX thread, pass to the handler:
            handler::accept);
    }
    
    @Override
    @OnThread(Tag.FX)
    public void regenerateAndReparse(ExpressionSlot<?> completing)
    {
        regenerateCode();
        // Update positions in Java source file:
        if (topLevelFrame != null)
        {
            TopLevelCodeElement code = topLevelFrame.getCode();
            code.updateSourcePositions();
        }
    }

    private void updateDisplays()
    {
        // Go through methods and set override tags:
        CodeElement el;
        if (topLevelFrame != null && (el = topLevelFrame.getCode()) != null)
        {
            topLevelFrame.getAllFrames().forEach(f -> {
                if (f instanceof NormalMethodFrame)
                    SwingUtilities.invokeLater(() -> ((NormalMethodFrame)f).updateOverrideDisplay((ClassElement)el));
            });
        }
    }

    private void updateFontSize()
    {
        // We don't bind because topLevelFrame may change
        topLevelFrame.getNode().setStyle("-fx-font-size: " + getFontSizeCSS().get() + ";");
    }

    //package-visible
    void decreaseFontSize()
    {
        final IntegerProperty fontSize = PrefMgr.strideFontSizeProperty();
        int prev = fontSize.get();
        fontSize.set(Math.max(PrefMgr.MIN_STRIDE_FONT_SIZE, prev >= 36 ? prev - 4 : (prev >= 16 ? prev - 2 : prev - 1)));
    }
    
    //package-visible
    void increaseFontSize()
    {
        final IntegerProperty fontSize = PrefMgr.strideFontSizeProperty();
        int prev = fontSize.get();
        fontSize.set(Math.min(PrefMgr.MAX_STRIDE_FONT_SIZE, prev < 32 ? (prev < 14 ? prev + 1 : prev + 2) : prev + 4));
    }

    @Override
    public StringExpression getFontSizeCSS()
    {
        return PrefMgr.strideFontSizeProperty().asString().concat("pt");
    }

    private void calculateBirdseyeRectangle()
    {
        Node n = birdseyeManager.getNodeForRectangle();
        Point2D scene = n.localToScene(n.getBoundsInLocal().getMinX(), n.getBoundsInLocal().getMinY());
        // We can't ask birdseyeSelectionPane to transform because it is not visible yet
        // Ask the class frame instead:
        Point2D onPane = topLevelFrame.getNode().sceneToLocal(scene);
        
        birdseyeSelection.setX(onPane.getX());
        birdseyeSelection.setY(onPane.getY() + 1.5);
        birdseyeSelection.setWidth(n.getBoundsInLocal().getWidth());
        birdseyeSelection.setHeight(n.getBoundsInLocal().getHeight() - 1.5);
        
        birdseyeSelection.setFocusTraversable(true);
        birdseyeSelection.requestFocus();
    }
    
    @OnThread(Tag.Swing)
    private List<AssistContentThreadSafe> getPrimitiveTypes()
    {
        if (prims == null)
            prims = PrimitiveTypeCompletion.allPrimitiveTypes().stream().map(AssistContentThreadSafe::copy).collect(Collectors.toList());
        return prims;
    }

    @Override
    @OnThread(Tag.Any)
    public void withTypes(Class<?> superType, boolean includeSelf, Set<Kind> kinds, FXConsumer<List<AssistContentThreadSafe>> handler)
    {
        final List<AssistContentThreadSafe> r = new ArrayList<>();

        SwingUtilities.invokeLater(() -> {
            if (kinds.contains(Kind.PRIMITIVE))
                r.addAll(getPrimitiveTypes());
            r.addAll(editor.getLocalTypes(superType, includeSelf, kinds));
            Utility.getBackground().schedule(() -> {
                r.addAll(getImportedTypes(superType, includeSelf, kinds)
                    .stream()
                    .sorted(Comparator.comparing(AssistContentThreadSafe::getName))
                    .distinct()
                    .collect(Collectors.toList()));
                Platform.runLater(() -> handler.accept(r));
            }, 0, TimeUnit.MILLISECONDS);
        });
    }
    
    @Override
    @OnThread(Tag.Any)
    public void withTypes(FXConsumer<List<AssistContentThreadSafe>> handler)
    {
        withTypes(null, true, Kind.all(), handler);
    }
    
    public boolean insertAppendMethod(NormalMethodElement method)
    {
        // TODO maybe we have to insert it into the element not the frames.
        if (topLevelFrame != null) {
            for (NormalMethodFrame normalMethodFrame : topLevelFrame.getMethods()) {
                // Check if it already exists
                if (normalMethodFrame.getName().equals(method.getName())) {
                    insertMethodContentsIntoMethodFrame(method, normalMethodFrame);
                    return true;
                }
            }
            // method not found, create it
            insertMethodElementAtTheEnd(method);
        }
        else {
            Debug.message("insertAppendMethod @ FrameEditorTab: " + "class frame is null!" );
        }
        return false;
    }
    
    public boolean insertMethodCallInConstructor(String className, CallElement methodCall)
    {
        // TODO maybe we have to insert it into the element not the frames.
        if (topLevelFrame != null) {
            if (topLevelFrame.getConstructors().isEmpty()) {
                topLevelFrame.addDefaultConstructor();
            }
            for (ConstructorFrame constructorFrame : topLevelFrame.getConstructors()) {
                for (CodeFrame innerFrame : constructorFrame.getMembersFrames()) {
                    if (innerFrame instanceof CallFrame) {
                        CallFrame doFrame = (CallFrame)innerFrame;
                        if ( doFrame.getCode().toJavaSource().toTemporaryJavaCodeString().equals(methodCall.toJavaSource().toTemporaryJavaCodeString()) ) {
                            return true;
                        }
                    }
                }
                // Constructor found, but doesn't contain the method call
                insertElementIntoMethod(methodCall, constructorFrame);
            }
        }
        else {
            Debug.message("insertMethodCallInConstructor @ FrameEditorTab: " + "class frame is null!" );
        }
        return false;
    }

    public void insertElementIntoMethod(CodeElement element, MethodFrameWithBody<? extends MethodWithBodyElement> methodFrame)
    {
        Platform.runLater(() -> methodFrame.getLastInternalCursor().insertBlockAfter(element.createFrame(this)));
    }
    
    private void insertMethodContentsIntoMethodFrame(MethodWithBodyElement methodElement, MethodFrameWithBody<? extends MethodWithBodyElement> methodFrame)
    {
        Platform.runLater(() -> {
            for (CodeElement element : methodElement.getContents())
            {
                methodFrame.getLastInternalCursor().insertBlockAfter(element.createFrame(this));
            }
        });
    }
    
    private void insertMethodElementAtTheEnd(MethodWithBodyElement method)
    {
        Platform.runLater(() -> topLevelFrame.insertAtEnd(method.createFrame(this)));
    }

    @OnThread(Tag.Any)
    private Stream<AssistContentThreadSafe> getAllImportedTypes()
    {
        return Stream.concat(Stream.of(javaLangImports), importedTypes.stream()).map(FrameEditorTab::getFutureList).flatMap(List::stream);
    }
    
    @OnThread(Tag.Any)
    private List<AssistContentThreadSafe> getImportedTypes(Class<?> superType, boolean includeSelf, Set<Kind> kinds)
    {
        if (superType == null)
            return getAllImportedTypes()
                     .filter(ac -> kinds.contains(ac.getTypeKind()))
                     .collect(Collectors.toList());
        
        return getAllImportedTypes()
                    .filter(ac -> kinds.contains(ac.getTypeKind()))
                    .filter(ac -> ac.getSuperTypes().contains(superType.getName()) || (includeSelf && ac.getPackage() != null && (ac.getPackage() + "." + ac.getName()).equals(superType.getName())))
                    .collect(Collectors.toList());
    }

    @Override
    public Collection<AssistContentThreadSafe> getOtherPopularImports()
    {
        HashMap<String, AssistContentThreadSafe> popular = new HashMap();
        // Add popular:
        popularImports
            .stream()
            .map(FrameEditorTab::getFutureList)
            .flatMap(List::stream)
            .filter(imp -> imp.getPackage() != null)
            .forEach(imp -> popular.put(imp.getPackage() + "." + imp.getName(), imp));
        // Remove what we already import:
        getAllImportedTypes()
            .filter(imp -> imp.getPackage() != null)
            .forEach(imp -> popular.remove(imp.getPackage() + "." + imp.getName()));
        // And return the result:
        return popular.values();
    }
    
    @Override
    public void addImport(String importSrc)
    {
        topLevelFrame.addImport(importSrc);
    }
    
    @Override
    public List<FileCompletion> getAvailableFilenames()
    {
        List<FileCompletion> r = new ArrayList<>();
        Project project = parent.getProject();
        File imageDir = new File(project.getProjectDir(), "images");
        if (imageDir.exists())
        {
            File[] files = imageDir.listFiles(name -> name.getName().toLowerCase().endsWith(".png")
                    || name.getName().toLowerCase().endsWith(".jpg")
                    || name.getName().toLowerCase().endsWith(".jpeg"));
            
            r.addAll(Utility.mapList(Arrays.asList(files), ImageCompletion::new));
        }
        File soundDir = new File(project.getProjectDir(), "sounds");
        if (soundDir.exists())
        {
            File[] files = soundDir.listFiles(name -> name.getName().toLowerCase().endsWith(".wav"));
            
            r.addAll(Utility.mapList(Arrays.asList(files), SoundCompletion::new));
        }
        return r;
    }

    public void nextError()
    {
        // If we don't have an iterator, or we've reached the end, restart:
        if (errors == null || !errors.hasNext())
        {
            errors = getAllErrors().iterator();
        }
        
        // Still might be no errors, if the whole file is error free:
        while (errors.hasNext())
        {
            CodeError e = errors.next();
            if (e.visibleProperty().get())
            {
                e.jumpTo(this);
                return;
            }
        }
    }
    
    // You can pass null.
    @Override
    public void registerStackHighlight(Frame frame)
    {
        if (stackHighlight != null && stackHighlight != frame) {
            stackHighlight.removeStackHighlight();
        }
        stackHighlight = frame;
    }

    public ObservableBooleanValue initialisedProperty()
    {
        return initialised;
    }

    @Override
    public ObservableStringValue nameProperty()
    {
        // We don't just return the class frame's name property direct because
        // this method will get called by the constructor frame initialisation, before
        // the class frame has finished initialisation.  So nameProperty acts as a 
        // delay to the binding, which will be completed once the class frame has initialised.
        return nameProperty;
    }

    @Override
    public void setupFocusableSlotComponent(EditableSlot parent, Node node, boolean canCodeComplete, List<FrameCatalogue.Hint> hints)
    {
        node.focusedProperty().addListener((a, b, focused) -> {
            if (focused)
            {
                selection.clear();
            }
            this.parent.scheduleUpdateCatalogue(FrameEditorTab.this, null, focused && canCodeComplete ? CodeCompletionState.POSSIBLE : CodeCompletionState.NOT_POSSIBLE, false, getView(), hints);
        });

        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (dragTarget == null && e.isShortcutDown())
            {
                if (showingUnderlinesFor != parent)
                {
                    if (showingUnderlinesFor != null)
                        showingUnderlinesFor.removeAllUnderlines();
                    showingUnderlinesFor = parent;
                    parent.findLinks().stream().forEach(l -> searchLink(l, olid -> olid.ifPresent(lid -> lid.show())));
                }
            }
            else if (showingUnderlinesFor == parent)
            {
                showingUnderlinesFor = null;
                parent.removeAllUnderlines();
            }
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            if (showingUnderlinesFor == parent)
            {
                showingUnderlinesFor = null;
                parent.removeAllUnderlines();
            }
        });

        setupFocusable(new CursorOrSlot(parent), node);
    }
    
    private void setupFocusable(CursorOrSlot parent, Node node)
    {
        // When we detect focus gain, or whenever the size/position changes and node is focused,
        // make sure we remain visible:
        node.focusedProperty().addListener((a, b, focused) -> {
            if (focused)
            {
                focusedItem.set(parent);

                if (menu != null)
                {
                    menu.hide();
                }

                if (parent.isInsideCanvas(topLevelFrame.getImportCanvas()))
                {
                    topLevelFrame.ensureImportCanvasShowing();
                }
                
                if (!animatingScroll && !anyButtonsPressed)
                {
                    // focusedItem.set above, will have set this to false if it's a new owner.
                    if (!manualScrolledSinceLastFocusChange)
                        ensureNodeVisible(node);
                }
                
                if (topLevelFrame != null)
                {
                    // Have to take it into a list, as some slots vanish when they lose focus, which
                    // causes an exception in the underlying stream iterator:
                    List<EditableSlot> lostFocusSlots = topLevelFrame.getEditableSlots().filter(s -> !parent.matchesSlot(s)).collect(Collectors.toList());
                    lostFocusSlots.forEach(EditableSlot::lostFocus);
                    
                    // We need to find the focused frame.  That frame, and its direct slots,
                    // and all its ancestor frames and their direct slots, if fresh, should not show errors.
                    // Every other frame and slot become no longer fresh, and thus will show errors.
                    
                    Frame focusedFrame = parent.getParentFrame();
                    HashSet<Frame> frameAndAncestors = new HashSet<>();
                    for (Frame f = focusedFrame; f != null; f = f.getParentCanvas() == null ? null : f.getParentCanvas().getParent().getFrame())
                    {
                        frameAndAncestors.add(f);
                    }
                    
                    // Now go through all frames, and if they are not in frameAndAncestors set,
                    // mark them as non-fresh.
                    for (Frame f : Utility.iterableStream(topLevelFrame.getAllFrames()))
                    {
                        if (!frameAndAncestors.contains(f))
                        {
                            f.markNonFresh();
                        }
                    }
                }
            }
            else
            {
                if (parent.equals(focusedItem.get()))
                    focusedItem.set(null);
            }
        });
        FXRunnable checkPositionChange = new FXRunnable() {
            Bounds lastBounds = boundsInScrollContent(node);
            @Override
            public void run()
            {
                if (node.isFocused())
                {
                    Bounds boundsInScroll = boundsInScrollContent(node);
                    // Only scroll if our vertical position relative to scroll has changed:
                    if (Math.abs(boundsInScroll.getMinY() - lastBounds.getMinY()) >= 1.0
                            || Math.abs(boundsInScroll.getMaxY() - lastBounds.getMaxY()) >= 1.0)
                    {
                        //Debug.message("Position changed from " + lastBounds + " to " + boundsInScroll);
                        // Must change before calling ensureNodeVisible, as we may get re-triggered by
                        // consequent changes, and need to prevent infinite loop:
                        lastBounds = boundsInScroll;
                        if (!anyButtonsPressed)
                            ensureNodeVisible(node);
                    }
                    else
                    {
                        lastBounds = boundsInScroll;
                    }
                }
            }
        };

        // The bounds will be in the middle of changing, so we use runLater to make sure
        // we adjust after they have all settled down:
        node.localToSceneTransformProperty().addListener((ChangeListener) (a, b, c) -> {
            if (node.isFocused())
                FXRunnable.runLater(checkPositionChange);
        });
        node.boundsInLocalProperty().addListener((ChangeListener) (a, b, c) -> {
            if (node.isFocused())
                FXRunnable.runLater(checkPositionChange);
        });
    }

    private void ensureNodeVisible(Node node)
    {
        Bounds boundsInScroll = boundsInScroll(node);
        
        if (boundsInScroll == null)
            return;

        /*
         * There is a problem to do with focusing items just added to the scene.  When an item is just added,
         * its position is usually invalid nonsense (zero, height, positioned high up in the scene.
         * If we try to requestFocus on it, we'll end up here, trying to scroll towards this invalid
         * position, which we don't want to do; we want to wait until the position is valid
         * before deciding whether to scroll.
         *
         * A reasonable test for "valid position" appears to be whether the height is at least one pixel.
         * I've seen it zero and 0.4 with an invalid position, but after that it seems to become valid.
         *
         * What we do in the case the position is invalid is line up a one-time listener on the bounds
         * which will call this method again.  If they still aren't valid we'll go into the if-statement again,
         * add a listener, come back, etc, until they are valid.
         */
        if (boundsInScroll.getHeight() < 1.0)
        {
            JavaFXUtil.addSelfRemovingListener(node.boundsInLocalProperty(), x -> ensureNodeVisible(node));
            return;
        }

        final double MIN = 75; // Minimum pixels from edge of scroll view
        final Duration SCROLL_TIME = Duration.millis(150);

        if (boundsInScroll.getMaxY() < MIN) {
            scrollTo(node, -MIN, SCROLL_TIME);
        }
        else if (boundsInScroll.getMinY() > scroll.heightProperty().get() - MIN) {
            scrollTo(node, -(scroll.heightProperty().get() - MIN), SCROLL_TIME);
        }
    }

    // Returns null if the position does not seem to be valid yet
    // (we detect this by seeing if scene Y position is different to local Y position yet
    // -- it should be for everything except the very outermost node, which we
    // aren't concerned with here
    private Bounds boundsInScroll(Node node)
    {
        Bounds local = node.getBoundsInLocal();
        Bounds scene = node.localToScene(local);
        // It's only valid if local is different from scene in Y:
        if (local.getMinY() != scene.getMinY() && local.getMaxY() != scene.getMaxY())
            return scroll.sceneToLocal(scene);
        else
            return null;
    }

    private Bounds boundsInScrollContent(Node node)
    {
        return scrollContent.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    }
    
    @Override
    public KeyCode getKey(ShortcutKey keyPurpose)
    {
        switch (keyPurpose)
        {
            case YES_ANYWHERE: return KeyCode.F2;
            case NO_ANYWHERE: return KeyCode.F3;
        }
        return null;
    }

    @Override
    public boolean isLoading()
    {
        return loading;
    }
    
    public boolean isEditable()
    {
        return viewProperty.get() != View.JAVA_PREVIEW;
    }

    @Override
    public void setupSuggestionWindow(Stage window) {
        JavaFXUtil.addChangeListener(window.focusedProperty(), focused ->
                        parent.scheduleUpdateCatalogue(FrameEditorTab.this, null, focused ? CodeCompletionState.SHOWING : CodeCompletionState.NOT_POSSIBLE, false, View.NORMAL, Collections.emptyList())
        );
    }

    @Override
    public Pane getDragTargetCursorPane()
    {
        return parent.getDragCursorPane();
    }

    public void compiled()
    {
        if (topLevelFrame != null)
            topLevelFrame.getAllFrames().forEach(Frame::compiled);
        updateDisplays();
    }

    @Override
    public void ensureImportsVisible()
    {
        if (topLevelFrame != null)
            topLevelFrame.ensureImportCanvasShowing();
    }

    void ignoreEdits(FXRunnable during)
    {
        loading = true;
        during.run();
        loading = false;
    }
    
    public void updateCatalog(FrameCursor f)
    {
        parent.scheduleUpdateCatalogue(FrameEditorTab.this, f, CodeCompletionState.NOT_POSSIBLE, !selection.getSelected().isEmpty(), getView(), Collections.emptyList());
    }
    
    //package-visible
    void close()
    {
        parent.close(this);
    }

    //package-visible
    List<Menu> getMenus()
    {
        return menuManager.getMenus();
    }

    @Override
    public WindowOverlayPane getWindowOverlayPane()
    {
        return windowOverlayPane;
    }

    @Override
    public CodeOverlayPane getCodeOverlayPane()
    {
        return codeOverlayPane;
    }

    @Override
    public Observable getObservableScroll()
    {
        return observableScroll;
    }

    @Override
    public DoubleExpression getObservableViewportHeight()
    {
        return viewportHeight;
    }

    View getView()
    {
        return viewProperty.get();
    }

    @Override
    public ReadOnlyObjectProperty<View> viewProperty()
    {
        return viewProperty;
    }

    @Override
    public FrameCursor getFocusedCursor()
    {
        if (focusedItem.get() == null)
            return null;
        else
            return focusedItem.get().getCursor();
    }

    //package-visible
    public Observable focusedItemObservable()
    {
        return focusedItem;
    }

    @Override
    public void updateErrorOverviewBar()
    {
        // This method is called as a canvas begins to unfold/fold.  So we add a delay
        // before we recalculate the positions, to make sure the canvas has reached
        // its final size.  At worst, we recalculate twice; no big deal:
        JavaFXUtil.runAfter(Duration.millis(500), () -> updateErrorOverviewBar(false));
    }

    @Override
    public Paint getHighlightColor()
    {
        return contentRoot.cssHighlightColorProperty().get();
    }

    public FXTabbedEditor getFXTabbedEditor()
    {
        return parent;
    }

    private class ContentBorderPane extends BorderPane
    {
        private final CssMetaData<ContentBorderPane, Color> COLOR_META_DATA =
                JavaFXUtil.cssColor("-bj-highlight-color", ContentBorderPane::cssHighlightColorProperty);
        private final SimpleStyleableObjectProperty<Color> cssHighlightColorProperty = new SimpleStyleableObjectProperty<Color>(COLOR_META_DATA);
        private final List <CssMetaData <? extends Styleable, ? > > cssMetaDataList =
                JavaFXUtil.extendCss(BorderPane.getClassCssMetaData())
                        .add(COLOR_META_DATA)
                        .build();

        public final SimpleStyleableObjectProperty<Color> cssHighlightColorProperty() { return cssHighlightColorProperty; }

        @Override public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() { return cssMetaDataList; }

    }
}