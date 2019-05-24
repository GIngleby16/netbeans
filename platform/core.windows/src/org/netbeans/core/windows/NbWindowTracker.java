/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.core.windows;

import java.awt.Component;
import java.awt.Window;
import java.util.WeakHashMap;
import org.netbeans.core.windows.view.ui.MainWindow;
import org.openide.windows.NbWindow;
import org.netbeans.core.windows.view.ui.NbWindowComponent;

/**
 *
 * @author Graeme Ingleby graeme@ingleby.net        
 */
public class NbWindowTracker {
    private static WeakHashMap<NbWindowImpl, Window> map = new WeakHashMap<NbWindowImpl, Window>();
    private static WeakHashMap<Window, NbWindowImpl> reverseMap = new WeakHashMap<Window, NbWindowImpl>();
    
    private static NbWindowTracker instance = new NbWindowTracker();
    
    private NbWindowTracker() {
        map.put(null, MainWindow.getInstance().getFrame());
        reverseMap.put(MainWindow.getInstance().getFrame(), null);
    }
    
//    public static NbWindowImpl findWindow(String name) {
//        if(name == null || name.equals(""))
//            return null;
//        
//        for(NbWindowImpl window: map.keySet()) {
//            if(window != null && window.getName().equals(name))
//                return window;
//        }
//        return null;
//    }
    
    public static NbWindowTracker getInstance() {
        return instance;
    }
    
    public void addWindow(NbWindowImpl nbWindow, Window window) {
        synchronized(map) {
            map.put(nbWindow, window);
            reverseMap.put(window, nbWindow);
        }
    }
    
    public void removeWindow(NbWindow nbWindow) {
        synchronized(map) {
            Window window = map.get(nbWindow);
            map.remove(nbWindow);
            reverseMap.remove(window);
        }        
    }
    
    public Window toWindow(NbWindow nbWindow) {
        return map.get(nbWindow);
    }
    
    public NbWindowImpl toNbWindow(Window window) {
        return reverseMap.get(window);
    }
    
    public Component getDesktopComponent(NbWindow nbWindow) {
        Window win = map.get(nbWindow);
        if(win instanceof NbWindowComponent) {
            return ((NbWindowComponent)win).getDesktopComponent();
        } else {
            return MainWindow.getInstance().getDesktop();
        }
    }    
}
