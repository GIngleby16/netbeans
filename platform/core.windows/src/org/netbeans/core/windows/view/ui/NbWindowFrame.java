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
package org.netbeans.core.windows.view.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import org.netbeans.core.windows.NbWindowImpl;
import org.netbeans.core.windows.NbWindowTracker;
import org.netbeans.core.windows.WindowManagerImpl;
import org.netbeans.core.windows.view.Controller;
import org.netbeans.core.windows.view.dnd.ZOrderManager;
import org.openide.util.ImageUtilities;
import org.openide.windows.WindowManager;

/**
 * NBWindowFrame
 *
 * @author Graeme Ingleby graeme@ingleby.net
 */
public class NbWindowFrame extends JFrame implements NbWindowComponent {

    private static final String ICON_16 = "org/netbeans/core/startup/frame.gif"; // NOI18N
    private static final String ICON_32 = "org/netbeans/core/startup/frame32.gif"; // NOI18N
    private static final String ICON_48 = "org/netbeans/core/startup/frame48.gif"; // NOI18N

    static void initFrameIcons(Window f) {
        List<Image> currentIcons = f.getIconImages();
        if (!currentIcons.isEmpty()) {
            return; //do not override icons if they have been already provided elsewhere (JDev)
        }
        f.setIconImages(Arrays.asList(
                ImageUtilities.loadImage(ICON_16, true),
                ImageUtilities.loadImage(ICON_32, true),
                ImageUtilities.loadImage(ICON_48, true)));
    }

    private Controller controller;

    private NbWindowImpl window;

    private Component desktop;
    
    public NbWindowFrame(NbWindowImpl window, Rectangle bounds, Controller controller) {
        //super(WindowManager.getDefault().getMainWindow(), window.getName());
        super(window.getName());
        setName(window.getName());
        setBounds(bounds);
        this.window = window;
        this.controller = controller;
        
        // Automatically register with window tracker
        NbWindowTracker.getInstance().addWindow(window, this);

//        getRootPane().putClientProperty("isAuxFrame", Boolean.TRUE);
        // make minimize button visible in view tab
        // initialize frame
        initFrameIcons(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        setTitle(WindowManager.getDefault().getMainWindow().getTitle());

        // To be able to activate on mouse click.
        enableEvents(java.awt.AWTEvent.MOUSE_EVENT_MASK);

        ZOrderManager.getInstance().attachWindow(this);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
               WindowManagerImpl wm = WindowManagerImpl.getInstance();
               wm.setActiveMode(wm.getActiveMode(getNbWindow()));
            }
            
            @Override
            public void windowClosing(WindowEvent e) {
                NbWindowFrame.this.controller.userClosingNbWindow(NbWindowFrame.this.window);
//                AuxWindowFrame.this.window.setVisible(false);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                ZOrderManager.getInstance().detachWindow(NbWindowFrame.this);
            }

        });        
    }

    @Override
    public void setName(String name) {
        super.setName(name); //To change body of generated methods, choose Tools | Templates.
    }

    public Component getDesktopComponent() {
        return desktop;
    }

    public void setDesktop(Component desktop) {
        if (this.desktop != desktop) {
            if (this.desktop != null) {
                getContentPane().remove(this.desktop);
            }
            this.desktop = desktop;
            getContentPane().add(desktop, BorderLayout.CENTER);
        }
    }

    public NbWindowImpl getNbWindow() {
        return window;
    }

    public Controller getController() {
        return controller;
    }
}
