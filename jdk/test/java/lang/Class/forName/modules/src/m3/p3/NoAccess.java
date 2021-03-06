/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package p3;

import java.io.FilePermission;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Set;

public class NoAccess {
    private static final Module M3 = NoAccess.class.getModule();
    private static final Path MODS_DIR1 = Paths.get("mods1");
    private static final Path MODS_DIR2 = Paths.get("mods2");
    public static void main(String[] args) throws Exception {
        // disable security manager until Class.forName is called.
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            System.setSecurityManager(null);
        }

        ModuleFinder finder = ModuleFinder.of(Paths.get("mods1"), Paths.get("mods2"));

        Layer bootLayer = Layer.boot();
        Configuration parent = bootLayer.configuration();

        Configuration cf = parent.resolveRequiresAndUses(finder,
                                                         ModuleFinder.of(),
                                                         Set.of("m1", "m2"));

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Layer layer = bootLayer.defineModulesWithManyLoaders(cf, scl);

        if (sm != null) {
            System.setSecurityManager(sm);
        }

        Module m1 = bootLayer.findModule("m1").get();
        Module m2 = bootLayer.findModule("m2").get();
        Module m3 = bootLayer.findModule("m3").get();

        findClass(m1, "p1.internal.B");
        findClass(m2, "p2.C");
        findClass(m3, "p3.internal.Foo");

        // permissions granted
        findClass(m1, "p1.A");
        findClass(m1, "p1.internal.B");
        findClass(m2, "p2.C");
        findClass(m3, "p3.internal.Foo");


        // m1 and m2 from a different layer
        m1 = layer.findModule("m1").get();
        m2 = layer.findModule("m2").get();
        m3 = layer.findModule("m3").get();

        findClass(m1, "p1.A");
        findClass(m3, "p3.internal.Foo");

        // no permission
        Path path = MODS_DIR1.resolve("p1").resolve("internal").resolve("B.class");
        findClass(m1, "p1.internal.B", new FilePermission(path.toString(), "read"));
        path = MODS_DIR2.resolve("p2").resolve("C.class");
        findClass(m2, "p2.C", new FilePermission(path.toString(), "read"));
    }

    static Class<?> findClass(Module module, String cn) {
        return findClass(module, cn, null);
    }

    static Class<?> findClass(Module module, String cn, Permission perm) {
        try {
            Class<?> c = Class.forName(module, cn);
            if (c == null) {
                throw new RuntimeException(cn + " not found in " + module);
            }
            if (c.getModule() != module) {
                throw new RuntimeException(c.getModule() + " != " + module);
            }
            return c;
        } catch (AccessControlException e) {
            if (e.getPermission().equals(perm))
                return null;
            throw e;
        }
    }
}
