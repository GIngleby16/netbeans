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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;

import org.netbeans.core.windows.model.DockingStatus;
import org.netbeans.core.windows.model.Model;
import org.netbeans.core.windows.model.ModelElement;
import org.netbeans.core.windows.model.ModelFactory;
import org.netbeans.core.windows.options.WinSysPrefs;
import org.netbeans.core.windows.view.ControllerHandler;
import org.netbeans.core.windows.view.View;
import org.netbeans.core.windows.view.dnd.TopComponentDraggable;
import org.netbeans.core.windows.view.dnd.ZOrderManager;
import org.openide.util.Lookup;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindowSelector;


/**
 * This class is a central unit of window system. It controls communication
 * flows to model, to view, from controller and from API calls.
 *
 * @author Peter Zavadsky
 */
final class Central implements ControllerHandler {
    
    /** Model of window system. */
    private final Model model = ModelFactory.createWindowSystemModel();
    
    /** Helper class for managing requests to view. */
    private final ViewRequestor viewRequestor = new ViewRequestor(this);
    
    private ModeImpl modeBeingMaximized = null;
    
    /** Constructor. */
    public Central() {
    }

    
    public void topComponentRequestAttention (ModeImpl mode, TopComponent tc) {
        String modeName = getModeName(mode);
        viewRequestor.scheduleRequest (
            new ViewRequest(modeName, View.TOPCOMPONENT_REQUEST_ATTENTION, tc, tc));
    }

    public void topComponentCancelRequestAttention (ModeImpl mode, TopComponent tc) {
        String modeName = getModeName(mode);
        viewRequestor.scheduleRequest (
            new ViewRequest(modeName, View.TOPCOMPONENT_CANCEL_REQUEST_ATTENTION, tc, tc));
    }

    /**
     * Turns tab highlight on/off
     * @param mode
     * @param tc
     * @param highlight 
     * @since 2.54
     */
    public void topComponentAttentionHighlight (ModeImpl mode, TopComponent tc, boolean highlight) {
        String modeName = getModeName(mode);
        viewRequestor.scheduleRequest (
            new ViewRequest(modeName, highlight ? View.TOPCOMPONENT_ATTENTION_HIGHLIGHT_ON
                                                : View.TOPCOMPONENT_ATTENTION_HIGHLIGHT_OFF, tc, tc));
    }
    
    /////////////////////
    // Mutators >>
    /** Sets visible or invisible window system and requests view accordingly. */
    public void setVisible(boolean visible) {
        if(isVisible() == visible) {
            return;
        }
        
        model.setVisible(visible);
        
        viewRequestor.scheduleRequest(
            new ViewRequest(null, View.CHANGE_VISIBILITY_CHANGED, null, Boolean.valueOf(visible)));
    }
    
