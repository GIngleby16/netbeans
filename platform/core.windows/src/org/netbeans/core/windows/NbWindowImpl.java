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

import java.awt.Rectangle;
import org.openide.windows.NbWindow;

/**
 *
 * @author Graeme Ingleby graeme@ingleby.net
 */
public class NbWindowImpl implements NbWindow  {
    private boolean requestDialog = false;
    
    public NbWindowImpl(String name, Rectangle bounds, boolean requestDialog) {
        this.requestDialog = requestDialog;
        getCentral().createNbWindowModel(this, name, bounds);
    }
    
    @Override
    public String getName() {
        return getCentral().getNbWindowName(this);
    }

    @Override
    public Rectangle getBounds() {
        return getCentral().getNbWindowBounds(this);
    }

    @Override
    public void setBounds(Rectangle bounds) {
        getCentral().setNbWindowBounds(this, bounds);
    }

    @Override
    public void setVisible(boolean visible) {
        getCentral().setNbWindowVisible(this, visible);
    }

    @Override
    public boolean isVisible() {
         return getCentral().isNbWindowVisible(this);
    }
    
    public boolean isDialogRequested() {
        return requestDialog;
    }    
    
    // just a shortcut
    private static Central getCentral() {
        return WindowManagerImpl.getInstance().getCentral();
    }

    @Override
    public String toString() {
        return super.toString() + "[" + getName() + "]";
    }
}
