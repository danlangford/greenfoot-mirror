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
package bluej.pkgmgr;

import java.util.Comparator;

import bluej.pkgmgr.dependency.*;
import bluej.pkgmgr.target.*;

/**
 * An ordering on targets to make layout nicer (reduce line intersections, etc.)
 *
 * @author Michael Cahill
 * @version $Id: LayoutComparer.java 6683 2009-09-16 10:17:14Z davmac $
 */
public class LayoutComparer implements Comparator<Dependency>
{
    DependentTarget centre;
    boolean in;

    public LayoutComparer(DependentTarget centre, boolean in)
    {
        this.centre = centre;
        this.in = in;
    }

    /**
     * Order <a> and <b> depending on their relative positions
     * and their positions relative to the centre
     *
     * Note: this is designed to reduce intersections when drawing lines.
     */
    public int compare(Dependency a, Dependency b)
    {
        DependentTarget ta = in ? a.getFrom() : a.getTo();
        DependentTarget tb = in ? b.getFrom() : b.getTo();

        int ax = ta.getX() + ta.getWidth()/2;
        int ay = ta.getY() + ta.getHeight()/2;
        int bx = tb.getX() + tb.getWidth()/2;
        int by = tb.getY() + tb.getHeight()/2;

        if((ax == bx) && (ay == by))
            return 0;

        int cx = centre.getX() + centre.getWidth()/2;
        int cy = centre.getY() + centre.getHeight()/2;

        boolean a_above = (ay < cy);
        boolean a_left = (ax < cx);
        int a_quad = (a_above ? 0 : 2) + (a_left ? 0 : 1);
        boolean b_above = (by < cy);
        boolean b_left = (bx < cx);
        int b_quad = (b_above ? 0 : 2) + (b_left ? 0 : 1);

        if(a_quad != b_quad) // different quadrants
            return (a_quad > b_quad) ? 1 : -1;
        // otherwise, we're in the same quadrant
        int result = in ? ((ax < bx) ? -1 : 1) : ((ay < by) ? -1 : 1);
        return (a_above == a_left) ? -result : result;
    }
}
