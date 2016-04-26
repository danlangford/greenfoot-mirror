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

import bluej.stride.framedjava.ast.CallExpressionSlotFragment;
import bluej.stride.framedjava.ast.ExpressionSlotFragment;
import bluej.stride.framedjava.ast.FilledExpressionSlotFragment;
import bluej.stride.framedjava.ast.HighlightedBreakpoint;
import bluej.stride.framedjava.canvases.JavaCanvas;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.slots.CallExpressionSlot;
import bluej.stride.framedjava.slots.ExpressionSlot;
import bluej.stride.framedjava.slots.ExpressionSlot.SplitInfo;
import bluej.stride.framedjava.slots.FilledExpressionSlot;
import bluej.stride.generic.FrameFactory;
import bluej.stride.generic.InteractionManager;
import bluej.stride.generic.SingleLineFrame;
import bluej.stride.operations.FrameOperation;

/**
 * A method call, e.g. "do x(param y)"
 * @author Fraser McKay
 */
public class CallFrame extends SingleLineFrame
  implements CodeFrame<CallElement>, DebuggableFrame
{
    private final ExpressionSlot<CallExpressionSlotFragment> content;
    
    private CallElement element;
    
    /**
     * Default constructor.
     * @param editor 
     */
    private CallFrame(InteractionManager editor)
    {
        super(editor, null, "do-");
        content = new CallExpressionSlot(editor, this, this, getHeaderRow(), "do-method-name-", CallExpressionSlot.CALL_HINTS);
        content.setText("()");
        content.setMethodCallPromptText("method-name");
        
        setHeaderRow(content, previewSemi);

        content.onTextPropertyChange(s -> checkForTopLevelEquals());
    }
    
    // For replacement of AssignFrame:
    CallFrame(InteractionManager editor, String beforeCursor, String afterCursor)
    {
        this(editor);
        this.content.setSplitText(beforeCursor, afterCursor);
    }
    
    public CallFrame(InteractionManager editor, ExpressionSlotFragment e, boolean enabled)
    {
        this(editor);
        this.content.setText(e);
        frameEnabledProperty.set(enabled);
    }

    @Override
    public void regenerateCode()
    {
        element = new CallElement(this, content.getSlotElement(), frameEnabledProperty.get());
    }
    
    @Override
    public CallElement getCode()
    {
        return element;
    }  
    
    public static FrameFactory<CallFrame> getFactory()
    {
        return new FrameFactory<CallFrame>() {
            @Override
            public CallFrame createBlock(InteractionManager editor)
            {
                return new CallFrame(editor);
            }
                        
            @Override
            public Class<CallFrame> getBlockClass()
            {
                return CallFrame.class;
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

    private void checkForTopLevelEquals()
    {
        // If the user has put a single top-level '=' then we should turn into an assignment frame
        SplitInfo info = content.trySplitOnEquals();
        if (info != null && getParentCanvas() != null)
        {
            getParentCanvas().replaceBlock(this, new AssignFrame(getEditor(), info.lhs, info.rhs));
        }
    }
}
