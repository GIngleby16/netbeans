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

import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.NbWindowSelector;
import org.openide.windows.TopComponent;

/**
 * The DefaultNbWindowSelector creates a Dialog based window for
 * both views & editors.  
 * 
 * The argument netbeans.winsys.enhanced.nbwindow-both=true can be used
 * to change the default behavior so that NbWindowSelector creates a 
 * Frame for an Editor (TopComponent with EditorCookie) or a Dialog for 
 * Views (a TopComponent without EditorCookie)
 * 
 * @author Graeme Ingleby graeme@ingleby.net
 */
@ServiceProvider(service=NbWindowSelector.class)
public class DefaultNbWindowSelector implements NbWindowSelector {
    @Override
    public Boolean isDialogRequested(TopComponent tc) {
        if (Boolean.getBoolean("netbeans.winsys.enhanced.nbwindow-both")) {
            // create dialogs for non-editors
            boolean isEditor = WindowManagerImpl.getInstance().isEditorTopComponent(tc);
            return !isEditor;
        }
        // default behavior
        return true;
    }    
}
