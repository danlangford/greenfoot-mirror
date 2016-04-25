/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
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
package bluej.pkgmgr.actions;

import javax.swing.ButtonModel;

import bluej.pkgmgr.PkgMgrFrame;

/**
 * Action to toggle display of debugger. This action provides a ButtonModel
 * so that it can be tied to a check-box.
 * 
 * @author Davin McCall
 * @version $Id: ShowDebuggerAction.java 6215 2009-03-30 13:28:25Z polle $
 */
final public class ShowDebuggerAction extends PkgMgrAction
{
    public ShowDebuggerAction()
    {
        super("menu.view.showExecControls");
    }
            
    public ButtonModel getToggleModel(PkgMgrFrame pmf)
    {
        return new bluej.debugmgr.ExecControlButtonModel(pmf);
    }
}
