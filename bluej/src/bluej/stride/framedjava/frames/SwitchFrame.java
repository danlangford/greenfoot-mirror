/*
 This file is part of the BlueJ program. 
 Copyright (C) 2014,2015 Michael Kölling and John Rosenberg 
 
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
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bluej.stride.framedjava.frames;


import java.util.ArrayList;
import java.util.List;

import bluej.stride.framedjava.ast.ExpressionSlotFragment;
import bluej.stride.framedjava.ast.FilledExpressionSlotFragment;
import bluej.stride.framedjava.ast.HighlightedBreakpoint;
import bluej.stride.framedjava.canvases.JavaCanvas;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.SwitchElement;
import bluej.stride.framedjava.frames.BreakFrame.BreakEncloser;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.framedjava.slots.FilledExpressionSlot;
import bluej.stride.generic.ExtensionDescription;
import bluej.stride.generic.Frame;
import bluej.stride.generic.FrameCanvas;
import bluej.stride.generic.FrameContentRow;
import bluej.stride.generic.FrameCursor;
import bluej.stride.generic.FrameFactory;
import bluej.stride.generic.InteractionManager;
import bluej.stride.generic.MultiCanvasFrame;
import bluej.stride.operations.FrameOperation;
import bluej.stride.operations.PullUpContentsOperation;
import bluej.stride.slots.EditableSlot;
import bluej.stride.slots.SlotLabel;
import bluej.utility.Debug;
import bluej.utility.javafx.JavaFXUtil;

/**
 * Container-block representing a switch-case statement.
 */
