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
package greenfoot.collision;

import greenfoot.TestObject;
import greenfoot.TestUtilDelegate;
import greenfoot.World;
import greenfoot.WorldCreator;
import greenfoot.util.GreenfootUtil;

import java.util.List;

import junit.framework.TestCase;

/**
 * Tests whether the collision checker works correctly when dealing with sub classes.
 * 
 * @author Poul Henriksen
 */
public class SubClassTests extends TestCase
{

    class SuperClass extends TestObject {

        public SuperClass(int w, int h)
        {
            super(w, h);
        }
    }

    class SubClass extends SuperClass {

        public SubClass(int w, int h)
        {
            super(w, h);
        }
        
    }
    
    class IndependentClass extends TestObject {

        public IndependentClass(int w, int h)
        {
            super(w, h);
        }
        
    }

    @Override
    protected void setUp()
        throws Exception
    {
        GreenfootUtil.initialise(new TestUtilDelegate());        
    }
    
    public void testHierarchy()
    {
        World world = WorldCreator.createWorld(10, 10, 10);

        TestObject superObj = new SuperClass(20, 20);
        world.addObject(superObj, 2, 2);
        TestObject subObj = new SubClass(10, 10);
        world.addObject(subObj, 2, 2);
        TestObject indepObj = new IndependentClass(10, 10);
        world.addObject(indepObj, 2, 2);
        
        List res = indepObj.getIntersectingObjectsP(SuperClass.class);
        assertEquals(2,res.size());        

        res = indepObj.getIntersectingObjectsP(SubClass.class);
        assertEquals(1,res.size());        

        res = indepObj.getIntersectingObjectsP(SuperClass.class);
        assertEquals(2,res.size());       

    }  
    
    public void testHierarchy2()
    {
        World world = WorldCreator.createWorld(10, 10, 10);

        TestObject superObj = new SuperClass(20, 20);
        world.addObject(superObj, 2, 2);
        TestObject subObj = new SubClass(10, 10);
        world.addObject(subObj, 2, 2);
        TestObject indepObj = new IndependentClass(10, 10);
        world.addObject(indepObj, 2, 2);
        
       
        
        List res = superObj.getIntersectingObjectsP(IndependentClass.class);
        assertEquals(1,res.size());        

        res = indepObj.getIntersectingObjectsP(SuperClass.class);
        assertEquals(2,res.size());        
    

    }

}