    /** Sets main window bounds (joined[tiled] state) into model and requests view (if needed). */
    public void setMainWindowBoundsJoined(Rectangle mainWindowBoundsJoined) {
        if(mainWindowBoundsJoined == null) {
            return;
        }
        
        Rectangle old = getMainWindowBoundsJoined();
        if(old.equals(mainWindowBoundsJoined)) {
            return;
        }
        
        model.setMainWindowBoundsJoined(mainWindowBoundsJoined);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MAIN_WINDOW_BOUNDS_JOINED_CHANGED,
                    old, mainWindowBoundsJoined));
        }
    }
    
    /** Sets main window bounds (separated state) into model and requests view (if needed). */
    public void setMainWindowBoundsSeparated(Rectangle mainWindowBoundsSeparated) {
        if(mainWindowBoundsSeparated == null) {
            return;
        }
        
        Rectangle old = getMainWindowBoundsSeparated();
        if(old.equals(mainWindowBoundsSeparated)) {
            return;
        }
        
        model.setMainWindowBoundsSeparated(mainWindowBoundsSeparated);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MAIN_WINDOW_BOUNDS_SEPARATED_CHANGED,
                    old, mainWindowBoundsSeparated));
        }
    }
    
    public void setMainWindowFrameStateJoined(int frameState) {
        int old = getMainWindowFrameStateJoined();
        if(old == frameState) {
            return;
        }
        
        model.setMainWindowFrameStateJoined(frameState);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_MAIN_WINDOW_FRAME_STATE_JOINED_CHANGED,
                Integer.valueOf(old), Integer.valueOf(frameState)));
        }
    }
    
    public void setMainWindowFrameStateSeparated(int frameState) {
        int old = getMainWindowFrameStateSeparated();
        if(old == frameState) {
            return;
        }
        
        model.setMainWindowFrameStateSeparated(frameState);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_MAIN_WINDOW_FRAME_STATE_SEPARATED_CHANGED,
                Integer.valueOf(old), Integer.valueOf(frameState)));
        }
    }
    
    /** Sets active mode into model and requests view (if needed). */
    public void setActiveMode(final ModeImpl activeMode) {
        //#87843: Do not activate mode when it does not contain any opened TC
        if (activeMode != null) {
            List<TopComponent> l = activeMode.getOpenedTopComponents();
            if (l.isEmpty()) {
                return;
            }
        }
        
        // We should be able to find the window based on the mode?
        NbWindowImpl window = model.getWindowForMode(activeMode);
        
        final ModeImpl old = getActiveMode();
        if(activeMode == old) {
            
            // kind of workaround to the scenario when a window slides out automatically
            // and user clicks in the currently active mode, not allow to exit in such case and fire changes to
            // force the slided-out window to disappear.
            ModeImpl impl = model.getSlidingMode(window, Constants.BOTTOM);
            boolean bottom = (impl == null || impl.getSelectedTopComponent() == null);
            impl = model.getSlidingMode(window, Constants.LEFT);
            boolean left = (impl == null || impl.getSelectedTopComponent() == null);
            impl = model.getSlidingMode(window, Constants.RIGHT);
            boolean right = (impl == null || impl.getSelectedTopComponent() == null);
            impl = model.getSlidingMode(window, Constants.TOP);
            boolean top = (impl == null || impl.getSelectedTopComponent() == null);
            if (bottom && left && right && top) {
                return;
            }
        }
        
        model.setActiveMode(window, activeMode);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_ACTIVE_MODE_CHANGED,
                    old, activeMode));
        }

        
        
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManagerImpl.PROP_ACTIVE_MODE, old, activeMode);
        
        // Notify registry.
        // active mode can be null, Active mode info is stored in winsys config (system layer) and modes in
        // project layer, that can cause out of synch state when switching projects.
        // all subsequent calls should handle the null value correctly.
        if (activeMode != null) {
            WindowManagerImpl.notifyRegistryTopComponentActivated(
            activeMode.getSelectedTopComponent());
        } else {
            WindowManagerImpl.notifyRegistryTopComponentActivated(null);
        }
    }

    /** Sets editor area bounds into model and requests view (if needed). */
    public void setEditorAreaBounds(Rectangle editorAreaBounds) {
        if(editorAreaBounds == null) {
            return;
        }
        
        Rectangle old = getEditorAreaBounds();
        if(old.equals(editorAreaBounds)) {
            return;
        }
        
        model.setEditorAreaBounds(editorAreaBounds);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_EDITOR_AREA_BOUNDS_CHANGED,
                            old, editorAreaBounds));
        }
    }

    /** Sets editor area constraints into model and requests view (if needed). */
    public void setEditorAreaConstraints(NbWindowImpl window, SplitConstraint[] editorAreaConstraints) {
        SplitConstraint[] old = getEditorAreaConstraints(window);
        if(Arrays.equals(old, editorAreaConstraints)) {
            return;
        }
        
        model.setEditorAreaConstraints(window, editorAreaConstraints);

        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_EDITOR_AREA_CONSTRAINTS_CHANGED,
                                old, editorAreaConstraints));
        }
    }

    /** Sets editor area state into model and requests view (if needed). */
    public void setEditorAreaState(int editorAreaState) {
        int old = getEditorAreaState();
        if(editorAreaState == old) {
            return;
        }
        
        int requiredState = editorAreaState == Constants.EDITOR_AREA_JOINED
                                                ? Constants.MODE_STATE_JOINED
                                                : Constants.MODE_STATE_SEPARATED;
                                                
        for(Iterator it = getModes().iterator(); it.hasNext(); ) {
            ModeImpl mode = (ModeImpl)it.next();
            if(mode.getKind() == Constants.MODE_KIND_VIEW
            && mode.getState() != requiredState) {
                model.setModeState(mode, requiredState);
                // Adjust bounds if necessary.
                if(editorAreaState == Constants.EDITOR_AREA_SEPARATED) {
                    Rectangle bounds = model.getModeBounds(mode);
                    if(bounds.isEmpty()) {
                        model.setModeBounds(mode, model.getModeBoundsSeparatedHelp(mode));
                    }
                }
            }
            // when switching to SDI, undock sliding windows
            // #51992 -start
            if (mode.getKind() == Constants.MODE_KIND_SLIDING && editorAreaState == Constants.EDITOR_AREA_SEPARATED) {
                TopComponent[] tcs = mode.getTopComponents();
                for (int i = 0; i < tcs.length;i++) {
                    String tcID = WindowManagerImpl.getInstance().findTopComponentID(tcs[i]);
                    ModeImpl targetMode = model.getModeTopComponentPreviousMode(mode, tcID);
                    if ((targetMode == null) || !model.getModes().contains(targetMode)) {
                        SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints(mode, tcID);
                        constraints = constraints == null ? new SplitConstraint[0] : constraints;
                        // create mode to dock topcomponent back into
                        targetMode = WindowManagerImpl.getInstance().createModeImpl(
                            ModeImpl.getUnusedModeName(), Constants.MODE_KIND_VIEW, false);
                        model.setModeState(targetMode, requiredState);
                        model.addMode(null, targetMode, constraints);  // gwi-editor: setEditorState needs NbWindow
                    }
                    moveTopComponentIntoMode(targetMode, tcs[i] );                    
                }
            }
            // #51992 - end
        }
                                                
        if(editorAreaState == Constants.EDITOR_AREA_SEPARATED) {
            Rectangle editorAreaBounds = model.getEditorAreaBounds();
            // Adjust bounds if necessary.
            if(editorAreaBounds.isEmpty()) {
                model.setEditorAreaBounds(model.getEditorAreaBoundsHelp());
            }
            
            // Adjust bounds if necessary.
            Rectangle mainWindowBoundsSeparated = model.getMainWindowBoundsSeparated();
            if(mainWindowBoundsSeparated.isEmpty()) {
                model.setMainWindowBoundsSeparated(model.getMainWindowBoundsSeparatedHelp());
            }
        }
        
        model.setEditorAreaState(editorAreaState);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_EDITOR_AREA_STATE_CHANGED,
                        Integer.valueOf(old), Integer.valueOf(editorAreaState)));
        }
        
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManagerImpl.PROP_EDITOR_AREA_STATE, Integer.valueOf(old), Integer.valueOf(editorAreaState));
    }

    public void setEditorAreaFrameState(int frameState) {
        int old = getEditorAreaFrameState();
        if(old == frameState) {
            return;
        }
        model.setEditorAreaFrameState(frameState);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_EDITOR_AREA_FRAME_STATE_CHANGED,
                Integer.valueOf(old), Integer.valueOf(frameState)));
        }
    }
    
    /** Sets new maximized mode into model and requests view update (if needed). */
    void switchMaximizedMode(NbWindowImpl window, ModeImpl newMaximizedMode) {
//        NbWindowImpl window = getWindowForMode(newMaximizedMode);
        
        ModeImpl old = getCurrentMaximizedMode(window); 
        if(newMaximizedMode == old) {
            return;
        }
        
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        if( null == newMaximizedMode ) {
            //cancel current maximized mode
            if( isViewMaximized(window) ) { 
                //some non-editor TopComponent is currently maximized
                
                //restore docking status of other components
                if( isEditorMaximized(window) ) { 
                    restoreViews(window, model.getMaximizedDockingStatus(window) ); 
                } else {
                    restoreViews(window, model.getDefaultDockingStatus(window) ); 
                }
                
                //return the maximized TopComponent to its original mode
                ModeImpl currentMaximizedMode = getViewMaximizedMode(window); 
                if( currentMaximizedMode.getTopComponents().length == 1 ) {
                    TopComponent maximizedTC = currentMaximizedMode.getTopComponents()[0];
                    String tcID = wm.findTopComponentID( maximizedTC );
                    //find the mode where the TopComponent was before its maximization
                    ModeImpl prevMode = getModeTopComponentPreviousMode( tcID, currentMaximizedMode );
                    int prevIndex = model.getModeTopComponentPreviousIndex( currentMaximizedMode, tcID );
                    if( null == prevMode ) {
                        //TODO log a warning here because we somehow lost the previous mode
                        if ((prevMode == null) || !model.getModes().contains(prevMode)) {
                            // mode to return to isn't valid anymore, try constraints
                            SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints(currentMaximizedMode, tcID);
                            if (constraints != null) {
                                // create mode with the same constraints to dock topcomponent back into
                                prevMode = WindowManagerImpl.getInstance().createModeImpl(
                                        ModeImpl.getUnusedModeName(), Constants.MODE_KIND_VIEW, false);
                                model.addMode(window, prevMode, constraints);
                            }
                        }

                        if (prevMode == null) {
                            // fallback, previous saved mode not found somehow, use default modes
                            prevMode = WindowManagerImpl.getInstance().getDefaultViewMode(window); 
                        }
                    }
                    prevMode.addOpenedTopComponent( maximizedTC, prevIndex );
                    prevMode.setSelectedTopComponent( maximizedTC );
                    setActiveMode(prevMode);
                    model.removeMode( currentMaximizedMode, false );
                } else {
                    Logger.getLogger( Central.class.getName() ).log( Level.WARNING, 
                            "A 'view' mode is maximized but it has wrong number of TopComponents, Mode=[" 
                            + currentMaximizedMode.getName() + "], TC count=" + currentMaximizedMode.getTopComponents().length );
                }
                //cancel the maximized mode
                setViewMaximizedMode( null );
                
            } else if( isEditorMaximized(window) ) {
                //an editor TopComponent is maximized
                model.getMaximizedDockingStatus(window).mark();
                ModeImpl prevActiveMode = getActiveMode();
                //restore the docking status of other components
                restoreViews(window, model.getDefaultDockingStatus(window) );
                
                //cancel the maximized mode
                setEditorMaximizedMode(window, null );
                
                setActiveMode( prevActiveMode );
                
            }
        } else {
            assert !isViewMaximized(window);
            
            //set new maximized mode
            if( newMaximizedMode.getKind() == Constants.MODE_KIND_EDITOR ) {
                //the new maximized mode is an editor TopComponent
                
                //remember the current docking status of opened TopComponents
                model.getDefaultDockingStatus(window).mark();
                //slide-out/dock some TopComponents according to their previous state in maximized mode
                restoreViews(window, model.getMaximizedDockingStatus(window) );

                setEditorMaximizedMode(window, newMaximizedMode );

            } else if( newMaximizedMode.getKind() == Constants.MODE_KIND_VIEW ) {
                //the new maximized mode is non-editor TopComponent

                //get the TopComponent that will be maximized
                TopComponent tcToMaximize = newMaximizedMode.getSelectedTopComponent();
                if( null == tcToMaximize ) {
                    if( newMaximizedMode.getOpenedTopComponents().isEmpty() ) {
                        return;
                    }
                    tcToMaximize = newMaximizedMode.getOpenedTopComponents().get(0);
                }
                
                //remember the docking status of opened components
                if( isEditorMaximized(window) ) {
                    model.getMaximizedDockingStatus(window).mark();
                } else {
                    model.getDefaultDockingStatus(window).mark();
                }
                
                modeBeingMaximized = newMaximizedMode;

                String tcID = wm.findTopComponentID( tcToMaximize );
                int prevIndex = newMaximizedMode.getOpenedTopComponents().indexOf( tcToMaximize );

                //create a new mode for the maximization
                ModeImpl mode = WindowManagerImpl.getInstance().createModeImpl(ModeImpl.getUnusedModeName(), Constants.MODE_KIND_VIEW, true);
                model.addMode(window, mode, new SplitConstraint[0]);
                //the mode has just one TopComponent
                mode.addOpenedTopComponent( tcToMaximize );
                mode.setSelectedTopComponent( tcToMaximize );
                //remember where to put the TopComponent back when un-maximizing
                setModeTopComponentPreviousMode( tcID, mode, newMaximizedMode, prevIndex );

                setViewMaximizedMode( mode );
                
                //slide-out all other TopComponents (the editor area won't be visible)
                slideAllViews(window);
                
                setActiveMode( mode );

                modeBeingMaximized = null;
            } else {
                throw new IllegalArgumentException( "Cannot maximize a sliding view" );
            }
        }

        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MAXIMIZED_MODE_CHANGED,
                    old, getCurrentMaximizedMode(window)));
        }
        
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManagerImpl.PROP_MAXIMIZED_MODE, old, getCurrentMaximizedMode(window));
    }

    /** Sets editor mode that is currenlty maximized (used when the window system loads) */
    void setEditorMaximizedMode(NbWindowImpl window, ModeImpl editorMaximizedMode) {
        model.setEditorMaximizedMode(window,  editorMaximizedMode );
    }
    
    /** Sets view mode that is currenlty maximized (used when the window system loads) */
    void setViewMaximizedMode(ModeImpl viewMaximizedMode) {
        NbWindowImpl window = getWindowForMode(viewMaximizedMode);
        model.setViewMaximizedMode(window, viewMaximizedMode );
    }
    
    /** Sets constraints for mode into model and requests view (if needed). */
    public void setModeConstraints(NbWindowImpl window, ModeImpl mode, SplitConstraint[] modeConstraints) {
        SplitConstraint[] old = getModeConstraints(window, mode);
        if(Arrays.equals(modeConstraints, old)) {
            return;
        }
        
        model.setModeConstraints(window, mode, modeConstraints);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MODE_CONSTRAINTS_CHANGED,
                        old, modeConstraints));
        }
    }

    /** Adds mode into model and requests view (if needed). */
    public void addMode(NbWindowImpl window, ModeImpl mode, SplitConstraint[] modeConstraints) {
        // PENDING which one to use?
//        if(getModes().contains(mode)) {
//            return;
//        }
        SplitConstraint[] old = getModeConstraints(window, mode);
        if(modeConstraints == old) {
            return;
        }
        
        model.addMode(window, mode, modeConstraints);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MODE_ADDED, null, mode));
        }
        
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }
    
    final void setModeName(ModeImpl mode, String text) {
        model.setModeName(mode, text);
    }

    /** Removes mode from model and requests view (if needed). */
    public void removeMode(ModeImpl mode) {
        if(!getModes().contains(mode)) {
            return;
        }
        
        System.out.println("Model:removeMode " + mode.getName());
        
//        debugLog("removeMode()=" + mode.getDisplayName());
        model.removeMode(mode, false);
                
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MODE_REMOVED, null, mode));
        }

        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    /** Sets toolbar configuration name and requests view (if needed). */
    public void setToolbarConfigName(String toolbarConfigName) {
        String old = getToolbarConfigName();
        if(old.equals(toolbarConfigName)) {
            return;
        }
            
        model.setToolbarConfigName(toolbarConfigName);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_TOOLBAR_CONFIGURATION_CHANGED,
                            old, toolbarConfigName));
        }
    }

    /** Updates UI. */
    public void updateUI() {
        // Pure request, no model change.
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_UI_UPDATE, null, null));
        }
    }
    
    ////////////////////////////
    // Mode specific >>
    private void closeMode(ModeImpl mode) {
        if(mode == null) {
            return;
        }
        
        TopComponent[] tcs = getModeOpenedTopComponents(mode).toArray(new TopComponent[0]);
        
        for (int i = 0; i < tcs.length; i++) {
            if (PersistenceHandler.isTopComponentPersistentWhenClosed(tcs[i])) {
                model.addModeClosedTopComponent(mode, tcs[i]);
            } else {
                if (Boolean.TRUE.equals(tcs[i].getClientProperty(Constants.KEEP_NON_PERSISTENT_TC_IN_MODEL_WHEN_CLOSED))) {
                    model.addModeClosedTopComponent(mode, tcs[i]);
                } else {
                    model.removeModeTopComponent(mode, tcs[i], null);
                }
            }
        }
        
        ModeImpl oldActive = getActiveMode();
        ModeImpl newActive;
        if(mode == oldActive) {
            newActive = setSomeModeActive(getWindowForMode(mode));
        } else {
            newActive = oldActive;
        }
//        debugLog("closeMode()");
        
        // Remove mode from model if is not permanennt and emptied.
        boolean modeRemoved = false;
        if(!mode.isPermanent() && model.getModeTopComponents(mode).isEmpty()) {
            // only if no sliding modes' tc points to this mode, then it's ok to remove it.
            if (doCheckSlidingModes(mode)) {
//                debugLog("do close mode=" + mode.getDisplayName());
                model.removeMode(mode, false);
                modeRemoved = true;
            }
        }
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(mode, View.CHANGE_MODE_CLOSED, null, null));
        }

        // Notify closed.
        for(int i = 0; i < tcs.length; i++) {
            // Notify TopComponent was closed.
            WindowManagerImpl.getInstance().notifyTopComponentClosed(tcs[i]);
        }
        
        if(oldActive != newActive) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManagerImpl.PROP_ACTIVE_MODE, oldActive, newActive);
        }
    
        if(modeRemoved) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManager.PROP_MODES, null, null);
        }
        
        // Notify new active.
        if(newActive != null) {
            // Notify registry.
            WindowManagerImpl.notifyRegistryTopComponentActivated(
                newActive.getSelectedTopComponent());
        } else {
            WindowManagerImpl.notifyRegistryTopComponentActivated(null);
        }
    }
    
    // XXX TODO Model should handle this on its own.
    private ModeImpl setSomeModeActive(NbWindowImpl window) {
        for(Iterator it = getModes().iterator(); it.hasNext(); ) {
            ModeImpl mode = (ModeImpl)it.next();
            if(!mode.getOpenedTopComponents().isEmpty() && Constants.MODE_KIND_SLIDING != mode.getKind()) {
                model.setActiveMode(window, mode);
                return mode;
            }
        }
        model.setActiveMode(window, null);
        return model.getActiveMode();
    }

    
    /** Sets bounds into model and requests view (if needed). */
    public void setModeBounds(ModeImpl mode, Rectangle bounds) {
        if(bounds == null) {
            return;
        }
        
        Rectangle old = getModeBounds(mode);
        if(old.equals(bounds)) {
            return;
        }
        
        model.setModeBounds(mode, bounds);
        
        if(isVisible() && getEditorAreaState() == Constants.EDITOR_AREA_SEPARATED) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_MODE_BOUNDS_CHANGED, old, bounds));
        }
        
        mode.doFirePropertyChange(ModeImpl.PROP_BOUNDS, old, bounds);
    }
    
    /** Sets frame state. */
    public void setModeFrameState(ModeImpl mode, int frameState) {
        int old = getModeFrameState(mode);
        if(frameState == old) {
            return;
        }
        
        model.setModeFrameState(mode, frameState);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_MODE_FRAME_STATE_CHANGED,
                Integer.valueOf(old), Integer.valueOf(frameState)));
        }
    }
    
    /** Sets seleted TopComponent into model and requests view (if needed). */
    public void setModeSelectedTopComponent(ModeImpl mode, TopComponent selected) {
        // don't apply check for sliding kind when clearing selection to null
        if (mode.getKind() != Constants.MODE_KIND_SLIDING || selected != null) {
            if(!getModeOpenedTopComponents(mode).contains(selected)) {
                return;
            }
        }
        
        TopComponent old = getModeSelectedTopComponent(mode);
        if(selected == old) {
            return;
        }
        
        model.setModeSelectedTopComponent(mode, selected);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(mode, View.CHANGE_MODE_SELECTED_TOPCOMPONENT_CHANGED,
                old, selected));
        }
        
        // Notify registry.
        if(mode == getActiveMode()) {
            WindowManagerImpl.notifyRegistryTopComponentActivated(selected);
        }
    }
    
    /**
     * Remember which TopComponent was previously the selected one, used when switching to/from maximized mode.
     * 
     * @param mode 
     * @param tcId ID of TopComponent that was previously selected.
     */
    public void setModePreviousSelectedTopComponentID(ModeImpl mode, String tcId) {
        model.setModePreviousSelectedTopComponentID( mode, tcId );
    }

    /** Adds opened TopComponent into model and requests view (if needed). */
    public void addModeOpenedTopComponent(ModeImpl mode, TopComponent tc) {
        boolean wasOpened = tc.isOpened();
        if(getModeOpenedTopComponents(mode).contains(tc)) {
            return;
        }

        // Validate the TopComponent was removed from other modes.
        removeTopComponentFromOtherModes(mode, tc);

        model.addModeOpenedTopComponent(mode, tc);

        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(mode, View.CHANGE_MODE_TOPCOMPONENT_ADDED,
                null, tc));
        }

        if( !wasOpened ) { //make sure componentOpened() is called just once
            // Notify opened.
            WindowManagerImpl.getInstance().notifyTopComponentOpened(tc);
        }
    }

    /** Adds opened TopComponent into model and requests view (if needed). */
    void addModeOpenedTopComponentNoNotify(ModeImpl mode, TopComponent tc) {
        if(getModeOpenedTopComponents(mode).contains(tc)) {
            return;
        }

        // Validate the TopComponent was removed from other modes.
        removeTopComponentFromOtherModes(mode, tc);

        model.addModeOpenedTopComponent(mode, tc);

        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(mode, View.CHANGE_MODE_TOPCOMPONENT_ADDED,
                null, tc));
        }
    }
    
    public void insertModeOpenedTopComponent(ModeImpl mode, TopComponent tc, int index) {
        boolean wasOpened = tc.isOpened();
        List openedTcs = getModeOpenedTopComponents(mode);
        if(index >= 0 && !openedTcs.isEmpty()
        && openedTcs.size() > index && openedTcs.get(index) == tc) {
            return;
        }
        
        // Validate the TopComponent was removed from other modes.
        removeTopComponentFromOtherModes(mode, tc);
        
        model.insertModeOpenedTopComponent(mode, tc, index);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(mode, View.CHANGE_MODE_TOPCOMPONENT_ADDED, // PENDING inserted?
                null, tc));
        }
        
        if( !wasOpened ) { //make sure componentOpened() is called just once
            // #102258: Notify opened when opened through openAtTabPosition as well
            WindowManagerImpl.getInstance().notifyTopComponentOpened(tc);
        }
    }
    
    public boolean addModeClosedTopComponent(ModeImpl mode, TopComponent tc) {
        boolean opened = getModeOpenedTopComponents(mode).contains(tc);
        
        if(opened && !tc.canClose()) {
            return false;
        }
        
        if(containsModeTopComponent(mode,tc) && !opened) {
            return false;
        }
        
        NbWindowImpl window = getWindowForMode(mode);
        if( isViewMaximized(window) && mode.getKind() == Constants.MODE_KIND_SLIDING ) {
            //134622 - unslide first if some other view is maximized, otherwise
            //the view being closed will reopen in slidebar after restoring from maximized mode
            mode = unSlide(tc, mode);
        }
        // Validate the TopComponent was removed from other modes.
        removeTopComponentFromOtherModes(mode, tc);
        
        model.addModeClosedTopComponent(mode, tc);

        ModeImpl oldActive = getActiveMode();
        ModeImpl newActive;
        if(model.getModeOpenedTopComponents(mode).isEmpty() && mode == oldActive) {
            newActive = setSomeModeActive(window);
        } else {
            newActive = oldActive;
        }

        // Unmaximize mode if necessary.
        if(getCurrentMaximizedMode(window) == mode && model.getModeOpenedTopComponents(mode).isEmpty()) {
            switchMaximizedMode(window, null);
        }
        
        if(isVisible() && opened) {
            viewRequestor.scheduleRequest(
                new ViewRequest(mode, View.CHANGE_MODE_TOPCOMPONENT_REMOVED,
                null, tc));
        }
        
        if(oldActive != newActive) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManagerImpl.PROP_ACTIVE_MODE, oldActive, newActive);
        }

        if(newActive != null) {
            // Notify registry.
            WindowManagerImpl.notifyRegistryTopComponentActivated(
                newActive.getSelectedTopComponent());
        } else {
            WindowManagerImpl.notifyRegistryTopComponentActivated(null);
        }
        
        // Notify closed.
        if(opened) {
            WindowManagerImpl.getInstance().notifyTopComponentClosed(tc);
        }
        System.out.println("Central:addModeClosedTopComponent, at bottom is the mode empty=" + mode.isEmpty());
        System.out.println("Mode=" + mode.getName());
        System.out.println("Mode.openTopComponents=" + mode.getOpenedTopComponentsIDs().size());
        System.out.println("Mode.closedTopComponents=" + mode.getClosedTopComponentsIDs().size());
        return true;
    }

    // XXX Could be called only during load phase of window system.
    public void addModeUnloadedTopComponent(ModeImpl mode, String tcID, int index) {
        TopComponentTracker.getDefault().add( tcID, mode );
        model.addModeUnloadedTopComponent(mode, tcID, index);
    }
    
    // XXX
    public void setUnloadedSelectedTopComponent(ModeImpl mode, String tcID) {
        model.setModeUnloadedSelectedTopComponent(mode, tcID);
    }
    
    public void setUnloadedPreviousSelectedTopComponent(ModeImpl mode, String tcID) {
        model.setModeUnloadedPreviousSelectedTopComponent(mode, tcID);
    }

    // XXX
    public List<String> getModeOpenedTopComponentsIDs(ModeImpl mode) {
        return model.getModeOpenedTopComponentsIDs(mode);
    }
    // XXX
    public List getModeClosedTopComponentsIDs(ModeImpl mode) {
        return model.getModeClosedTopComponentsIDs(mode);
    }
    // XXX
    public List<String> getModeTopComponentsIDs(ModeImpl mode) {
        return model.getModeTopComponentsIDs(mode);
    }
    
    /** Helper validation. */
    private boolean removeTopComponentFromOtherModes(ModeImpl mode, TopComponent tc) {
        NbWindowImpl window = null;
        
        boolean tcRemoved = false;
        for(Iterator it = model.getModes().iterator(); it.hasNext(); ) {
            ModeImpl m = (ModeImpl)it.next();
            if(m == mode) {
                continue;
            }
            
            if(model.containsModeTopComponent(m, tc)) {
                tcRemoved = true;
                window = getWindowForMode(m);
                
                model.removeModeTopComponent(m, tc, null);
//                debugLog("removeTopComponentFromOtherModes()");

                // Remove mode from model if is not permanennt and emptied.
                boolean modeRemoved = false;
                if(!m.isPermanent() && m.isEmpty() && doCheckSlidingModes(m) 
                    // now the tc is not added to the sliding mode yet, but is *somehow* expected to be..
                    // maybe needs redesign..
                        && mode.getKind() != Constants.MODE_KIND_SLIDING
                        //do not let remove a mode whose only TC is just being maximized
                        && m != modeBeingMaximized ) {
//                    debugLog("removeTopComponentFromOtherModes() - really removing=" + m.getDisplayName());
                    model.removeMode(m, false);
                    modeRemoved = true;
                }
            
                if(modeRemoved) {
                    WindowManagerImpl.getInstance().doFirePropertyChange(
                        WindowManager.PROP_MODES, null, null);
                }
            }
        }
        
        if(window != null) {
            // check to see if empty!
            boolean isEmpty = true;
            Set<ModeImpl> checkModes = model.getModesForWindow(window);
            for(ModeImpl m: checkModes) {
                if(!m.isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            if(isEmpty)
                destroyNbWindow(window);
        }
        
        return tcRemoved;
    }
    
    /** Removed top component from model and requests view (if needed). */
    public boolean removeModeTopComponent(ModeImpl mode, TopComponent tc) {
        if(!containsModeTopComponent(mode, tc)) {
            return false;
        }
        
        System.out.println("Central:removeModeTopComponent " + tc);
        
        boolean viewChange = getModeOpenedTopComponents(mode).contains(tc);
        
        if(viewChange && !tc.canClose()) {
            return false;
        }
        
        TopComponent recentTc = null;
        if( mode.getKind() == Constants.MODE_KIND_EDITOR ) {
            //an editor document is being closed so let's find the most recent editor to select
            recentTc = getRecentTopComponent( mode, tc );
        }
        model.removeModeTopComponent(mode, tc, recentTc);

        NbWindowImpl window = getWindowForMode(mode);

        ModeImpl oldActive = getActiveMode();
        ModeImpl newActive;
        if(model.getModeOpenedTopComponents(mode).isEmpty() && mode == oldActive) {
            newActive = setSomeModeActive(window);
        } else {
            newActive = oldActive;
        }

        // Unmaximize mode if necessary.
        if(getCurrentMaximizedMode(window) == mode && model.getModeOpenedTopComponents(mode).isEmpty()) {
            switchMaximizedMode(window, null);
        }
        
//        debugLog("removeModeTopComponent()");
        // Remove mode from model if is not permanennt and emptied.
        boolean modeRemoved = false;
        if(!mode.isPermanent() && model.getModeTopComponents(mode).isEmpty()) {
            System.out.println("IF THIS MODE IS EMPTY - REMOVE IT FROM THE MODEL - THIS leads to window closure?");
            // remove only if there's no other component in sliding modes that has this one as the previous mode.
            //TODO
            if (doCheckSlidingModes(mode)) {
//                debugLog("removeModeTopComponent() -removing " + mode.getDisplayName());
                model.removeMode(mode, false);
                modeRemoved = true;
            }
        }
        
        
        
        if(viewChange && isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_MODE_TOPCOMPONENT_REMOVED,
                null, tc));
        }
        
        // Notify closed.
        if(viewChange) {
            WindowManagerImpl.getInstance().notifyTopComponentClosed(tc);
        }
        
        if(oldActive != newActive) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManagerImpl.PROP_ACTIVE_MODE, oldActive, newActive);
        }
    
        if(modeRemoved) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManager.PROP_MODES, null, null);
        }
            
        if(newActive != null) {
            // Notify registry.
            WindowManagerImpl.notifyRegistryTopComponentActivated(
                newActive.getSelectedTopComponent());
        } else {
            WindowManagerImpl.notifyRegistryTopComponentActivated(
                null);
        }
        
        return true;
    }
    
    /**
     * Find TopComponent to be selected when the currently selected TC is closed
     * in the given mode.
     * @param editorMode Editor mode
     * @param closedTc TopComponent which is being closed in the given mode.
     * @return TopComponent to select or null (e.g. the mode will be empty after close)
     */
    TopComponent getRecentTopComponent( ModeImpl editorMode, TopComponent closedTc ) {
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        if (!WinSysPrefs.HANDLER.getBoolean(WinSysPrefs.EDITOR_CLOSE_ACTIVATES_RECENT, true)) {
            List<TopComponent> open = editorMode.getOpenedTopComponents();
            int pos = open.indexOf(closedTc);
            if (open.size() > 1 && pos >= 0) {
                if (pos > 0) { // select previous one
                    return open.get(pos - 1);
                } else { // this was first, so select next one
                    return open.get(1);
                }
            }
        }
        String[] ids = wm.getRecentViewIDList();
        
        for( String tcId : ids ) {
            ModeImpl mode = findMode(tcId);
            if (mode == null 
               || mode != editorMode) {
                continue;
            }
            TopComponent tc = wm.findTopComponent( tcId );
            if( tc == closedTc )
                continue;
            return tc;
        }
        return null;
    }

    private ModeImpl findMode( String tcId ) {
        for( ModeImpl mode : getModes() ) {
            if( mode.getTopComponentsIDs().contains( tcId ) )
                return mode;
        }
        return null;
    }
    
   // remove the mode only if there's no other component in sliding modes that has this one as the previous mode.
    boolean doCheckSlidingModes(ModeImpl mode) {
        NbWindowImpl window = model.getWindowForMode(mode);
        
        ModeImpl slid = model.getSlidingMode(window, Constants.BOTTOM);
        if (slid != null) {
            TopComponent[] tcs = slid.getTopComponents();
            for (int i = 0; i < tcs.length; i++) {
                String tcID = WindowManagerImpl.getInstance().findTopComponentID(tcs[i]);
                ModeImpl impl = model.getModeTopComponentPreviousMode(slid, tcID);
                if (impl == mode) {
                    return false;
                }
            }
        }
        slid = model.getSlidingMode(window, Constants.LEFT);
        if (slid != null) {
            TopComponent[] tcs = slid.getTopComponents();
            for (int i = 0; i < tcs.length; i++) {
                String tcID = WindowManagerImpl.getInstance().findTopComponentID(tcs[i]);
                ModeImpl impl = model.getModeTopComponentPreviousMode(slid, tcID);
                if (impl == mode) {
                    return false;
                }
            }
        }
        slid = model.getSlidingMode(window, Constants.RIGHT);
        if (slid != null) {
            TopComponent[] tcs = slid.getTopComponents();
            for (int i = 0; i < tcs.length; i++) {
                String tcID = WindowManagerImpl.getInstance().findTopComponentID(tcs[i]);
                ModeImpl impl = model.getModeTopComponentPreviousMode(slid, tcID);
                if (impl == mode) {
                    return false;
                }
            }
        }        
        slid = model.getSlidingMode(window, Constants.TOP);
        if (slid != null) {
            TopComponent[] tcs = slid.getTopComponents();
            for (int i = 0; i < tcs.length; i++) {
                String tcID = WindowManagerImpl.getInstance().findTopComponentID(tcs[i]);
                ModeImpl impl = model.getModeTopComponentPreviousMode(slid, tcID);
                if (impl == mode) {
                    return false;
                }
            }
        }        
        return true;
    }
    
    // XXX
    public void removeModeClosedTopComponentID(ModeImpl mode, String tcID) {
        // It is silent now, has to be used only for closed yet unloaded components!
        model.removeModeClosedTopComponentID(mode, tcID);
    }
    /// << Mode specific    
    //////////////////////////////
    
    // TopComponentGroup>>
    public boolean isGroupOpened(TopComponentGroupImpl tcGroup) {
        return model.isGroupOpened(tcGroup);
    }
    
    /** Opens TopComponentGroup. */
    public void openGroup(TopComponentGroupImpl tcGroup) {
        if(isGroupOpened(tcGroup)) {
            return;
        }
        
        //TODO gwi-group: openGroup - is this only for main window?
        
        NbWindowImpl window = null; // TODO gwi-max: NEED TO GET WINDOW FOR THIS TOP COMPONENT GROUP!
        
        
        if( isEditorMaximized(window) && isViewMaximized(window) )
            switchMaximizedMode(window, null );

        Set<TopComponent> openedBeforeTopComponents = new HashSet<TopComponent>();
        Set<TopComponent> tcs = tcGroup.getTopComponents();
        for(Iterator<TopComponent> it = tcs.iterator(); it.hasNext(); ) {
            TopComponent tc = it.next();
            if( tc.isOpened() ) {
                openedBeforeTopComponents.add( tc );
            }
        }
        
        tcs = tcGroup.getOpeningSet();
        HashSet<ModeImpl> openedModes = new HashSet<ModeImpl>( tcs.size() );
        List<TopComponent> openedTcs = new ArrayList<TopComponent>();
        for(Iterator<TopComponent> it = tcs.iterator(); it.hasNext(); ) {
            TopComponent tc = it.next();
            if(!tc.isOpened()) {
                WindowManagerImpl wm = WindowManagerImpl.getInstance();
                ModeImpl mode = (ModeImpl)wm.findMode(tc);
                if(mode == null) {
                    // Only view TopComponent is in group.
                    mode = wm.getDefaultViewMode(window);
                } else {
                    if( mode.getOpenedTopComponentsIDs().isEmpty() ) {
                        openedModes.add( mode );
                    }
                }
                model.addModeOpenedTopComponent(mode, tc);
                if (tc.getClientProperty(GROUP_SELECTED) != null) {
                    tc.requestVisible();
                }                
                if( isEditorMaximized(window) && mode.getState() != Constants.MODE_STATE_SEPARATED ) {
                    String tcID = wm.findTopComponentID( tc );
                    if( !isTopComponentDockedInMaximizedMode(window, tcID ) && mode.getKind() != Constants.MODE_KIND_SLIDING ) {
                        //slide the TopComponent to edgebar and slide it out
                        slide( tc, mode, getSlideSideForMode(window, mode ) );
                    }
                }
                openedTcs.add(tc);
            }
        }

        
        model.openGroup(tcGroup, new HashSet<TopComponent>(openedTcs), openedBeforeTopComponents);
        
        //restore selected TopComponents
        for( ModeImpl mode : openedModes ) {
            TopComponent prevSelTC = mode.getPreviousSelectedTopComponent();
            if( null != prevSelTC )
                mode.setSelectedTopComponent( prevSelTC );
        }
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(tcGroup, 
                View.CHANGE_TOPCOMPONENT_ARRAY_ADDED, null,
                openedTcs.toArray(new TopComponent[0])));
        }

        // Notify oepned.
        for(TopComponent tc: openedTcs) {
            WindowManagerImpl.getInstance().notifyTopComponentOpened(tc);
        }
    }
    
    /** Used to remember selected group member TC in mode so that it can be
     *  selected when group is opened.
     */
    private static final Object GROUP_SELECTED = new Object();
    
    /** Closes TopComponentGroup. */
    public void closeGroup(TopComponentGroupImpl tcGroup) {
        if(!isGroupOpened(tcGroup)) {
            return;
        }
        
        // TODO gwi-group

        NbWindowImpl window = null; // TODO gwi-max: NEED TO GET WINDOW FOR THIS TOP COMPONENT GROUP!

        if( isViewMaximized(window) ) {
            //#222210
            switchMaximizedMode(window,  null );
        }
        
        Set tcs = tcGroup.getClosingSet();
        List<TopComponent> closedTcs = new ArrayList<TopComponent>();
        
        Set<TopComponent> openedTcsByGroup = model.getGroupOpenedTopComponents(tcGroup);
        
        // Find out TC which were opened before the group was opened.
        Set<TopComponent> openedTcsBefore = model.getGroupOpenedBeforeTopComponents(tcGroup);

        // Adjust opening flags.
        for(Iterator<TopComponent> it = model.getGroupTopComponents(tcGroup).iterator(); it.hasNext(); ) {
            TopComponent tc = it.next();
            boolean wasOpenedBefore = openedTcsBefore.contains(tc);
            boolean openedByGroup = openedTcsByGroup.contains(tc);
            
            if(tc.isOpened()) {
                if(!wasOpenedBefore && !openedByGroup) {
                    // Open by group next time, user opened it while group was opened.
                    model.addGroupOpeningTopComponent(tcGroup, tc);
                }
            } else {
                if(wasOpenedBefore || openedByGroup) {
                    // Don't open by group next time, user closed it while group was opened.
                    model.removeGroupOpeningTopComponent(tcGroup, tc);
                }
            }
        }

        ArrayList<ModeImpl> groupModes = new ArrayList<ModeImpl>( tcs.size() );
        //remember which TCs are active
        for(Iterator it = tcs.iterator(); it.hasNext(); ) {
            TopComponent tc = (TopComponent)it.next();
            if( !tc.isOpened() || openedTcsBefore.contains(tc)) {
                continue;
            }
            ModeImpl mode = (ModeImpl)WindowManagerImpl.getInstance().findMode(tc);
            if( null != mode )
                groupModes.add( mode );
        }
        for( ModeImpl mode : groupModes ) {
            TopComponent selTC = mode.getSelectedTopComponent();
            if( null != selTC ) {
                String tcId = WindowManagerImpl.getInstance().findTopComponentID(selTC);
                if( null != tcId )
                    setModePreviousSelectedTopComponentID( mode, tcId );
            }
        }
        
        WindowManagerImpl wmi = WindowManagerImpl.getInstance();

        // Now close those which needed.
        for(Iterator it = tcs.iterator(); it.hasNext(); ) {
            TopComponent tc = (TopComponent)it.next();
            if(tc.isOpened()) {
                // Whether to ignore closing flag.
                if(openedTcsBefore.contains(tc)) {
                    continue;
                }
                //#202131 - avoid endless opening and closing of window groups
                //when non-document window from a TC group is dropped into editor area
                if( wmi.isEditorTopComponent( tc ) ) {
                    continue;
                }
                
                boolean ignore = false;
                for(Iterator it2 = model.getTopComponentGroups().iterator(); it2.hasNext(); ) {
                    TopComponentGroupImpl group = (TopComponentGroupImpl)it2.next();
                    if(group == tcGroup) {
                        continue;
                    }
                    if(group.isOpened() && group.getOpeningSet().contains(tc)) {
                        ignore = true;
                        break;
                    }
                }
                if(ignore) {
                    continue;
                }
                
                // Now you can close it.
                ModeImpl mode = (ModeImpl)WindowManagerImpl.getInstance().findMode(tc);
                if(mode != null) {
                    if (mode.getSelectedTopComponent() == tc) {
                        tc.putClientProperty(GROUP_SELECTED, Boolean.TRUE);
                    } else {
                        tc.putClientProperty(GROUP_SELECTED, null);
                    }
                    if (PersistenceHandler.isTopComponentPersistentWhenClosed(tc)) {
                        model.addModeClosedTopComponent(mode, tc);
                    } else {
                        if (Boolean.TRUE.equals(tc.getClientProperty(Constants.KEEP_NON_PERSISTENT_TC_IN_MODEL_WHEN_CLOSED))) {
                            model.addModeClosedTopComponent(mode, tc);
                        } else {
                            model.removeModeTopComponent(mode, tc, null);
                        }
                    }
                    closedTcs.add(tc);
                }
            }
        }

        model.closeGroup(tcGroup);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(tcGroup, 
                View.CHANGE_TOPCOMPONENT_ARRAY_REMOVED, null,
                closedTcs.toArray(new TopComponent[0])));
        }
        
        // Notify closed.
        for(TopComponent tc: closedTcs) {
            WindowManagerImpl.getInstance().notifyTopComponentClosed(tc);
        }
    }
    
    /** Adds TopComponentGroup into model. */
    public void addTopComponentGroup(TopComponentGroupImpl tcGroup) {
        model.addTopComponentGroup(tcGroup);
    }
    
    /** Removes TopComponentGroup from model. */
    public void removeTopComponentGroup(TopComponentGroupImpl tcGroup) {
        model.removeTopComponentGroup(tcGroup);
    }
    
    public boolean addGroupUnloadedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.addGroupUnloadedTopComponent(tcGroup, tcID);
    }
    
    public boolean removeGroupUnloadedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.removeGroupUnloadedTopComponent(tcGroup, tcID);
    }
    
    /** Adds opening top component for set into model. */
    public boolean addGroupUnloadedOpeningTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.addGroupUnloadedOpeningTopComponent(tcGroup, tcID);
    }
    
    /** Removes opening top component from model. */
    public boolean removeGroupUnloadedOpeningTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.removeGroupUnloadedOpeningTopComponent(tcGroup, tcID);
    }
    
    /** Adds closing top component for set into model. */
    public boolean addGroupUnloadedClosingTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.addGroupUnloadedClosingTopComponent(tcGroup, tcID);
    }
    
    /** Removes closing top component for set from model. */
    public boolean removeGroupUnloadedClosingTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        return model.removeGroupUnloadedClosingTopComponent(tcGroup, tcID);
    }
    
    // XXX Just helper for persistence.
    public boolean addGroupUnloadedOpenedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        if(!isGroupOpened(tcGroup)) {
            return false;
        }
        
        return model.addGroupUnloadedOpenedTopComponent(tcGroup, tcID);
    }
    
    // XXX Just helper for persistence
    public Set getGroupOpenedTopComponents(TopComponentGroupImpl tcGroup) {
        return model.getGroupOpenedTopComponents(tcGroup);
    }
    
    // XXX>>
    public Set<String> getGroupTopComponentsIDs(TopComponentGroupImpl tcGroup) {
        return model.getGroupTopComponentsIDs(tcGroup);
    }
    
    public Set<String> getGroupOpeningSetIDs(TopComponentGroupImpl tcGroup) {
        return model.getGroupOpeningSetIDs(tcGroup);
    }
    
    public Set<String> getGroupClosingSetIDs(TopComponentGroupImpl tcGroup) {
        return model.getGroupClosingSetIDs(tcGroup);
    }
    
    public Set<String> getGroupOpenedTopComponentsIDs(TopComponentGroupImpl tcGroup) {
        return model.getGroupOpenedTopComponentsIDs(tcGroup);
    }
    // XXX<<
    // TopComponentGroup<<
    //////////////////////////////
    
    // Mutators <<
    /////////////////////

    
    /////////////////////
    // Accessors>>
    
    /** Indicates whether windows system shows GUI. */
    public boolean isVisible() {
        return model.isVisible();
    }
    
    /** Gets <code>Set</code> of all <code>Mode</code>'s. */
    public Set<? extends ModeImpl> getModes () {
        return model.getModes();
    }

    /** Gets main window bounds for joined(tiled) state. */
    public Rectangle getMainWindowBoundsJoined() {
        return model.getMainWindowBoundsJoined();
    }
    
    /** Gets main window bounds for separated state. */
    public Rectangle getMainWindowBoundsSeparated() {
        return model.getMainWindowBoundsSeparated();
    }
    
    public int getMainWindowFrameStateJoined() {
        return model.getMainWindowFrameStateJoined();
    }
    
    public int getMainWindowFrameStateSeparated() {
        return model.getMainWindowFrameStateSeparated();
    }

    /** Gets active mode from model. */
    public ModeImpl getActiveMode () {
        return model.getActiveMode();
    }

    public ModeImpl getActiveMode (NbWindowImpl window) {
        return model.getActiveMode(window);
    }

    /** Gets last active editor mode from model. */
    public ModeImpl getLastActiveEditorMode () {
        return model.getLastActiveEditorMode();
    }
    
    /** Gets editor area bounds from model. */
    public Rectangle getEditorAreaBounds() {
        return model.getEditorAreaBounds();
    }

    /** Gets editor area constraints from model. */
    public SplitConstraint[] getEditorAreaConstraints(NbWindowImpl window) {
        return model.getEditorAreaConstraints(window);
    }

    /** Gets editor area state from model. */
    public int getEditorAreaState() {
        return model.getEditorAreaState();
    }
    
    public int getEditorAreaFrameState() {
        return model.getEditorAreaFrameState();
    }
    
    public Component getEditorAreaComponent(NbWindowImpl window) {
        return viewRequestor.getEditorAreaComponent(window);
    }

    /** Gets mode that is currently maximized (can be an editor or view component). */
    ModeImpl getCurrentMaximizedMode(NbWindowImpl window) {
        if( isViewMaximized(window) )
            return model.getViewMaximizedMode(window);
        if( isEditorMaximized(window) )
            return model.getEditorMaximizedMode(window);
        return null;
    }

    /** Gets editor maximized mode. */
    ModeImpl getEditorMaximizedMode(NbWindowImpl window) {
        return model.getEditorMaximizedMode(window);
    }

    /** Gets view maximized mode. */
    ModeImpl getViewMaximizedMode(NbWindowImpl window) {
        return model.getViewMaximizedMode(window);
    }
    
    /** Gets constraints for mode from model. */
    public SplitConstraint[] getModeConstraints(NbWindowImpl window, ModeImpl mode) {
        return model.getModeConstraints(window, mode);
    }


    /** Gets toolbar configuration name from model. */
    public String getToolbarConfigName () {
        return model.getToolbarConfigName();
    }

    ////////////////////////////////
    /// >> Mode specific
    /** Gets programatic name of mode. */
    public String getModeName(ModeImpl mode) {
        return model.getModeName(mode);
    }
    /** Gets bounds. */
    public Rectangle getModeBounds(ModeImpl mode) {
        return model.getModeBounds(mode);
    }
    /** Gets State. */
    public int getModeState(ModeImpl mode) {
        return model.getModeState(mode);
    }
    /** Gets kind. */
    public int getModeKind(ModeImpl mode) {
        return model.getModeKind(mode);
    }
    
    /** Gets side. */
    public String getModeSide(NbWindowImpl window, ModeImpl mode) {
        return model.getModeSide(window, mode);
    }
    
    /** Gets frame state. */
    public int getModeFrameState(ModeImpl mode) {
        return model.getModeFrameState(mode);
    }
    /** Gets used defined. */
    public boolean isModePermanent(ModeImpl mode) {
        return model.isModePermanent(mode);
    }
    public boolean isModeEmpty(ModeImpl mode) {
        return model.isModeEmpty(mode);
    }
    /** */
    public boolean containsModeTopComponent(ModeImpl mode, TopComponent tc) {
        return model.containsModeTopComponent(mode, tc);
    }
    /** Gets selected TopComponent. */
    public TopComponent getModeSelectedTopComponent(ModeImpl mode) {
        return model.getModeSelectedTopComponent(mode);
    }
    /**
     * @return ID of TopComponent that was previously selected in the given mode or null.
     */
    public String getModePreviousSelectedTopComponentID(ModeImpl mode) {
        return model.getModePreviousSelectedTopComponentID( mode );
    }
    /** Gets list of top components in this workspace. */
    public List<TopComponent> getModeTopComponents(ModeImpl mode) {
        return model.getModeTopComponents(mode);
    }
    /** Gets list of top components in this workspace. */
    public List<TopComponent> getModeOpenedTopComponents(ModeImpl mode) {
        return model.getModeOpenedTopComponents(mode);
    }
    
    /** Gets position of top component in given mode. */
    public int getModeTopComponentTabPosition(ModeImpl mode, TopComponent tc) {
        return model.getModeOpenedTopComponentTabPosition(mode, tc);
    }

    /**
     * 
     * @param mode
     * @return True if the given mode is minimized.
     * @since 2.30
     */
    boolean isModeMinimized( ModeImpl mode ) {
        return model.isModeMinimized( mode );
    }

    /**
     * Mark the given mode as minimized or docked.
     * @param mode
     * @param minimized True if the mode is minimized, false if docked.
     * @since 2.30
     */
    void setModeMinimized( ModeImpl mode, boolean minimized ) {
        model.setModeMinimized( mode, minimized );
    }

    /**
     * @param mode
     * @return List of Mode's additional names.
     * @since 2.30
     */
    Collection<String> getModeOtherNames( ModeImpl mode ) {
        return model.getModeOtherNames( mode );
    }

    /**
     * Add another mode name.
     * @param mode
     * @param modeOtherName 
     * @since 2.30
     */
    void addModeOtherName( ModeImpl mode, String modeOtherName ) {
        model.addModeOtherName( mode, modeOtherName );
    }
    
    /// << Mode specific
    ////////////////////////////////
    
    ////////////////////////////////////
    // TopComponentGroup specific >>
    public Set<TopComponentGroupImpl> getTopComponentGroups() {
        return model.getTopComponentGroups();
    }
    
    public String getGroupName(TopComponentGroupImpl tcGroup) {
        return model.getGroupName(tcGroup);
    }
    
    public Set<TopComponent> getGroupTopComponents(TopComponentGroupImpl tcGroup) {
        return model.getGroupTopComponents(tcGroup);
    }
    
    /** Gets opening top components for group from model. */
    public Set<TopComponent> getGroupOpeningTopComponents(TopComponentGroupImpl tcGroup) {
        return model.getGroupOpeningTopComponents(tcGroup);
    }
    
    /** Gets closing top components for group from model. */
    public Set getGroupClosingTopComponents(TopComponentGroupImpl tcGroup) {
        return model.getGroupClosingTopComponents(tcGroup);
    }
    // TopComponentGroup specific <<
    ////////////////////////////////////

    // Accessors<<
    /////////////////////
    
    
    // Others>>
    // PENDING>>
    public void topComponentDisplayNameChanged(ModeImpl mode, TopComponent tc) {
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_TOPCOMPONENT_DISPLAY_NAME_CHANGED, null, tc));
        }
    }

    public void topComponentDisplayNameAnnotation(ModeImpl mode, TopComponent tc) {
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_TOPCOMPONENT_DISPLAY_NAME_ANNOTATION_CHANGED, null, tc));
        }
    }
    // PENDING<<
    
    public void topComponentToolTipChanged(ModeImpl mode, TopComponent tc) {
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_TOPCOMPONENT_TOOLTIP_CHANGED, null, tc));
        }
    }
    
    public void topComponentIconChanged(ModeImpl mode, TopComponent tc) {
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(
                mode, View.CHANGE_TOPCOMPONENT_ICON_CHANGED, null, tc));
        }
    }

    /**
     * 
     * @param mode
     * @param tc
     * @param busy 
     * @since 2.45
     */
    public void topComponentMakeBusy( ModeImpl mode, TopComponent tc, boolean busy ) {
        String modeName = getModeName(mode);
        viewRequestor.scheduleRequest (
            new ViewRequest(modeName, busy ? View.TOPCOMPONENT_SHOW_BUSY : View.TOPCOMPONENT_HIDE_BUSY, tc, tc));
    }
    
    public void resetModel() {
        model.reset();
    }
    
    // Others<<
    
    
    // Compound ones>>
    
    /**
     * Creates a new mode on the side of the reference mode.
     * @param referenceMode
     * @param side
     * @param modeName
     * @param modeKind
     * @param permanent
     * @return 
     */
    ModeImpl attachModeToSide(NbWindowImpl window, ModeImpl referenceMode, String side, String modeName, int modeKind, boolean permanent ) {
        ModeImpl newMode = WindowManagerImpl.getInstance().createModeImpl(modeName, modeKind, permanent);
        
        model.addModeToSide(window, newMode, referenceMode, side);
        
        return newMode;
    }

    /** Creates new mode on side of specified one and puts there the TopComponentS. */
    private ModeImpl attachModeToSide(NbWindowImpl window, ModeImpl attachMode, String side, int modeKind) {
        return attachModeToSide(window, attachMode, side, ModeImpl.getUnusedModeName(), modeKind, false);
    }

    /** Creates new mode on side of desktop */
    private ModeImpl attachModeAroundDesktop(NbWindowImpl window, String side) {
        // New mode. It is necessary to add it yet.
        ModeImpl newMode = WindowManagerImpl.getInstance().createModeImpl(
            ModeImpl.getUnusedModeName(), Constants.MODE_KIND_VIEW, false);

        model.addModeAround(window, newMode, side);
        
        return newMode;
    }
    
    /** Creates new mode on side of editor area and puts there the TopComponentS. */
    private ModeImpl attachModeAroundEditor(NbWindowImpl window, String side, int modeKind) {
        // New mode. It is necessary to add it yet.
        ModeImpl newMode = WindowManagerImpl.getInstance().createModeImpl(
            ModeImpl.getUnusedModeName(), modeKind, false);

        model.addModeAroundEditor(window, newMode, side);

        return newMode;
    }
    
    private ModeImpl createFloatingMode(Rectangle bounds, int modeKind) {
        WindowManagerImpl wmi = WindowManagerImpl.getInstance();
        // New mode. It is necessary to add it yet.
        ModeImpl newMode = wmi.createModeImpl(
            ModeImpl.getUnusedModeName(), modeKind, Constants.MODE_STATE_SEPARATED, false);
        newMode.setBounds(bounds);
        
        model.addMode(null, newMode, new SplitConstraint[] {new SplitConstraint(Constants.HORIZONTAL, 100, 0.5f)});  

        return newMode;
    }

    /** */
    public void activateModeTopComponent(ModeImpl mode, TopComponent tc) {
        if(!getModeOpenedTopComponents(mode).contains(tc)) {
            return;
        }
        
        ModeImpl oldActiveMode = getActiveMode();
        //#45650 -some API users call the activation all over again all the time on one item.
        // improve performance for such cases.
        if (oldActiveMode != null && oldActiveMode.equals(mode)) {
            if (tc != null && tc.equals(model.getModeSelectedTopComponent(mode))) {
                // #82385, #139319 do repeat activation if focus is not
                // owned by tc to be activated
                Component fOwn = KeyboardFocusManager.getCurrentKeyboardFocusManager().
                        getFocusOwner();
                if (fOwn != null && SwingUtilities.isDescendingFrom(fOwn, tc)) {
                    //#70173 - activation request came probably from a sliding
                    //window in 'hover' mode, so let's hide it
                    slideOutSlidingWindows( mode );
                    return;
                }
            }
        }
        NbWindowImpl window = getWindowForMode(mode);
        model.setActiveMode(window, mode);
        model.setModeSelectedTopComponent(mode, tc);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(new ViewRequest(mode, 
                View.CHANGE_TOPCOMPONENT_ACTIVATED, null, tc));

            //restore floating windows if iconified
            if( mode.getState() == Constants.MODE_STATE_SEPARATED ) {
                Frame frame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, tc);
                if( null != frame && frame != WindowManagerImpl.getInstance().getMainWindow()
                        && (frame.getExtendedState() & Frame.ICONIFIED) > 0 ) {
                    frame.setExtendedState(frame.getExtendedState() - Frame.ICONIFIED );
                }
            }
        }
        
        // Notify registry.
        WindowManagerImpl.notifyRegistryTopComponentActivated(tc);
        
        if(oldActiveMode != mode) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManagerImpl.PROP_ACTIVE_MODE, oldActiveMode, mode);
        }
    }
    // Compound ones<<
    
    
    /**
     * Make sure no sliding window is slided-in.
     */
    protected void slideOutSlidingWindows( ModeImpl newActiveMode ) {
        for( ModeImpl mode : getModes() ) {
            if( !newActiveMode.equals(mode)
                && mode.getKind() == Constants.MODE_KIND_SLIDING
                && null != mode.getSelectedTopComponent() ) {
                setModeSelectedTopComponent( mode, null );
            }
        }
    }
    

    // Other >>
    public boolean isDragInProgress() {
        // XXX
        return viewRequestor.isDragInProgress();
    }
    
    public Frame getMainWindow() {
        // XXX
        return viewRequestor.getMainWindow(); 
    }
    
    public String guessSlideSide(TopComponent tc) {
        return viewRequestor.guessSlideSide(tc);
    }
    
    /**
     * Find the side (LEFT/RIGHT/BOTTOM) where the TopComponent from the given
     * mode should slide to.
     * 
     * @param mode Mode
     * @return The slide side for TopComponents from the given mode.
     */
    String getSlideSideForMode(NbWindowImpl window, ModeImpl mode) {
        return model.getSlideSideForMode( window, mode );
    }

    /** Tells whether given top component is inside joined mode (in main window)
     * or inside separate mode (separate window).
     *
     * @param the component in question
     * @return True when given component is docked, which means lives now
     * inside main window. False if component lives inside separate window.
     */
    public boolean isDocked (TopComponent comp) {
        ModeImpl mode = (ModeImpl)WindowManagerImpl.getInstance().findMode(comp);
        return mode != null && mode.getState() == Constants.MODE_STATE_JOINED;
    }

    
    // Other <<
    
    // Helper methods
    /** Creates model for mode, used internally. */
    public void createModeModel(ModeImpl mode, String name, int state, int kind, boolean permanent) {
        model.createModeModel(mode, name, state, kind, permanent); 
    }
    
    
    /** Creates model for top component group, used internally. */
    public void createGroupModel(TopComponentGroupImpl tcGroup, String name, boolean opened) {
        model.createGroupModel(tcGroup, name, opened);
    }
    
    // snapshot
    /** Creates window system model snapshot, used for requesting view. */
    public WindowSystemSnapshot createWindowSystemSnapshot() {
        return model.createWindowSystemSnapshot();
    }
    
    
    ///////////////////////////
    // ControllerHandler>>
    @Override
    public void userActivatedMode(ModeImpl mode) {
        if(mode != null) {
            setActiveMode(mode);
        }
    }
    
    @Override
    public void userActivatedModeWindow(ModeImpl mode) {
        if(mode != null) {
            setActiveMode(mode);
        }
    }
    
    @Override
    public void userActivatedEditorWindow() {
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        TopComponent[] tcs = wm.getRecentViewList(); 
        for(int i = 0; i < tcs.length; i++) {
            TopComponent tc = tcs[i];
            ModeImpl mode = (ModeImpl)wm.findMode(tc);
            if(mode != null 
            && mode.getKind() == Constants.MODE_KIND_EDITOR
            && !mode.getOpenedTopComponents().isEmpty()) {
                setActiveMode(mode);
                return;
            }
        }
        
        ModeImpl mode = wm.getDefaultEditorMode(null);  // GWI null window
        if(mode != null && !mode.getOpenedTopComponents().isEmpty()) {
            setActiveMode(mode);
        } else {
            // when someone calls this as a matter of activating editor mode as a fallback, but none is opened,
            // do unactivate the current selection.
            // #44389
            setActiveMode(null);
        }
    }
    
    @Override
    public void userActivatedTopComponent(ModeImpl mode, TopComponent selected) {
        if(mode != null) {
            setModeSelectedTopComponent(mode, selected);
        }
    }
    
    @Override
    public void userResizedMainWindow(Rectangle bounds) {
        if(getEditorAreaState() == Constants.EDITOR_AREA_JOINED) {
            model.setMainWindowBoundsJoined(bounds);
        } else {
            model.setMainWindowBoundsSeparated(bounds);
        }
    }
    
    @Override
    public void userResizedMainWindowBoundsSeparatedHelp(Rectangle bounds) {
        if(getEditorAreaState() == Constants.EDITOR_AREA_JOINED
        && getMainWindowBoundsSeparated().isEmpty()) {
            model.setMainWindowBoundsUserSeparatedHelp(bounds);
        }
    }
    
    @Override
    public void userResizedEditorArea(Rectangle bounds) {
        model.setEditorAreaBounds(bounds);
    }
    
    @Override
    public void userResizedEditorAreaBoundsHelp(Rectangle bounds) {
        if(getEditorAreaState() == Constants.EDITOR_AREA_JOINED
        && getEditorAreaBounds().isEmpty()) {
            model.setEditorAreaBoundsUserHelp(bounds);
        }
    }

    @Override
    public void userResizedModeBounds(ModeImpl mode, Rectangle bounds) {
        Rectangle old = model.getModeBounds(mode);
        model.setModeBounds(mode, bounds);
               
        mode.doFirePropertyChange(ModeImpl.PROP_BOUNDS, old, bounds);
    }
    
    @Override
    public void userResizedModeBoundsSeparatedHelp(ModeImpl mode, Rectangle bounds) {
        model.setModeBoundsSeparatedHelp(mode, bounds);
    }
    
    @Override
    public void userChangedFrameStateMainWindow(int frameState) {
        if(getEditorAreaState() == Constants.EDITOR_AREA_JOINED) {
            model.setMainWindowFrameStateJoined(frameState);
        } else {
            model.setMainWindowFrameStateSeparated(frameState);
        }
    }
    
    @Override
    public void userChangedFrameStateEditorArea(int frameState) {
        model.setEditorAreaFrameState(frameState);
    }
    
    @Override
    public void userChangedFrameStateMode(ModeImpl mode, int frameState) {
        model.setModeFrameState(mode, frameState);
    }
    
    @Override
    public void userChangedSplit(NbWindowImpl window, ModelElement[] snapshots, double[] splitWeights ) {
        model.setSplitWeights( window, snapshots, splitWeights );
    }

    @Override
    public void userClosedTopComponent(ModeImpl mode, TopComponent tc) {
        NbWindowImpl window = getWindowForMode(mode);
        if( mode == getCurrentMaximizedMode(window) && isViewMaximized(window) ) {
            switchMaximizedMode(window, null );
            for(Iterator it = getModes().iterator(); it.hasNext(); ) {
                ModeImpl newMode = (ModeImpl)it.next();

                if(newMode.containsTopComponent(tc)) {
                    userClosedTopComponent( newMode, tc );
                    return;
                }
            }
        }
        TopComponent recentTc = null;
        if( mode.getKind() == Constants.MODE_KIND_EDITOR ) {
            //an editor document is being closed so let's find the most recent editor to select
            recentTc = getRecentTopComponent( mode, tc );
        }
        boolean wasTcClosed = false;
        if (PersistenceHandler.isTopComponentPersistentWhenClosed(tc)) {
            wasTcClosed = addModeClosedTopComponent(mode, tc);
        } else {
            if (Boolean.TRUE.equals(tc.getClientProperty(Constants.KEEP_NON_PERSISTENT_TC_IN_MODEL_WHEN_CLOSED))) {
                wasTcClosed = addModeClosedTopComponent(mode, tc);
            } else {
                wasTcClosed = removeModeTopComponent(mode, tc);
            }
        }
        if( wasTcClosed 
                && mode.getKind() == Constants.MODE_KIND_EDITOR 
                && "editor".equals(mode.getName())  //NOI18N
                && mode.getOpenedTopComponentsIDs().isEmpty() ) {
            
            //134945 - if user just closed the last topcomponent in the default
            //and permanent "editor" mode then pick some other arbitrary editor mode
            //and move its topcomponents to the default editor mode. otherwise opening
            //of a new editor window will cause a split in the editor area.
            ModeImpl otherEditorMode = findSomeOtherEditorModeImpl();
            if( null != otherEditorMode ) {
                for( String closedTcId : otherEditorMode.getClosedTopComponentsIDs() ) {
                    mode.addUnloadedTopComponent(closedTcId);
                }
                List<TopComponent> tcs = otherEditorMode.getOpenedTopComponents();
                for( TopComponent t : tcs ) {
                    int index = otherEditorMode.getTopComponentTabPosition(t);
                    mode.addOpenedTopComponent(t, index);
                }
                removeMode(otherEditorMode);
            }
        }
        if ((recentTc != null) && wasTcClosed) {
            recentTc.requestActive();
        }
        //#177986 - repaint the main window when closing the last tc
        if( TopComponent.getRegistry().getOpened().isEmpty() ) {
            SwingUtilities.invokeLater( new Runnable() {
                @Override
                public void run() {
                    java.awt.Frame f = getMainWindow();
                    if( null != f && f.isVisible() ) {
                        f.invalidate();
                        f.repaint();
                    }
                }
            });
        }
    }
    
    /**
     * @return ModeImpl with opened TopComponents which is 'editor' kind but 
     * not the default and permanent one. Returns null if there is no such mode.
     */
    private ModeImpl findSomeOtherEditorModeImpl() {
        for( ModeImpl m : getModes() ) {
            if( m.getKind() == Constants.MODE_KIND_EDITOR 
                    && !"editor".equals(m.getName()) //NOI18N
                    && !m.getOpenedTopComponentsIDs().isEmpty()
                    && !m.isPermanent()
                    && m.getState() != Constants.MODE_STATE_SEPARATED )
                return m;
        }
        return null;
    }
    
    public void userClosedMode(ModeImpl mode) {
        if(mode != null) {
            NbWindowImpl window = getWindowForMode(mode);
            boolean allAreClosable = true;
            for( TopComponent tc : mode.getOpenedTopComponents() ) {
                if( !Switches.isClosingEnabled(tc) ) {
                    allAreClosable = false;
                    break;
                }
            }
            if( allAreClosable ) {
                closeMode(mode);
            } else {
                ArrayList<TopComponent> tcs = new ArrayList<>(mode.getOpenedTopComponents());
                for( TopComponent tc : tcs ) {
                    if( Switches.isClosingEnabled(tc) ) {
                        userClosedTopComponent(mode, tc);
                    }
                }
                
            }
            // Unmaximize if necessary.
            if(mode.getOpenedTopComponents().isEmpty()
                && mode == getCurrentMaximizedMode(window)) 
            {
                switchMaximizedMode(window, null);
            }
        }
        
    }
    
    
    // DnD
    @Override
    public void userDroppedTopComponents(NbWindowImpl window, ModeImpl mode, TopComponentDraggable draggable) {
        boolean unmaximize = false;
        if( draggable.isTopComponentTransfer() ) {
            unmaximize = moveTopComponentIntoMode( mode, draggable.getTopComponent() );
            System.out.print("User Dropped Top Componentns into mode " + mode);
        } else {
            TopComponent selTC = draggable.getMode().getSelectedTopComponent();
            mergeModes( draggable.getMode(), mode, -1 );
            if( null != selTC )
                mode.setSelectedTopComponent( selTC );
        }
        //NbWindowImpl window = getWindowForMode(mode);
        updateViewAfterDnD(null, unmaximize);
        updateViewAfterDnD(window, unmaximize);
    }
    
    @Override
    public void userDroppedTopComponents(NbWindowImpl window, ModeImpl mode, TopComponentDraggable draggable, int index) {
        boolean unmaximize = false;
        if( draggable.isTopComponentTransfer() ) {
            unmaximize = moveTopComponentIntoMode( mode, draggable.getTopComponent(), index );
        } else {
            TopComponent selTC = draggable.getMode().getSelectedTopComponent();
            mergeModes( draggable.getMode(), mode, index );
            if( null != selTC )
                mode.setSelectedTopComponent( selTC );
        }
        //NbWindowImpl window = getWindowForMode(mode);
        updateViewAfterDnD(window, unmaximize);
    }
    
    @Override
    public void userDroppedTopComponents(NbWindowImpl window, ModeImpl mode, TopComponentDraggable draggable, String side) {
        ModeImpl newMode = attachModeToSide(window, mode, side, mode.getKind() );
        if( draggable.isTopComponentTransfer() ) {
            moveTopComponentIntoMode( newMode, draggable.getTopComponent() );
        } else {
            if( newMode.getKind() != draggable.getKind() ) {
                mergeModes( draggable.getMode(), newMode, -1 );
            } else {
                dockMode( newMode, draggable.getMode() );
            }
        }
        
        updateViewAfterDnD(window, true);
    }
    
    @Override
    public void userDroppedTopComponentsIntoEmptyEditor(NbWindowImpl window, TopComponentDraggable draggable) {
        // PENDING
        String editorModeName = "editor";
        if(window instanceof NbWindowImpl)
            editorModeName = window.getName() + "_editor";
        ModeImpl mode = (ModeImpl)WindowManagerImpl.getInstance().findMode(editorModeName); // NOI18N
        if( null == mode || mode.getState() == Constants.MODE_STATE_SEPARATED ) {
            for( ModeImpl m : getModes() ) {
                if( m.getKind() == Constants.MODE_KIND_EDITOR && m.getState() == Constants.MODE_STATE_JOINED ) {
                    mode = m;
                    break;
                }
            }
        }
        
        
        if( null == mode || mode == draggable.getMode() ) {
            if( draggable.isModeTransfer() && draggable.getMode().getKind() == Constants.MODE_KIND_EDITOR ) {
                userDockedMode( draggable.getMode() );
            }
            return;
        }
        if( draggable.isTopComponentTransfer() ) {
            moveTopComponentIntoMode(mode, draggable.getTopComponent());
        } else {
            if( mode.getKind() != draggable.getKind() ) {
                mergeModes( draggable.getMode(), mode, 0 );
            } else {
                dockMode( mode, draggable.getMode() );
            }
        }
        updateViewAfterDnD(window, true);
    }
    
    @Override
    public void userDroppedTopComponentsAround(NbWindowImpl window, TopComponentDraggable draggable, String side) {
        ModeImpl newMode = attachModeAroundDesktop(window, side );
        if( draggable.isTopComponentTransfer() ) {
            moveTopComponentIntoMode( newMode, draggable.getTopComponent() );
        } else {
            dockMode( newMode, draggable.getMode() );
        }

        //NbWindowImpl window = getWindowForMode(newMode);
        updateViewAfterDnD(window, true);
    }
    
    @Override
    public void userDroppedTopComponentsAroundEditor(NbWindowImpl window, TopComponentDraggable draggable, String side) {
        ModeImpl newMode = attachModeAroundEditor(window, side, draggable.getKind() );
        if( draggable.isTopComponentTransfer() ) {
            moveTopComponentIntoMode( newMode, draggable.getTopComponent() );
        } else {
            dockMode( newMode, draggable.getMode() );
        }
        updateViewAfterDnD(window, true);
    }
    
    @Override
    public void userDroppedTopComponentsIntoFreeArea(TopComponentDraggable draggable, Rectangle bounds) {
        NbWindowImpl window = null;
        if( draggable.isTopComponentTransfer() ) {
            if (Boolean.getBoolean("netbeans.winsys.enhanced")) {
                TopComponent tc = draggable.getTopComponent();
                
                Collection<? extends NbWindowSelector> selectors = Lookup.getDefault().lookupAll(NbWindowSelector.class);
                Boolean isDialogRequested = null;
                for (NbWindowSelector s : selectors) {
                    isDialogRequested = s.isDialogRequested(tc);
                    if(isDialogRequested != null) {
                        break;
                    }                
                }
                                
                String nbWindowName = getUnusedNbWindowName();
                window = (NbWindowImpl)WindowManagerImpl.getInstance().createNbWindow(nbWindowName, bounds, Boolean.TRUE.equals(isDialogRequested)); 

            // a  nbwindow name can be changed via setName - ? Should that be allowed ? Should we also have a UID?
//            ModeImpl newMode = WindowManagerImpl.getInstance().createModeImpl(nbWindowName + "_editor", Constants.MODE_KIND_EDITOR, Constants.MODE_STATE_JOINED, true);  
//            model.addMode(window, newMode, new SplitConstraint[0]);
//
//            ModeImpl newMode2 = WindowManagerImpl.getInstance().createModeImpl(nbWindowName + "_bottomSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);  
//            model.addSlidingMode(window, newMode2, "bottom", null);
//
//            ModeImpl newMode3 = WindowManagerImpl.getInstance().createModeImpl(nbWindowName + "_rightSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);  
//            model.addSlidingMode(window, newMode3, "right", null);
//
//            ModeImpl newMode4 = WindowManagerImpl.getInstance().createModeImpl(nbWindowName + "_leftSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);  
//            model.addSlidingMode(window, newMode4, "left", null);
//
//            ModeImpl newMode5 = WindowManagerImpl.getInstance().createModeImpl(nbWindowName + "_topSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true); 
//            model.addSlidingMode(window, newMode5, "top", null);
                
                
               
                // move top component into mode
                ModeImpl nbWindowMode = null;
                String name = nbWindowName + "_editor";
                for (ModeImpl m : getModes()) {
                    if (m.getName().equals(name)) {
                        nbWindowMode = m;
                        break;
                    }
                }
                moveTopComponentIntoMode(nbWindowMode, draggable.getTopComponent());
                nbWindowMode.setSelectedTopComponent(draggable.getTopComponent());
            } else {
                ModeImpl newMode = createFloatingMode( bounds, draggable.getKind() );
                moveTopComponentIntoMode( newMode, draggable.getTopComponent() );
                newMode.setSelectedTopComponent( draggable.getTopComponent() );
                window = null;
            }
        } else {
            userUndockedMode( draggable.getMode(), bounds );
        }
        updateViewAfterDnD(window, false);
    }

    /**
     * User made the given Mode floating.
     * @param mode 
     * @since 2.30
     */
    public void userUndockedMode( ModeImpl mode ) {
        NbWindowImpl window = getWindowForMode(mode);
        if (getCurrentMaximizedMode(window) == mode) {
            switchMaximizedMode(window, null);
        }
        Rectangle modeBounds = null;
        TopComponent tc = mode.getSelectedTopComponent();
        if( null != tc ) {
            Point tcLoc = tc.getLocation();
            Dimension tcSize = tc.getSize();
            SwingUtilities.convertPointToScreen(tcLoc, tc);
            modeBounds = new Rectangle(tcLoc, tcSize);
        }

        userUndockedMode( mode, modeBounds );
    }

    private void userUndockedMode( ModeImpl mode, Rectangle modeBounds ) {
        int modeKind = mode.getKind();
        if (modeKind == Constants.MODE_KIND_SLIDING) {
            modeKind = Constants.MODE_KIND_VIEW;
        }
        NbWindowImpl window = getWindowForMode(mode);
        if (getCurrentMaximizedMode(window) == mode) {
            switchMaximizedMode(window, null);
        }
        
        if (Boolean.getBoolean("netbeans.winsys.enhanced")) {
            // new behavior - create a nbwindow
            String nbWindowName = getUnusedNbWindowName();
            //TODO gwi-window: Need a way to determine if we need a frame or a dialog
            WindowManagerImpl.getInstance().createNbWindow(nbWindowName, modeBounds, false); // do I need to adjust size  to accomodate frame?

            ModeImpl nbMode = null;
            String name = nbWindowName + "_editor";
            for (ModeImpl m : getModes()) {
                if (m.getName().equals(name)) {
                    nbMode = m;
                    break;
                }
            }            
            mergeModes(mode, nbMode, modeKind);
        } else {
            // original behavior
            if( null != modeBounds ) {
                model.setModeBounds( mode, modeBounds );
            }

            SplitConstraint[] constraints = mode.getConstraints();
            model.setModeState( mode, Constants.MODE_STATE_SEPARATED );

            ModeImpl previousMode = WindowManagerImpl.getInstance().createMode(null, null, mode.getKind(), Constants.MODE_STATE_JOINED, true, constraints);
            constraints = previousMode.getConstraints();
            List<String> openedIDs = mode.getOpenedTopComponentsIDs();
            for( String tcID : getModeTopComponentsIDs( mode ) ) {
                model.setModeTopComponentPreviousMode(mode, tcID, previousMode, openedIDs.indexOf( tcID ));
                model.setModeTopComponentPreviousConstraints(mode, tcID, constraints);
            }
            model.setModeConstraints(window, mode, new SplitConstraint[0] );
        }
        updateViewAfterDnD(window, false);
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    /**
     * User docked the given mode (from floating state).
     * @param mode 
     * @since 2.30
     */
    public void userDockedMode( ModeImpl mode ) {
        int modeKind = mode.getKind();
        if (modeKind == Constants.MODE_KIND_SLIDING) {
            modeKind = Constants.MODE_KIND_VIEW;
        }
        NbWindowImpl window = getWindowForMode(mode);
        switchMaximizedMode(window, null);

        TopComponent selectedTC = mode.getSelectedTopComponent();

        if( !mode.isPermanent() ) {
            for( TopComponent tc : mode.getOpenedTopComponents() ) {
                userDockedTopComponent( tc, mode );
            }
        } else {
        
            List<String> ids = mode.getTopComponentsIDs();
            if( !ids.isEmpty() ) {
                String tcID = ids.get( 0 );
                ModeImpl previousMode = model.getModeTopComponentPreviousMode( mode, tcID );
                if( null == previousMode || !model.getModes().contains(previousMode) ) {
                    SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints( mode, tcID );
                    if( null != constraints ) {
                        previousMode = findJoinedMode( modeKind, constraints);
                    }
                }
                if( null == previousMode ) {
                    SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints( mode, tcID );
                    if( null != constraints )
                        model.setModeConstraints(window, mode, constraints );
                    model.setModeState( mode, Constants.MODE_STATE_JOINED );
                } else {
                    if( previousMode.isPermanent() && !previousMode.getTopComponentsIDs().isEmpty() ) {
                        List<String> opened = mode.getOpenedTopComponentsIDs();
                        for( String id : opened ) {
                            int prevIndex = model.getModeTopComponentPreviousIndex( mode, id );
                            TopComponent tc = WindowManagerImpl.getInstance().findTopComponent( id );
                            previousMode.addOpenedTopComponent( tc, prevIndex );
                        }
                        mergeModes( mode, previousMode, -1 );
                        mode = null;
                    } else {
                        dockMode( previousMode, mode );
                    }
                }
            }
            if( null != mode ) {
                SplitConstraint[] constraints = mode.getConstraints();
                if( null == constraints || constraints.length == 0 ) {
                    //just a fallback, it shouldn't really happen
                    model.setModeConstraints(window, mode, new SplitConstraint[] { new SplitConstraint( Constants.HORIZONTAL, 0, 0.2) } );
                }
                model.setModeState( mode, Constants.MODE_STATE_JOINED );
            }
        }
        updateViewAfterDnD(window, false);
        if( null != selectedTC )
            selectedTC.requestActive();
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }
    
    /**
     * Merge the given Modes into a single name.
     * @param source Mode which will disappear after the merge.
     * @param target Mode which will receive all TopComponents from the source
     * mode.
     * @since 2.30
     */
    private void mergeModes( ModeImpl source, ModeImpl target, int index ) {
        ModeImpl prevMode = null;
        SplitConstraint[] prevConstraints = null;
        if( source.isPermanent() && !target.isPermanent() ) {
            prevMode = getPreviousMode( source );
            prevConstraints = getPreviousConstraints( source );
        } else {
            prevMode = getPreviousMode( target );
            prevConstraints = getPreviousConstraints( target );
        }
        int tmpIndex = index;
        for( String tcID: source.getTopComponentsIDs() ) {
            target.addUnloadedTopComponent( tcID, tmpIndex );
            if( tmpIndex >= 0 )
                tmpIndex++;
        }
        tmpIndex = index;
        List<TopComponent> opened = source.getOpenedTopComponents();
        for( TopComponent tc : opened ) {
            target.addOpenedTopComponent( tc, tmpIndex );
            if( tmpIndex >= 0 )
                tmpIndex++;
            TopComponentTracker.getDefault().add( tc, target );
        }
        if(source.isPermanent()) {
            target.addOtherName( source.getName() );
            for( String otherName : source.getOtherNames() ) {
                target.addOtherName( otherName );
            }
        }
        if( source.isPermanent() )
            model.makeModePermanent( target );
        if( target.getState() == Constants.MODE_STATE_SEPARATED ) {
            setPreviousMode( target, prevMode );
            setPreviousConstraints( target, prevConstraints );
        }
        model.removeMode( source, false );
    }
    
    /**
     * Find the mode which the TopComponents of the given mode were in before.
     * @param mode
     * @return 
     * @since 2.30
     */
    private ModeImpl getPreviousMode( ModeImpl mode ) {
        ModeImpl prevMode = null;
        if( mode.getState() == Constants.MODE_STATE_JOINED ) {
            prevMode = mode;
        } else {
            List<String> ids = mode.getTopComponentsIDs();
            if( !ids.isEmpty() )
                prevMode = model.getModeTopComponentPreviousMode( mode, ids.get( 0 ) );
        }
        if( prevMode != null && !getModes().contains( prevMode ) )
            prevMode = null;
        return prevMode;
    }
    
    /**
     * Find previous split constraints of TopComponents in the given mode.
     * @param mode
     * @return 
     * @since 2.30
     */
    private SplitConstraint[] getPreviousConstraints( ModeImpl mode ) {
        ModeImpl prevMode = getPreviousMode( mode );
        if( null != prevMode )
            return mode.getConstraints();
        List<String> ids = mode.getTopComponentsIDs();
        if( !ids.isEmpty() )
            return model.getModeTopComponentPreviousConstraints( mode, ids.get( 0 ) );
        return null;
    }
    
    /**
     * Set previous mode for all TopComponents in given mode.
     * @param mode Mode to adjust
     * @param prevMode Previous mode
     * @since 2.30
     */
    private void setPreviousMode( ModeImpl mode, ModeImpl prevMode ) {
        for( String tcId : mode.getTopComponentsIDs() ) {
            int prevIndex = model.getModeTopComponentPreviousIndex( mode, tcId );
            model.setModeTopComponentPreviousMode( mode, tcId, prevMode, prevIndex );
        }
    }
    
    /**
     * Set previous split constraints for all TopComponents in given mode.
     * @param mode
     * @param prevConstraints 
     * @since 2.30
     */
    private void setPreviousConstraints( ModeImpl mode, SplitConstraint[] prevConstraints ) {
        for( String tcId : mode.getTopComponentsIDs() ) {
            model.setModeTopComponentPreviousConstraints( mode, tcId, prevConstraints );
        }
    }
    
    private void dockMode( ModeImpl prevMode, ModeImpl floatingMode ) {
        ModeImpl floatingPrevMode = getPreviousMode( floatingMode );
        List<TopComponent> opened = prevMode.getOpenedTopComponents();
        for( TopComponent tc : opened ) {
            floatingMode.addOpenedTopComponent( tc );
        }
        for( String tcID: prevMode.getClosedTopComponentsIDs() ) {
            floatingMode.addUnloadedTopComponent( tcID );
        }
        model.dockMode( prevMode, floatingMode );
        setPreviousMode( floatingMode, null );
        setPreviousConstraints( floatingMode, null );
        if( null != floatingPrevMode && floatingPrevMode.getTopComponentsIDs().isEmpty() ) {
            model.removeMode( floatingPrevMode, false );
        }
    }

    /**
     * User minimized the whole Mode
     * @param mode 
     * @since 2.30
     */
    void userMinimizedMode(NbWindowImpl window, ModeImpl mode ) {
        List<TopComponent> opened = mode.getOpenedTopComponents();
        TopComponent selTc = mode.getSelectedTopComponent();
        String side = getSlideSideForMode(window, mode );
        for( TopComponent tc : opened ) {
            slide( tc, mode, side );
        }
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        int index = 0;
        for( TopComponent tc : opened ) {
            ModeImpl newMode = ( ModeImpl ) wm.findMode( tc );
            if( null == newMode )
                continue;
            String tcId = wm.findTopComponentID( tc );
            model.setModeTopComponentPreviousMode(newMode, tcId, mode, index++);
        }
        setModeMinimized( mode, true );
        if( null != selTc ) {
            mode.setPreviousSelectedTopComponentID( wm.findTopComponentID(selTc) );
        }
    }

    /**
     * User restored a Mode from minimized state in given sliding Mode.
     * @param slidingMode
     * @param modeToRestore 
     * @since 2.35
     */
    void userRestoredMode(ModeImpl slidingMode, ModeImpl modeToRestore ) {
        TopComponent tcToSelect = modeToRestore.getPreviousSelectedTopComponent();
        setModeMinimized( modeToRestore, false );
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        for( TopComponent tc : slidingMode.getOpenedTopComponents() ) {
            String id = wm.findTopComponentID( tc );
            ModeImpl prevMode = model.getModeTopComponentPreviousMode( slidingMode, id );
            if( null != prevMode && prevMode.equals( modeToRestore ) ) {
                int prevIndex = model.getModeTopComponentPreviousIndex(slidingMode, id);
                moveTopComponentIntoMode(prevMode, tc, prevIndex);

            }
        }
        if( null != tcToSelect )
            modeToRestore.setSelectedTopComponent( tcToSelect );
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_TOPCOMPONENT_AUTO_HIDE_DISABLED, null, null));
        }
        setActiveMode( modeToRestore );
    }

    @Override
    public void userUndockedTopComponent(TopComponent tc, ModeImpl mode) {
        Point tcLoc = tc.getLocation();
        Dimension tcSize = tc.getSize();
        SwingUtilities.convertPointToScreen(tcLoc, tc);
        Rectangle bounds = new Rectangle(tcLoc, tcSize);
        // #89100: update mode kind when undocking view in sliding mode
        int modeKind = mode.getKind();
        if (modeKind == Constants.MODE_KIND_SLIDING) {
            modeKind = Constants.MODE_KIND_VIEW;
        }
        // #81479: unmaximize only if desirable
        NbWindowImpl window = getWindowForMode(mode);
        if (getCurrentMaximizedMode(window) == mode &&
            mode.getOpenedTopComponents().size() == 1 &&
            mode.getOpenedTopComponents().get(0) == tc) {
            switchMaximizedMode(window, null);
        }
        
        if (Boolean.getBoolean("netbeans.winsys.enhanced")) {
            // new behavior - create a nbwindow
            String nbWindowName = getUnusedNbWindowName();

            // TODO gwi-window: Need a way to determine frame or dialog
            Collection<? extends NbWindowSelector> selectors = Lookup.getDefault().lookupAll(NbWindowSelector.class);
            Boolean isDialogRequested = null;
            for (NbWindowSelector s : selectors) {
                isDialogRequested = s.isDialogRequested(tc);
                if(isDialogRequested != null) {
                    break;
                }                
            }
            WindowManagerImpl.getInstance().createNbWindow(nbWindowName, bounds, isDialogRequested);
            
            // move top component into mode
            ModeImpl nbMode = null;
            String name = nbWindowName + "_editor";
            for (ModeImpl m : getModes()) {
                if (m.getName().equals(name)) {
                    nbMode = m;
                    break;
                }
            }
            moveTopComponentIntoMode(nbMode, tc);
            nbMode.setSelectedTopComponent(tc);
        } else {
            // original behavior create a floating mode
            ModeImpl newMode = createFloatingMode( bounds, modeKind );
            moveTopComponentIntoMode( newMode, tc );
        }
        updateViewAfterDnD(window, false);
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    @Override
    public void userDockedTopComponent(TopComponent tc, ModeImpl mode) {
        ModeImpl dockTo = null;
        // find saved previous mode or at least constraints (=the place) to dock back into
        String tcID = WindowManagerImpl.getInstance().findTopComponentID(tc);
        ModeImpl source = (ModeImpl) WindowManagerImpl.getInstance().findMode(tc);
        dockTo = model.getModeTopComponentPreviousMode(source, tcID);
        int dockIndex = model.getModeTopComponentPreviousIndex(source, tcID);
        int modeKind = mode.getKind();
        
        if ((dockTo == null) || !model.getModes().contains(dockTo) || dockTo.getState() == Constants.MODE_STATE_SEPARATED) {
            // mode to dock to back isn't valid anymore, try constraints
            SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints(source, tcID);
            if (constraints != null) {
                //there might be some mode with the same constraints already
                dockTo = findJoinedMode( modeKind, constraints );
                if( null == dockTo ) {
                    // create mode with the same constraints to dock topcomponent back into
                    dockTo = WindowManagerImpl.getInstance().createModeImpl(
                            ModeImpl.getUnusedModeName(), modeKind, false);
                    model.addMode(null, dockTo, constraints);  // TODO gwi: user docked top component will use main window?
                }
            }
        }
        
        if (dockTo == null) {
            JOptionPane.showMessageDialog(null, "What window to use?");
            // fallback, previous saved mode not found somehow, use default modes
            dockTo = modeKind == Constants.MODE_KIND_EDITOR
                    ? WindowManagerImpl.getInstance().getDefaultEditorMode(null) //TODO gwi: WHAT WINDOW TO USE?
                    : WindowManagerImpl.getInstance().getDefaultViewMode(null);  //TODO gwi: WHAT WINDOW TO USE?
        }
        moveTopComponentIntoMode(dockTo, tc, dockIndex);
        NbWindowImpl window = getWindowForMode(dockTo);
        updateViewAfterDnD(window, false);
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }
    
    /**
     * 
     * @param draggable 
     * @since 2.37
     */
    void userStartedKeyboardDragAndDrop( TopComponentDraggable draggable ) {
        viewRequestor.userStartedKeyboardDragAndDrop( draggable );
    }

    private ModeImpl findJoinedMode( int modeKind, SplitConstraint[] constraints ) {
        for( ModeImpl m : getModes() ) {
            if( m.getKind() != modeKind || m.getState() != Constants.MODE_STATE_JOINED )
                continue;
            
            SplitConstraint[] modeConstraints = m.getConstraints();
            if( modeConstraints.length != constraints.length )
                continue;
            boolean match = true;
            for( int i=0; i<constraints.length; i++ ) {
                if( constraints[i].orientation != modeConstraints[i].orientation 
                    || constraints[i].index != modeConstraints[i].index ) {
                    match = false;
                    break;
                }
            }
            if( match )
                return m;
        }
        return null;
    }
    
    private boolean moveTopComponentIntoMode(ModeImpl mode, TopComponent tc) {
        return moveTopComponentIntoMode(mode, tc, -1);
    }
    
    private boolean moveTopComponentIntoMode(ModeImpl mode, TopComponent tc, int index) {
        boolean moved = false;
        boolean intoSliding = mode.getKind() == Constants.MODE_KIND_SLIDING;
        boolean intoSeparate = mode.getState() == Constants.MODE_STATE_SEPARATED;
        ModeImpl prevMode = null;
        String tcID = WindowManagerImpl.getInstance().findTopComponentID(tc);
        // XXX
        if(!mode.canContain(tc)) {
            return false;
        }
        TopComponentTracker.getDefault().add( tc, mode );
        for(Iterator it = model.getModes().iterator(); it.hasNext(); ) {
            ModeImpl m = (ModeImpl)it.next();
            if(model.containsModeTopComponent(m, tc)) {
                if (m.getKind() == Constants.MODE_KIND_SLIDING ||
                    m.getState() == Constants.MODE_STATE_SEPARATED) {
                    prevMode = model.getModeTopComponentPreviousMode(m, tcID);
                } else {
                    prevMode = m;
                }
                break;
            }
        }
        int prevIndex = prevMode != null && (intoSliding || intoSeparate) ? prevMode.getOpenedTopComponentsIDs().indexOf( tcID ) : -1;
        if(removeTopComponentFromOtherModes(mode, tc)) {
            moved = true;
        }
        if (index > -1) {
            model.insertModeOpenedTopComponent(mode, tc, index);
        } else {
            model.addModeOpenedTopComponent(mode, tc);
        }
        if (prevMode != null && (intoSliding || intoSeparate)) {
            if( intoSeparate && mode.isPermanent() ) {
                //TC is being moved to a undocked mode so from now it will be
                //groupped with other TCs in that mode
                //so change the previous mode to ensure it docks back into
                //the same mode as other TCs in this mode
                List<String> ids = mode.getTopComponentsIDs();
                if( !ids.isEmpty() ) {
                    ModeImpl groupPrevMode = model.getModeTopComponentPreviousMode( mode, ids.get( 0 ) );
                    if( null != groupPrevMode ) {
                        prevMode = groupPrevMode;
                    }
                }
            }
            // remember previous mode and constraints for precise de-auto-hide
            NbWindowImpl window = getWindowForMode(prevMode);
            model.setModeTopComponentPreviousMode(mode, tcID, prevMode, prevIndex);
            model.setModeTopComponentPreviousConstraints(mode, tcID, model.getModeConstraints(window, prevMode));
        }
        
        NbWindowImpl window = getWindowForMode(mode);
        
        if (!intoSliding) {
            // make the target mode active after dragging..
            model.setActiveMode(window, mode);
            model.setModeSelectedTopComponent(mode, tc);
        } else {
            sortSlidedOutTopComponentsByPrevModes( mode );
            // don't activate sliding modes, it means the component slides out, that's a bad thing..
            // make some other desktop mode active
            if(prevMode != null && prevMode == getActiveMode() 
                   && prevMode.getOpenedTopComponents().isEmpty()) {
                setSomeModeActive(window);
            }
            // check the drop mode if it was already used, if not, assign it some reasonable size, 
            // according to the current component.
            if (mode.getBounds().width == 0 && mode.getBounds().height == 0) {
                // now we have the sliding mode in initial state
                mode.setBounds(tc.getBounds());
            }            
        }
        //#232061 
        if( null != tc.getClientProperty("windnd_cloned_tc")) {
            tc.putClientProperty("windnd_cloned_tc", null);
            WindowManagerImpl.getInstance().notifyTopComponentOpened(tc);
        }
        return moved;
    }

    private void sortSlidedOutTopComponentsByPrevModes( ModeImpl slidingMode ) {
        if( !Switches.isModeSlidingEnabled() )
            return;
        
        List<String> opened = slidingMode.getOpenedTopComponentsIDs();
    }
    
    private void updateViewAfterDnD(NbWindowImpl window, boolean unmaximize) {
        if( unmaximize ) {
            switchMaximizedMode(window, null);
        }
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_DND_PERFORMED, null, null));
            FloatingWindowTransparencyManager.getDefault().update();
        }
    }

    
    // Sliding
    
   /** Adds mode into model and requests view (if needed). */
    public void addSlidingMode(NbWindowImpl window, ModeImpl mode, ModeImpl original, String side, Map<String,Integer> slideInSizes) {
//        NbWindowImpl window = model.getWindowForMode(mode);
        
        ModeImpl targetMode = model.getSlidingMode(window, side);
        if (targetMode != null) {
            //TODO what to do here.. something there already
            return;
        }
            targetMode = WindowManagerImpl.getInstance().createModeImpl(
                ModeImpl.getUnusedModeName(), Constants.MODE_KIND_SLIDING, false);
        
        model.addSlidingMode(window, mode, side, slideInSizes);
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_MODE_ADDED, null, mode));
        }
        
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }    
    
    @Override
    public void userEnabledAutoHide(TopComponent tc, ModeImpl source, String targetSide) {
        JOptionPane.showMessageDialog(null, "userEnabledAutoHide");
        ModeImpl mode = (ModeImpl)WindowManager.getDefault().findMode(tc);
        NbWindowImpl window = getWindowForMode(mode);
        
        if( isViewMaximized(window) )
            switchMaximizedMode(window, null );

        String tcID = WindowManagerImpl.getInstance().findTopComponentID(tc);        
        if( isEditorMaximized(window) )
            setTopComponentDockedInMaximizedMode(window, tcID, false );
    
        slide( tc, source, targetSide );
    }
    
    /**
     * Slide out the given TopComponent
     */
    void slide(TopComponent tc, ModeImpl source, String targetSide) {
        NbWindowImpl window = model.getWindowForMode(source);
        
        ModeImpl targetMode = model.getSlidingMode(window, targetSide);
        if (targetMode == null) {
            targetMode = WindowManagerImpl.getInstance().createModeImpl(
                ModeImpl.getUnusedModeName(), Constants.MODE_KIND_SLIDING, false);
            model.addSlidingMode(window, targetMode, targetSide, null);
            model.setModeBounds(targetMode, new Rectangle(tc.getBounds()));
        }

        ModeImpl oldActive = getActiveMode();
        moveTopComponentIntoMode(targetMode, tc);
        ModeImpl newActive = getActiveMode();
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_TOPCOMPONENT_AUTO_HIDE_ENABLED, null, null));
        }
        
        if(oldActive != newActive) {
            WindowManagerImpl.getInstance().doFirePropertyChange(
                WindowManagerImpl.PROP_ACTIVE_MODE, oldActive, newActive);
        }
    
        // Notify new active.
        if(newActive != null) {
            // Notify registry.
            WindowManagerImpl.notifyRegistryTopComponentActivated(
                newActive.getSelectedTopComponent());
        } else {
            WindowManagerImpl.notifyRegistryTopComponentActivated(null);
        }        
    }
    
    @Override
    public void userResizedSlidingMode(ModeImpl mode, Rectangle rect) {
        model.setModeBounds(mode, new Rectangle(rect));
        //remember user's settings for the slided-in TopComponent size
        String side = model.getSlidingModeConstraints( getWindowForMode(mode), mode );
        model.setSlideInSize( side, 
                mode.getSelectedTopComponent(), 
                Constants.BOTTOM.equals( side ) || Constants.TOP.equals(side) ? rect.height : rect.width );
        if( null != mode.getSelectedTopComponent() ) {
            String tcID = WindowManagerImpl.getInstance().findTopComponentID( mode.getSelectedTopComponent() );
            model.setTopComponentMaximizedWhenSlidedIn( tcID, false );
        }
    }
    
    
    @Override
    public void userDisabledAutoHide(TopComponent tc, ModeImpl source) {
        ModeImpl mode = (ModeImpl)WindowManager.getDefault().findMode(tc);
        NbWindowImpl window = getWindowForMode(mode);

        // unmaximize if needed
        if( isViewMaximized(window) ) {
            switchMaximizedMode(window, null);
        }
        
        String tcID = WindowManagerImpl.getInstance().findTopComponentID(tc);        
        if( isEditorMaximized(window) )
            setTopComponentDockedInMaximizedMode(window, tcID, true );
        
        unSlide( tc, source );
    }
    
    /**
     * Cancel the sliding mode of the given TopComponent.
     */
    private ModeImpl unSlide(TopComponent tc, ModeImpl source) {
        String tcID = WindowManagerImpl.getInstance().findTopComponentID(tc);        
        
        ModeImpl targetMode = model.getModeTopComponentPreviousMode(source, tcID);
        int targetIndex = model.getModeTopComponentPreviousIndex(source, tcID);
        
        if ((targetMode == null) || !model.getModes().contains(targetMode)) {
            // mode to return to isn't valid anymore, try constraints
            SplitConstraint[] constraints = model.getModeTopComponentPreviousConstraints(source, tcID);
            if (constraints != null) {
                // create mode with the same constraints to dock topcomponent back into
                targetMode = WindowManagerImpl.getInstance().createModeImpl(
                        ModeImpl.getUnusedModeName(), source.getKind(), false);
                model.addMode(getWindowForMode(targetMode), targetMode, constraints); 
            }
        }

        if (targetMode == null) {
            // fallback, previous saved mode not found somehow, use default modes
            targetMode = source.getKind() == Constants.MODE_KIND_EDITOR
                    ? WindowManagerImpl.getInstance().getDefaultEditorMode(null)
                    : WindowManagerImpl.getInstance().getDefaultViewMode(null);
        }

        moveTopComponentIntoMode(targetMode, tc, targetIndex);
        
        targetMode.setMinimized( false );
        
        if (source.isEmpty()) {
            model.removeMode(source, false);
        }
        
        if(isVisible()) {
            viewRequestor.scheduleRequest(
                new ViewRequest(null, View.CHANGE_TOPCOMPONENT_AUTO_HIDE_DISABLED, null, null));
        }
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManagerImpl.PROP_ACTIVE_MODE, null, getActiveMode());
        return targetMode;
    }

    void setTopComponentMinimized( TopComponent tc, boolean minimized ) {
        if( !tc.isOpened() ) {
            return;
        }
        if( isTopComponentMinimized( tc ) == minimized ) {
            return; //nothing todo
        }
        ModeImpl mode = ( ModeImpl ) WindowManagerImpl.getInstance().findMode( tc );
        if( null == mode || mode.getState() != Constants.MODE_STATE_JOINED ) {
            return;
        }
        if( minimized ) {
            slide( tc, mode, guessSlideSide( tc ) );
        } else {
            unSlide( tc, mode );
        }
        //#207438 - make sure global minimize/dock actions get updated
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    boolean isTopComponentMinimized( TopComponent tc ) {
        if( !tc.isOpened() ) {
            return false;
        }
        ModeImpl mode = ( ModeImpl ) WindowManagerImpl.getInstance().findMode( tc );
        return null != mode && mode.getKind() == Constants.MODE_KIND_SLIDING;
    }
    
    /**
     * @return The mode where the given TopComponent had been before it was moved to sliding or separate mode.
     */
    public ModeImpl getModeTopComponentPreviousMode(String tcID, ModeImpl currentSlidingMode) {
        return  model.getModeTopComponentPreviousMode(currentSlidingMode, tcID);
    }
    
    /**
     * @return The position (tab index) of the given TopComponent before it was moved to sliding or separate mode.
     */
    public int getModeTopComponentPreviousIndex(String tcID, ModeImpl currentSlidingMode) {
        return  model.getModeTopComponentPreviousIndex(currentSlidingMode, tcID);
    }
    
    /**
     * Remember the mode and position where the given TopComponent was before moving into sliding or separate mode.
     * 
     * @param tcID TopComponent's id
     * @param currentSlidingMode The mode where the TopComponent is at the moment.
     * @param prevMode The mode where the TopComponent had been before it was moved to the sliding mode.
     * @param prevIndex Tab index of the TopComponent before it was moved to the new mode.
     */
    public void setModeTopComponentPreviousMode(String tcID, ModeImpl currentSlidingMode, ModeImpl prevMode, int prevIndex) {
        model.setModeTopComponentPreviousMode(currentSlidingMode, tcID, prevMode, prevIndex);
    }
    
    Map<String,Integer> getSlideInSizes( String side ) {
        return model.getSlideInSizes( side );
    }
    
    /**
     * Set the state of the TopComponent when the editor is maximized.
     * 
     * @param tcID TopComponent id
     * @param docked True if the TopComponent should stay docked in maximized editor mode,
     * false if it should slide out when the editor is maximized.
     */
    void setTopComponentDockedInMaximizedMode(NbWindowImpl window, String tcID, boolean docked ) {
        if( docked )
            model.getMaximizedDockingStatus(window).addDocked( tcID );
        else
            model.getMaximizedDockingStatus(window).addSlided( tcID );
    }
    
    /**
     * Get the state of the TopComponent when the editor is maximized.
     * 
     * @param tcID TopComponent id.
     * @return True if the TopComponent should stay docked in maximized editor mode,
     * false if it should slide out when the editor is maximized.
     */
    boolean isTopComponentDockedInMaximizedMode(NbWindowImpl window, String tcID ) {
        return model.getMaximizedDockingStatus(window).isDocked( tcID );
    }
    
    /**
     * Set the state of the TopComponent when no mode is maximized.
     * 
     * @param tcID TopComponent id
     * @param slided True if the TopComponent is slided in the default mode,
     * false if it is docked.
     */
    void setTopComponentSlidedInDefaultMode(NbWindowImpl window, String tcID, boolean slided ) {
        if( slided )
            model.getDefaultDockingStatus(window).addSlided( tcID );
        else
            model.getDefaultDockingStatus(window).addDocked( tcID );
    }
    
    /**
     * Get the state of the TopComponent when it is slided-in.
     * 
     * @param tcID TopComponent id. 
     * @return true if the TopComponent is maximized when slided-in.
     */
    boolean isTopComponentMaximizedWhenSlidedIn( String tcID ) {
        return model.isTopComponentMaximizedWhenSlidedIn( tcID );
    }
    
    /**
     * Set the state of the TopComponent when it is slided-in.
     * 
     * @param tcID TopComponent id. 
     * @param maximized true if the TopComponent is maximized when slided-in.
     */
    void setTopComponentMaximizedWhenSlidedIn( String tcID, boolean maximized ) {
        model.setTopComponentMaximizedWhenSlidedIn( tcID, maximized );
    }
    
    void userToggledTopComponentSlideInMaximize( String tcID ) {
        setTopComponentMaximizedWhenSlidedIn( tcID, !isTopComponentMaximizedWhenSlidedIn( tcID ) );
        if( isVisible() ) {
            TopComponent tc = WindowManagerImpl.getInstance().findTopComponent( tcID );
            ModeImpl mode = WindowManagerImpl.getInstance().findModeForOpenedID( tcID );
            NbWindowImpl window = getWindowForMode(mode);
            if( null != tc && null != mode && null != mode.getSide(window) ) {
                viewRequestor.scheduleRequest (
                    new ViewRequest(tc, View.CHANGE_MAXIMIZE_TOPCOMPONENT_SLIDE_IN, null, mode.getSide(window)));
            }
        }
    }
    
    /**
     * Get the state of the TopComponent when no mode is maximized.
     * 
     * @param tcID TopComponent id.
     * @return True if the TopComponent is slided in the default mode,
     * false if it is docked.
     */
    boolean isTopComponentSlidedInDefaultMode(NbWindowImpl window, String tcID ) {
        return model.getDefaultDockingStatus(window).isSlided( tcID );
    }
    
    boolean isEditorMaximized(NbWindowImpl window) {
        return null != model.getEditorMaximizedMode(window);
    }
    
    boolean isViewMaximized(NbWindowImpl window) {
        return null != model.getViewMaximizedMode(window);
    }
    
    /**
     * Slide-out or dock opened TopComponent according to their previous state.
     */
    private void restoreViews(NbWindowImpl window, DockingStatus viewStatus ) {
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        Set<? extends Mode> modes = model.getModesForWindow(window);
        for( Iterator<? extends Mode> i=modes.iterator(); i.hasNext(); ) {
            ModeImpl modeImpl = (ModeImpl)i.next();
            if( modeImpl.getState() == Constants.MODE_STATE_SEPARATED )
                continue;
            
            if( modeImpl.getKind() == Constants.MODE_KIND_VIEW ) {
                List<TopComponent> views = getModeOpenedTopComponents( modeImpl );
                Collections.reverse( views );
                for( Iterator<TopComponent> j=views.iterator(); j.hasNext(); ) {
                    TopComponent tc = j.next();
                    String tcID = wm.findTopComponentID( tc );
                    if( viewStatus.shouldSlide( tcID ) ) {
                        slide( tc, modeImpl, guessSlideSide( tc ) );
                    }
                }
            } else if( modeImpl.getKind() == Constants.MODE_KIND_SLIDING ) {
                List<TopComponent> views = getModeOpenedTopComponents( modeImpl );
                Collections.reverse( views );
                for( Iterator<TopComponent> j=views.iterator(); j.hasNext(); ) {
                    TopComponent tc = j.next();
                    String tcID = wm.findTopComponentID( tc );
                    if( viewStatus.shouldDock( tcID ) ) {
                        unSlide( tc, modeImpl );
                    }
                }
            }
        }
        
        //now that all views are slided/restore make sure the right views are selected in each mode
        for( Iterator<? extends Mode> i=modes.iterator(); i.hasNext(); ) {
            ModeImpl modeImpl = (ModeImpl)i.next();
            if( modeImpl.getState() == Constants.MODE_STATE_SEPARATED )
                continue;
            
            if( modeImpl.getKind() == Constants.MODE_KIND_VIEW ) {
                //make sure that the same view is selected as before
                TopComponent prevActiveTc = modeImpl.getPreviousSelectedTopComponent();
                if( null != prevActiveTc ) {
                    setModeSelectedTopComponent( modeImpl, prevActiveTc );
                }
            }
        }
    }
    
    /**
     * Slide out all non-editor TopComponents.
     */
    private void slideAllViews(NbWindowImpl window) {
        //find appropriate sliding bars first, otherwise the split hierarchy
        //will change while sliding some windows so the sliding positions would be wrong
        Map<TopComponent, String> tc2slideSide = new HashMap<TopComponent, String>(30);
//        Set<? extends Mode> modes = getModes();
        Set<ModeImpl> modes = model.getModesForWindow(window);
        for( Iterator<? extends Mode> i=modes.iterator(); i.hasNext(); ) {
            ModeImpl modeImpl = (ModeImpl)i.next();
            //NbWindowImpl window = getWindowForMode(modeImpl);
            if( modeImpl.getKind() == Constants.MODE_KIND_VIEW 
                    && modeImpl != getViewMaximizedMode(window)
                    && modeImpl.getState() != Constants.MODE_STATE_SEPARATED ) {
                List<TopComponent> views = getModeOpenedTopComponents( modeImpl );
                for( Iterator<TopComponent> j=views.iterator(); j.hasNext(); ) {
                    TopComponent tc = j.next();
                    tc2slideSide.put( tc, guessSlideSide( tc ) );
                }
            }
        }
        for( Iterator<? extends Mode> i=modes.iterator(); i.hasNext(); ) {
            ModeImpl modeImpl = (ModeImpl)i.next();
//            NbWindowImpl window = getWindowForMode(modeImpl);
            if( modeImpl.getKind() == Constants.MODE_KIND_VIEW 
                    && modeImpl != getViewMaximizedMode(window)
                    && modeImpl.getState() != Constants.MODE_STATE_SEPARATED ) {
                List<TopComponent> views = getModeOpenedTopComponents( modeImpl );
                Collections.reverse( views );
                for( Iterator<TopComponent> j=views.iterator(); j.hasNext(); ) {
                    TopComponent tc = j.next();
                    slide( tc, modeImpl, tc2slideSide.get( tc ) );
                }
            }
        }
    }
    
    // ControllerHandler <<
    ////////////////////////////
    
    /**
     * Creates a new mode and moves the given TopComponent into it.
     * @param tc 
     */
    void newTabGroup( TopComponent tc ) {
        ModeImpl currentMode = ( ModeImpl ) WindowManagerImpl.getInstance().findMode( tc );
        if( null == currentMode ) 
            return;
        ModeImpl newMode = attachModeToSide(null, currentMode, null, currentMode.getKind() );
        moveTopComponentIntoMode( newMode, tc );
        NbWindowImpl window = getWindowForMode(newMode);
        updateViewAfterDnD(window, true );
        tc.requestActive();
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    /**
     * Removes the given mode and moves all its TopComponents to some other mode.
     * @param mode 
     */
    void collapseTabGroup( ModeImpl mode ) {
        ModeImpl neighbor = findClosestNeighbor( mode );
        if( null == neighbor )
            return;
        TopComponent selTC = mode.getSelectedTopComponent();
        mergeModes( mode, neighbor, -1 );
        if( null != selTC )
            selTC.requestActive();
        NbWindowImpl window  = getWindowForMode(mode);
        updateViewAfterDnD(window, true );
        WindowManagerImpl.getInstance().doFirePropertyChange(
            WindowManager.PROP_MODES, null, null);
    }

    /**
     * Find mode that is closest to the given mode. Usually it's a neighbor at the 
     * same level in the split hierarchy.
     * @param mode
     * @return Closest neighbor to the given mode or null.
     */
    private ModeImpl findClosestNeighbor( ModeImpl mode ) {
        ArrayList<ModeImpl> modes = new ArrayList<ModeImpl>( model.getModes().size() );
        ModeImpl inSplitLeftNeighbor = null;
        ModeImpl inSplitRightNeighbor = null;
        SplitConstraint[] sc = mode.getConstraints();
        int index = sc[sc.length-1].index;
        for( ModeImpl m : model.getModes() ) {
            if( mode == m || m.getKind() != mode.getKind() || m.getState() != mode.getState() )
                continue;
            SplitConstraint[] otherSc = m.getConstraints();
            if( sameSplit( sc, otherSc) ) {
                int otherIndex = otherSc[sc.length-1].index;
                if( index < otherIndex ) {
                    if( null == inSplitLeftNeighbor || otherIndex > inSplitLeftNeighbor.getConstraints()[sc.length-1].index )
                        inSplitLeftNeighbor = m;
                } else {
                    if( null == inSplitRightNeighbor || otherIndex < inSplitRightNeighbor.getConstraints()[sc.length-1].index )
                        inSplitRightNeighbor = m;
                }
            }
            modes.add( m );
        }
        if( modes.isEmpty() )
            return null;
        if( null != inSplitLeftNeighbor )
            return inSplitLeftNeighbor;
        if( null != inSplitRightNeighbor )
            return inSplitRightNeighbor;
        Collections.sort( modes, new Comparator<ModeImpl>() {
            @Override
            public int compare( ModeImpl o1, ModeImpl o2 ) {
                SplitConstraint[] sc1 = o1.getConstraints();
                SplitConstraint[] sc2 = o2.getConstraints();
                return sc1.length - sc2.length;
            }
        });
        return modes.get( 0 );
    }
    
    /**
     * Check if the given constraints point to docking areas at the same level
     * in the split hierarchy.
     * @param sc1
     * @param sc2
     * @return 
     */
    private boolean sameSplit( SplitConstraint[] sc1, SplitConstraint[] sc2 ) {
        if( sc1.length != sc2.length )
            return false;
        for( int i=0; i<sc1.length-1; i++ ) {
            if( sc1[i].orientation != sc2[i].orientation || sc1[i].index != sc2[i].index )
                return false;
        }
        return sc1[sc1.length-1].orientation == sc2[sc2.length-1].orientation;
    }
    
    // NEW --------------------------------------------------------------------
    
    public void createNbWindowModel(NbWindowImpl window, String name, Rectangle bounds) {
        model.createNbWindowModel(window, name, bounds);
        
//        if(defaultModes) {        
            // a nbwindow name can be changed via setName - ? Should that be allowed ? Should we also have a UID?
            ModeImpl newMode = WindowManagerImpl.getInstance().createModeImpl(name + "_editor", Constants.MODE_KIND_EDITOR, Constants.MODE_STATE_JOINED, true);  
            model.addMode(window, newMode, new SplitConstraint[0]);

            ModeImpl newMode2 = WindowManagerImpl.getInstance().createModeImpl(name + "_bottomSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true); 
            model.addSlidingMode(window, newMode2, "bottom", null);

            ModeImpl newMode3 = WindowManagerImpl.getInstance().createModeImpl(name + "_rightSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);
            model.addSlidingMode(window, newMode3, "right", null);

            ModeImpl newMode4 = WindowManagerImpl.getInstance().createModeImpl(name + "_leftSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);
            model.addSlidingMode(window, newMode4, "left", null);

            ModeImpl newMode5 = WindowManagerImpl.getInstance().createModeImpl(name + "_topSlidingSide", Constants.MODE_KIND_SLIDING, Constants.MODE_STATE_JOINED, true);
            model.addSlidingMode(window, newMode5, "top", null);
//        }
        
        viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_NBWINDOW_ADDED, null, null));
    }
    
    public String getNbWindowName(NbWindowImpl window) {
        return model.getNbWindowName(window);
    }
    
    public Rectangle getNbWindowBounds(NbWindowImpl window) {
        return model.getNbWindowBounds(window);
    }
    
    public void setNbWindowBounds(NbWindowImpl window, Rectangle bounds) {
        model.setNbWindowBounds(window, bounds);
        viewRequestor.scheduleRequest(new ViewRequest(window, View.CHANGE_NBWINDOW_BOUNDS_CHANGED, null, bounds));
    }
    
    public boolean isNbWindowVisible(NbWindowImpl window) {
        return model.isNbWindowVisible(window);
    }
    
    public void setNbWindowVisible(NbWindowImpl window, boolean visible) {
        model.setNbWindowVisible(window, visible);
        viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_NBWINDOW_ADDED, null, window));
    }
    
    public void userClosedNbWindow(NbWindowImpl window) {
        destroyNbWindow(window);
    }

    @Override
    public void destroyNbWindow(NbWindowImpl window) {
        // TODO gwi destroy-window: Need to correctly cleanup resouces when the window is closed
       window.setVisible(false);
       Window rootPane = NbWindowTracker.getInstance().toWindow(window);
       ZOrderManager.getInstance().detachWindow((RootPaneContainer)rootPane);
       rootPane.setVisible(false); 
        // get all modes
        Set<ModeImpl> winModes = model.getModesForWindow(window);
        for(ModeImpl mode: winModes) {        
            model.removeMode(mode, true);
            userClosedMode(mode);
        }
        model.removeNbWindow(window);        
        viewRequestor.scheduleRequest(new ViewRequest(null, View.CHANGE_NBWINDOW_REMOVED, window, window));
    }
    
    

    @Override
    public void userResizedNbWindow(NbWindowImpl window, Rectangle bounds) {
        window.setBounds(bounds);
        //model.setAuxWindowBounds(window, bounds);
    }
    
    public Set<NbWindowImpl> getNbWindows() {
        return model.getNbWindows();
    }
    
 
    /*private*/ String getUnusedNbWindowName() {
        String base = "#";

        // don't allow base to be too long, because will act as file name too
        // PENDING Maximal length is 20.
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }

        // add numbers to the name
        String result;
        int windowNumber = 1;
        WindowManagerImpl wm = WindowManagerImpl.getInstance();
        while (model.findNbWindow(result = base + windowNumber) != null) { // NOI18N
            windowNumber++;
        }
        return result;
    }
    
    public NbWindowImpl getWindowForMode(ModeImpl mode) {
        return model.getWindowForMode(mode);
    }
}
