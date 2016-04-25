/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009  Poul Henriksen and Michael K�lling 
 
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
package greenfoot.actions;

import greenfoot.gui.GreenfootFrame;
import greenfoot.gui.export.ExportDialog;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import bluej.Config;

/**
 * Action to export a project to a standalone program.
 * 
 * @author Poul Henriksen, Michael Kolling
 * @version $Id: ExportProjectAction.java 6170 2009-02-20 13:29:34Z polle $
 */
public class ExportProjectAction extends AbstractAction 
{
    
    private ExportDialog exportDialog;
    private GreenfootFrame gfFrame;
    
    public ExportProjectAction(GreenfootFrame gfFrame)
    {
        super(Config.getString("export.project"));
        this.gfFrame = gfFrame;
        setEnabled(false);
    }

    public void actionPerformed(ActionEvent event)
    {       
        if(exportDialog == null) {
            exportDialog = new ExportDialog(gfFrame);
        }
        exportDialog.display();
    }
}
