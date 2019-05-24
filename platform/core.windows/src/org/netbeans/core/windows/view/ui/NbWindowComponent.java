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

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ComponentListener;
import javax.swing.RootPaneContainer;
import org.netbeans.core.windows.view.Controller;
import org.openide.windows.NbWindow;

/**
 * A tag used to identify NbWindow UI components 
 *      Implementation: 
 *          NbWindowFrame based on JFrame
 *          NbWindowDialog based on JDialog
 * 
 * @author Graeme Ingleby graeme@ingleby.net
 */
public interface NbWindowComponent extends RootPaneContainer  {
    /**
     * 
     * @return The NbWindow model object associated with this UI Component
     */
    public NbWindow getNbWindow();
    
    // The methods below are concepts from other core.windows classses and were
    // added to this interface to provide easy access without having to cast
    
    /**
     * @return The desktop component embedded in this UI Component
     */
    public Component getDesktopComponent();
    
    /**
     * Set the desktop component embedded in this UI Component
     * 
     * @param desktop 
     */
    public void setDesktop(Component desktop);
    
    /**
     * @return The View Controller for this UI Component
     */
    public Controller getController();
    
    /**
     * Change the visibility of this UI Component
     * 
     * @param isVisible 
     */
    public void setVisible(boolean isVisible);
    
    /**
     * @return If this UI Component is visible
     */
    public boolean isVisible();
    
    /**
     * Add a Component listener to this UI Component
     * @param listener 
     */
    public void addComponentListener(ComponentListener listener);
    
    /**
     * Remove a Component listener from this UI Component
     * @param listener 
     */
    public void removeComponentListener(ComponentListener listener);
    
    /**
     * Set the bounds of this UI Component
     * @param bounds 
     */
    public void setBounds(Rectangle bounds);
}
