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
/*
 * TabbedIconPaneListener - a listener to tab selection changes in the
 * TabbedIconPane.
 *
 * @author Michael Kolling
 * @version $Id: TabbedIconPaneListener.java 6170 2009-02-20 13:29:34Z polle $
 */

package greenfoot.gui.export;

public interface TabbedIconPaneListener 
{
    /** 
     * Called when the selection of the tabs changes.
     * 'name' is the NAME of the selected tab.
     */
    void tabSelected(String name);
}
