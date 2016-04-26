/*
 This file is part of the BlueJ program.
 Copyright (C) 2015 Michael Kölling and John Rosenberg

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
package bluej.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import bluej.debugger.gentype.ConstructorReflective;
import bluej.debugger.gentype.GenTypeParameter;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.MethodReflective;
import bluej.pkgmgr.JavadocResolver;
import bluej.stride.generic.InteractionManager;
import bluej.utility.JavaUtils;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 07/09/15.
 */
public class ConstructorCompletion extends AssistContent
{
    private final ConstructorReflective con;
    private final String className;
    private final JavadocResolver javadocResolver;
    private final Map<String,GenTypeParameter> typeArgs;

    public ConstructorCompletion(ConstructorReflective c, Map<String,GenTypeParameter> typeArgs,
                                 JavadocResolver javadocResolver)
    {
        this.con = c;
        className = c.getDeclaringType().getSimpleName();
        this.javadocResolver = javadocResolver;
        this.typeArgs = typeArgs;
    }

    @Override
    public Access getAccessPermission()
    {
        return fromModifiers(con.getModifiers());
    }

    @Override
    public String getDeclaringClass()
    {
        return con.getDeclaringType().getSimpleName();
    }

    @Override
    public String getJavadoc()
    {
        String jd = con.getJavaDoc();
        if (jd == null && javadocResolver != null) {
            javadocResolver.getJavadoc(con);
            jd = con.getJavaDoc();
        }
        return jd;
    }

    @Override
    public boolean getJavadocAsync(final JavadocCallback callback, Executor executor)
    {
        String jd = con.getJavaDoc();
        if (jd == null && javadocResolver != null) {
            return javadocResolver.getJavadocAsync(con, new JavadocResolver.AsyncCallback() {
                @Override
                public void gotJavadoc(ConstructorOrMethodReflective method)
                {
                    if (method.getJavaDoc() == null) {
                        method.setJavaDoc(""); // prevent repeated attempts to retrieve unavailable doc
                    }
                    callback.gotJavadoc(ConstructorCompletion.this);
                }
            }, executor);
        }
        else {
            return true;
        }
    }

    @Override
    public CompletionKind getKind()
    {
        return CompletionKind.CONSTRUCTOR;
    }

    @Override
    public @OnThread(Tag.Any) String getName()
    {
        return className;
    }

    @Override
    public List<ParamInfo> getParams()
    {
        // We must get Javadoc before asking for parameter names, as it is this method call that sets the parameter names:
        getJavadoc();
        ArrayList<ParamInfo> r = new ArrayList<>();
        List<JavaType> paramTypes = con.getParamTypes();
        List<String> paramNames = con.getParamNames();
        for (int i = 0; i < paramTypes.size(); i++)
        {
            JavaType t = convertToSolid(paramTypes.get(i));
            String paramName = paramNames == null ? null : paramNames.get(i);
            r.add(new ParamInfo(t.toString(), paramName, MethodCompletion.buildDummyName(t, paramName), javadocForParam(paramName)));
        }
        return r;
    }

    @Override
    public String getType()
    {
        return getDeclaringClass();
    }

    private JavaType convertToSolid(JavaType type)
    {
        if (! type.isPrimitive()) {
            if (typeArgs != null) {
                type = type.mapTparsToTypes(typeArgs).getTparCapture();
            }
            else {
                // null indicates a raw type.
                type = type.getErasedType();
            }
        }
        return type;
    }

    @OnThread(Tag.Swing)
    private String javadocForParam(String paramName)
    {
        JavaUtils.Javadoc javadoc = JavaUtils.parseJavadoc(getJavadoc());

        if (javadoc == null)
            return null;

        String target = "param " + paramName;
        for (String block : javadoc.getBlocks())
        {
            if (block.startsWith(target) && Character.isWhitespace(block.charAt(target.length())))
            {
                return block.substring(target.length() + 1).trim();
            }
        }
        return null;
    }
}
