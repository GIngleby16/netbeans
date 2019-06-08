/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the Lic!ense.  You may obtain a copy of the License at
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

import java.awt.Image;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.IconSelector;
import org.openide.windows.TopComponent;

/**
 * Default implementation for IconSelector.
 *      Only editors will display their default icon (the icon associated with the TopComponent)
 * 
 * @author Graeme Ingleby graeme@ingleby.net
 */
@ServiceProvider(service=IconSelector.class)
public class DefaultIconSelector implements IconSelector {

    @Override
    public Icon getIconForTopComponent(TopComponent tc, Icon wantedIcon) {        
        if(wantedIcon == null)
            return null;
        
        if (Boolean.getBoolean("netbeans.winsys.enhanced.tab-icons")) {
            return wantedIcon;
        }
        
        boolean isEditor = TopComponentTracker.getDefault().isEditorTopComponent(tc);
        if(!isEditor)
            return null;
        
        return wantedIcon;
    }    
}
