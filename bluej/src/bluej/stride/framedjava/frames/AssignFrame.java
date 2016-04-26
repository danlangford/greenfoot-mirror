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


import java.util.List;

import bluej.stride.generic.FrameContentItem;
import javafx.application.Platform;
import bluej.stride.framedjava.ast.FilledExpressionSlotFragment;
import bluej.stride.framedjava.ast.HighlightedBreakpoint;
import bluej.stride.framedjava.canvases.JavaCanvas;
import bluej.stride.framedjava.elements.AssignElement;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.framedjava.slots.FilledExpressionSlot;
import bluej.stride.generic.FrameContentRow;
import bluej.stride.generic.FrameFactory;
import bluej.stride.generic.InteractionManager;
import bluej.stride.generic.SingleLineFrame;
import bluej.stride.operations.FrameOperation;
import bluej.stride.slots.Focus;
import bluej.stride.slots.HeaderItem;
import bluej.stride.slots.SlotLabel;

/**
 * A set statement for assignment, e.g. "set x = 1"
 * @author Fraser McKay
 */
public class AssignFrame extends SingleLineFrame
  implements CodeFrame<AssignElement>, DebuggableFrame
{
    
    private final ExpressionSlot<FilledExpressionSlotFragment> slotLHS;
    private final ExpressionSlot<FilledExpressionSlotFragment> slotRHS;
    private AssignElement element;
    
    /**
     * Default constructor.
     * @param editor 
     */
    private AssignFrame(InteractionManager editor)
    {
        super(editor, null, "set-");
        //Parameters
        slotRHS = new FilledExpressionSlot(editor, this, this, getHeaderRow(), "", FilledExpressionSlot.SRC_HINTS);
        slotRHS.setSimplePromptText("new-value");
        slotLHS = new FilledExpressionSlot(editor, this, this, getHeaderRow(), "assign-lhs-");
        slotLHS.setSimplePromptText("variable");
        setHeaderRow(slotLHS, new SlotLabel("="), slotRHS, previewSemi);
        
        slotLHS.bindClosingChar(slotRHS, '=');
        slotLHS.bindClosingChar(slotRHS, ' ');
    }
    
    // For replacement of a method call frame:
    AssignFrame(InteractionManager editor, String lhs, String rhs)
    {
        this(editor);
        slotLHS.setText(lhs);
        slotRHS.setText(rhs);
        Platform.runLater(() -> slotRHS.requestFocus(Focus.LEFT));
    }
    
    public AssignFrame(InteractionManager editor, FilledExpressionSlotFragment lhs, FilledExpressionSlotFragment rhs, boolean enabled)
    {
        this(editor);
        slotLHS.setText(lhs);
        slotRHS.setText(rhs);
        frameEnabledProperty.set(enabled);
    }

    @Override
    public void regenerateCode()
    {
        element = new AssignElement(this, slotLHS.getSlotElement(), slotRHS.getSlotElement(), 
                frameEnabledProperty.get());
    }
    
    @Override
    public AssignElement getCode()
    {
        return element;
    }
    
    public static FrameFactory<AssignFrame> getFactory()
    {
        return new FrameFactory<AssignFrame>() {
            @Override
            public AssignFrame createBlock(InteractionManager editor)
            {
                return new AssignFrame(editor);
            }
                        
            @Override 
            public Class<AssignFrame> getBlockClass()
            { 
                return AssignFrame.class;
            }
        };
    }
    
    @Override
    public List<FrameOperation> getCutCopyPasteOperations(InteractionManager editor)
    {
        return GreenfootFrameUtil.cutCopyPasteOperations(editor);
    }

    @Override
    public HighlightedBreakpoint showDebugBefore(DebugInfo debug)
    {
        return ((JavaCanvas)getParentCanvas()).showDebugBefore(this, debug);        
    }

    public ExpressionSlot getLHS()
    {
        return slotLHS;
    }
    
    public ExpressionSlot getRHS()
    {
        return slotRHS;
    }

    @Override
    public boolean backspaceAtStart(FrameContentItem row, HeaderItem src)
    {
        if (src == slotRHS)
        {
            collapseIntoMethodCall();
            return true;
        }
        else
            return super.backspaceAtStart(row, src);
    }

    @Override
    public boolean deleteAtEnd(FrameContentItem row, HeaderItem src)
    {
        if (src == slotLHS)
        {
            collapseIntoMethodCall();
            return true;
        }
        return false;
    }

    private void collapseIntoMethodCall()
    {
        getParentCanvas().replaceBlock(this, new CallFrame(getEditor(), slotLHS.getText(), slotRHS.getText()));        
    }
}