public class SwitchFrame extends MultiCanvasFrame
  implements CodeFrame<SwitchElement>, DebuggableParentFrame
{
    private static final String SWITCH_STYLE_PREFIX = "switch-";
    private final ExpressionSlot<FilledExpressionSlotFragment> expression;
    private final JavaCanvas casesCanvas;
    private JavaCanvas defaultCanvas;
    private SwitchElement element;
    private final InteractionManager editor; // Saved for creating default canvas.

    /**
     * Default constructor.
     */
    private SwitchFrame(InteractionManager editor)
    {
        super(editor, "switch", SWITCH_STYLE_PREFIX);
        this.editor = editor;

        casesCanvas = new JavaCanvas(editor, this, SWITCH_STYLE_PREFIX, false);
        addCanvas(null, casesCanvas);


        //Parameters
        expression = new FilledExpressionSlot(editor, this, this, getHeaderRow(), SWITCH_STYLE_PREFIX){
            @Override
            public boolean backspaceAtStart()
            {
                if (isAlmostBlank()) {
                    new PullUpContentsOperation(editor).activate(getParentFrame());
                    return true;
                }
                return super.backspaceAtStart();
            }
        };
        expression.setSimplePromptText("expression");
        setHeaderRow(new SlotLabel("("), expression, new SlotLabel(")"));
        expression.onTextPropertyChange(updateSidebarCurried("switch "));
    }
    
    public SwitchFrame(InteractionManager editor, ExpressionSlotFragment expression, boolean enabled)
    {
        this(editor);
        this.expression.setText(expression);
        frameEnabledProperty.set(enabled);
    }

    public boolean addDefault()
    {
        if (defaultCanvas != null) {
            return false; //Already have one
        }

        defaultCanvas = new JavaCanvas(editor, this, "default-case-", false);
        SlotLabel defaultLabel = new SlotLabel("default");
        JavaFXUtil.addStyleClass(defaultLabel, "divider-default-case");
        addCanvas(new FrameContentRow(this, defaultLabel), defaultCanvas);
        defaultCanvas.getFirstCursor().requestFocus();
        return true;
    }

    private void removeDefault()
    {
        if (defaultCanvas != null) {
            removeCanvas(defaultCanvas);
            defaultCanvas = null;
        }
    }
    
    public static FrameFactory<SwitchFrame> getFactory()
    {
        return new FrameFactory<SwitchFrame>() {
            @Override
            public SwitchFrame createBlock(InteractionManager editor)
            {
                SwitchFrame switchFrame = new SwitchFrame(editor);
                switchFrame.getFirstInternalCursor().insertBlockAfter(CaseFrame.getFactory().createBlock(editor));
                return switchFrame;
            }
            
            @Override
            public Class<SwitchFrame> getBlockClass()
            { 
                return SwitchFrame.class;
            }
        };
    }
    
    @Override
    public List<FrameOperation> getCutCopyPasteOperations(InteractionManager editor)
    {
        return GreenfootFrameUtil.cutCopyPasteOperations(editor);
    }

    @Override
    public void pullUpContents()
    {
        // casesCanvas
        // Make copy because we're about to modify the contents:
        List<Frame> casesFrames = new ArrayList<>(casesCanvas.getBlockContents());
        casesFrames.forEach(c -> casesCanvas.removeBlock(c));
        final List<Frame> contents = new ArrayList<>();
        casesFrames.forEach(c -> {
                    // Add a BlankFrame before each case
                    contents.add(new BlankFrame(editor));
                    contents.addAll(((CaseFrame) c).getValidPulledStatements());
                }
        );
        getCursorBefore().insertFramesAfter(contents);

        if (defaultCanvas != null) {
            // Add a BlankFrame in between
            getCursorBefore().insertBlockAfter(new BlankFrame(getEditor()));

            // defaultCanvas
            // Make copy because we're about to modify the contents:
            List<Frame> defaultContents = new ArrayList<>(defaultCanvas.getBlockContents());
            defaultContents.forEach(c -> defaultCanvas.removeBlock(c));
            getCursorBefore().insertFramesAfter(defaultContents);
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this);
    }

    public void pullUpInnerCaseContents(CaseFrame frame)
    {
        int index = casesCanvas.getBlockContents().indexOf(frame);
        if (index < 0) {
            throw new IllegalStateException("CaseFrame should be in the casesCanvas");
        }
        else if (index == 0) {
            // TODO waiting a design decision
            Debug.message("pullUpInnerCaseContents @ SwitchFrame: Unimplemented case, waiting a design decision");
        }
        else {
            List<Frame> contents = frame.getValidPulledStatements();
            CaseFrame previous = (CaseFrame) casesCanvas.getBlockContents().get(index - 1);
            // Add a BlankFrame in between
            previous.getLastInternalCursor().insertBlockAfter(new BlankFrame(getEditor()));
            previous.getLastInternalCursor().insertFramesAfter(contents);
            casesCanvas.removeBlock(frame);
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this);
    }

    private void pullUpDefaultContents()
    {
        if (defaultCanvas == null) {
            throw new IllegalStateException("Default couldn't be null if this method is invoked.");
        }
        int casesCount = casesCanvas.blockCount();
        if (casesCount == 0) {
            // TODO waiting a design decision
            Debug.message("pullUpDefaultContents @ SwitchFrame: Unimplemented case, waiting a design decision");
        }
        else {
            List<Frame> defaultContents = new ArrayList<>(defaultCanvas.getBlockContents());
            defaultContents.forEach(frame -> frame.setParentCanvas(null));
            defaultContents.forEach(c -> defaultCanvas.removeBlock(c));

            CaseFrame previous = (CaseFrame) casesCanvas.getBlockContents().get(casesCount - 1);
            // Add a BlankFrame in between
            previous.getLastInternalCursor().insertBlockAfter(new BlankFrame(getEditor()));
            previous.getLastInternalCursor().insertFramesAfter(defaultContents);

            removeDefault();
        }
        //notify the editor that a change has been occurred. That will trigger a file save
        editor.modifiedFrame(this);
    }

    @Override
    public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
    {
        return ((JavaCanvas)getParentCanvas()).showDebugBefore(this, debug);
    }

    @Override
    public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
    {
        return getCasesCanvas().showDebugBefore(null, debug);
    }

    @Override
    public void regenerateCode()
    {
        List<CodeElement> casesContents = new ArrayList<>();
        casesCanvas.getBlocksSubtype(CodeFrame.class).forEach(f -> {
            f.regenerateCode();
            casesContents.add(f.getCode());
        });

        List<CodeElement> defaultContents = null;
        if (defaultCanvas != null) {
            defaultContents = new ArrayList<>();
            for (CodeFrame<?> f : defaultCanvas.getBlocksSubtype(CodeFrame.class)) {
                f.regenerateCode();
                defaultContents.add(f.getCode());
            }
        }
        element = new SwitchElement(this, expression.getSlotElement(), casesContents, defaultContents, frameEnabledProperty.get());
    }

    @Override
    public SwitchElement getCode()
    {
        return element;
    }

    @Override
    public BreakEncloser asBreakEncloser()
    {
        return BreakEncloser.SWITCH;
    }

    public JavaCanvas getCasesCanvas()
    {
        return casesCanvas;
    }

    public JavaCanvas getDefaultCanvas()
    {
        return defaultCanvas;
    }

    @Override
    public List<ExtensionDescription> getAvailableExtensions()
    {
        List<ExtensionDescription> result = new ArrayList<>(super.getAvailableExtensions());
        if (defaultCanvas == null) {
            result.add(new ExtensionDescription(GreenfootFrameDictionary.DEFAULT_EXTENSION_CHAR, "Add default", SwitchFrame.this::addDefault, true));
        }
        return result;
    }

    public DebuggableParentFrame getCasesDebug()
    {
        return new DebuggableParentFrame() {

            @Override
            public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
            {
                return ((JavaCanvas) getParentCanvas()).showDebugBefore(SwitchFrame.this, debug);
            }

            @Override
            public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
            {
                return getCasesCanvas().showDebugBefore(null, debug);
            }
        };
    }

    public DebuggableParentFrame getDefaultDebug()
    {
        return new DebuggableParentFrame() {

            @Override
            public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
            {
                // TODO check it
                return ((JavaCanvas)getParentCanvas()).showDebugBefore(SwitchFrame.this, debug);
            }

            @Override
            public HighlightedBreakpoint showDebugAtEnd(DebugInfo debug)
            {
                // TODO check it
                return getDefaultCanvas().showDebugBefore(null, debug);
            }
        };
    }

    @Override
    public boolean focusWhenJustAdded()
    {
        expression.requestFocus();
        return true;
    }

    // TODO AA fix it
//    @Override
//    public void focusUp(HeaderItem src, boolean toEnd)
//    {
//        casesCanvas.getLastCursor().requestFocus();
//    }

    // TODO AA fix it
//    @Override
//    public void focusDown(HeaderItem src)
//    {
//        casesCanvas.getFirstCursor().requestFocus();
//    }

    /*
    // TODO AA fix it
    @Override
    public Stream<EditableSlot> getHeaderItems()
    {
        Stream<EditableSlot> s = Stream.concat(casesCanvas.getHeaderItems(), Stream.of(exceptions));
        s = Stream.concat(s, catchCanvas.getHeaderItems());
        return (defaultCanvas == null) ? s : Stream.concat(s, defaultCanvas.getHeaderItems());
    }
    */

    @Override
    public boolean acceptsType(FrameCanvas canvas, Class<? extends Frame> blockClass)
    {
        if (canvas == casesCanvas)
        {
            return blockClass.equals(CaseFrame.class);
        }
        else if (canvas == defaultCanvas)
        {
            return getEditor().getDictionary().isValidStatment(blockClass);
        }
        return false;
    }

    @Override
    public List<ExtensionDescription> getAvailableInnerExtensions(FrameCanvas canvas, FrameCursor cursor)
    {
        List<ExtensionDescription> result = new ArrayList<>();

        if (defaultCanvas == null) {
            result.add(new ExtensionDescription(GreenfootFrameDictionary.DEFAULT_EXTENSION_CHAR,
                    "Add default", SwitchFrame.this::addDefault, true));
        }

        if (canvas == casesCanvas)
        {
            result.add(new ExtensionDescription('\b', "Remove switch/cases, keep contents", () ->
                    new PullUpContentsOperation(editor).activate(getFrame()), false, false));
        }
        if (defaultCanvas != null && canvas == defaultCanvas)
        {
            result.add(new ExtensionDescription('\b', "Remove default, keep contents",
                    SwitchFrame.this::pullUpDefaultContents, false, false));
        }

        return result;
    }

    public boolean isAlmostBlank()
    {
        return getEditableSlotsDirect().allMatch(EditableSlot::isAlmostBlank) &&
                defaultCanvas == null &&
                (casesCanvas.blockCount() == 0 || (casesCanvas.blockCount() == 1 && casesCanvas.getBlockContents().get(0).isAlmostBlank()));
    }
}
