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


package org.netbeans.core.windows.model;



import java.awt.Frame;
import java.awt.Rectangle;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.core.windows.NbWindowImpl;
import org.netbeans.core.windows.NbWindowStructureSnapshot.NbWindowSnapshot;
import org.netbeans.core.windows.Constants;
import org.netbeans.core.windows.ModeImpl;
import org.netbeans.core.windows.ModeStructureSnapshot;
import org.netbeans.core.windows.ModeStructureSnapshot.ModeSnapshot;
import org.netbeans.core.windows.ModeStructureSnapshot.WindowModeStructureSnapshot;
import org.netbeans.core.windows.SplitConstraint;
import org.netbeans.core.windows.TopComponentGroupImpl;
import org.netbeans.core.windows.WindowManagerImpl;
import org.netbeans.core.windows.WindowSystemSnapshot;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.NbWindow;
import org.openide.windows.WindowManager;


/**
 *
 * @author  Peter Zavadsky
 */
final class DefaultModel implements Model {

    private final Map<NbWindowImpl, NbWindowModel> nbWin2model = new WeakHashMap<NbWindowImpl, NbWindowModel>(4);

    /** ModeImpl to ModeModel. */
    private final Map<ModeImpl, ModeModel> mode2model =
            new WeakHashMap<ModeImpl, ModeModel>(10);
    /** TopComponentGroup to TopComponentGroupModel. */
    private final Map<TopComponentGroupImpl, TopComponentGroupModel> group2model =
            new WeakHashMap<TopComponentGroupImpl, TopComponentGroupModel>(10);

    /** Whether the current winsys is visible on the screen.
     * 'The most important' property of all winsys. */
    private boolean visible = false;

    /** Bounds of main window when the editor area state is joined (tiled). */
    private final Rectangle mainWindowBoundsJoined = new Rectangle();
    /** Bounds of main window when the editor area state is separated. */
    private final Rectangle mainWindowBoundsSeparated = new Rectangle();
    /** Keeps initial value of separated bounds (helper non-serializable variable). */
    private final Rectangle mainWindowBoundsSeparatedHelp = new Rectangle();

    /** */
    private int mainWindowFrameStateJoined = Frame.NORMAL;
    /** */
    private int mainWindowFrameStateSeparated = Frame.NORMAL;

    //TODO gwi FIX: assumes single EditorArea, with NbWindow there are multiple!
    // Could editorAreaState be held on EditorSplitSubModel?

    /** State of editor area. 1 = joined, 2 = separated */
    private int editorAreaState = Constants.EDITOR_AREA_JOINED;
    /** Bounds of editor area. */
    private final Rectangle editorAreaBounds = new Rectangle();
    /** Keeps initial value of editor area (helper non-serializable variable). */
    private final Rectangle editorAreaBoundsHelp = new Rectangle();
    /** */
    private int editorAreaFrameState = Frame.NORMAL;
    /** Name of toolbars configuration. */
    private String toolbarConfigName = "Standard"; // NOI18N
    /** The docking status (slided-out/docked) for TopComponents in maximized editor mode */
    //private DockingStatus maximizedDockingStatus = new DockingStatus( this );
    private HashMap<NbWindowImpl, DockingStatus> maximizedDockingStatus = new HashMap<NbWindowImpl, DockingStatus>();
    /** The docking status (slided-out/docked) for TopComponents in the default mode (nothing is maximized)*/
    //private DockingStatus defaultDockingStatus = new DefaultDockingStatus( this );
    private HashMap<NbWindowImpl, DefaultDockingStatus> defaultDockingStatus = new HashMap<NbWindowImpl, DefaultDockingStatus>();
    /** TopComponents that are maximized when slided-in. */
    private Set<String> slideInMaximizedTopComponents = new HashSet<String>( 3 );

    /** Modes structure. */
    private ModesSubModel modesSubModel = new ModesSubModel(this);

    /** Set of TopComponentGroup's. */
    private final Set<TopComponentGroupImpl> topComponentGroups =
            new HashSet<TopComponentGroupImpl>(5);

    // Locks.
    /** Lock for visible property. */
    private final Object LOCK_VISIBLE = new Object();
    /** Lock for mainWindowBounds property -> joined(tiled) state. */
    private final Object LOCK_MAIN_WINDOW_BOUNDS_JOINED = new Object();
    /** Lock for mainWindowBounds property -> separated state. */
    private final Object LOCK_MAIN_WINDOW_BOUNDS_SEPARATED = new Object();
    /** */
    private final Object LOCK_MAIN_WINDOW_BOUNDS_SEPARATED_HELP = new Object();
    /** */
    private final Object LOCK_MAIN_WINDOW_FRAME_STATE_JOINED = new Object();
    /** */
    private final Object LOCK_MAIN_WINDOW_FRAME_STATE_SEPARATED = new Object();
    /** Lock for editor area state. */
    private final Object LOCK_EDITOR_AREA_STATE = new Object();
    /** */
    private final Object LOCK_EDITOR_AREA_FRAME_STATE = new Object();
    /** Lock for editor area bounds. */
    private final Object LOCK_EDITOR_AREA_BOUNDS = new Object();
    /** */
    private final Object LOCK_EDITOR_AREA_BOUNDS_HELP = new Object();
    /** Lock for toolbarConfigName property. */
    private final Object LOCK_TOOLBAR_CONFIG = new Object();
    /** Locks for modes sub model. */
    private final Object LOCK_MODES = new Object();
    /** Lock for topComponentGroups property. */
    private final Object LOCK_TOPCOMPONENT_GROUPS = new Object();

    private final Object LOCK_PROJECT_NAME = new Object();


    public DefaultModel() {
    }


    /////////////////////////////////////
    // Mutator methods >>
    /////////////////////////////////////
    /** Sets visibility status. */
    @Override
    public void setVisible(boolean visible) {
        synchronized(LOCK_VISIBLE) {
            this.visible = visible;
        }
    }

    /** Setter for mainWindowBoundsJoined property. */
    @Override
    public void setMainWindowBoundsJoined(Rectangle mainWindowBoundsJoined) {
        if(mainWindowBoundsJoined == null) {
            return;
        }

        synchronized(LOCK_MAIN_WINDOW_BOUNDS_JOINED) {
            this.mainWindowBoundsJoined.setBounds(mainWindowBoundsJoined);
        }
    }

    /** Setter for mainWindowBoundsSeparated property. */
    @Override
    public void setMainWindowBoundsSeparated(Rectangle mainWindowBoundsSeparated) {
        if(mainWindowBoundsSeparated == null) {
            return;
        }

        synchronized(LOCK_MAIN_WINDOW_BOUNDS_SEPARATED) {
            this.mainWindowBoundsSeparated.setBounds(mainWindowBoundsSeparated);
        }
    }

    /** Sets frame state of main window when editor area is in tiled(joined) state. */
    @Override
    public void setMainWindowFrameStateJoined(int frameState) {
        synchronized(LOCK_MAIN_WINDOW_FRAME_STATE_JOINED) {
            this.mainWindowFrameStateJoined = frameState;
        }
    }

    /** Sets frame state of main window when editor area is in separated state. */
    @Override
    public void setMainWindowFrameStateSeparated(int frameState) {
        synchronized(LOCK_MAIN_WINDOW_FRAME_STATE_SEPARATED) {
            this.mainWindowFrameStateSeparated = frameState;
        }
    }

    /** Setter of editorAreaBounds property. */
    @Override
    public void setEditorAreaBounds(Rectangle editorAreaBounds) {
        if(editorAreaBounds == null) {
            return;
        }

        synchronized(LOCK_EDITOR_AREA_BOUNDS) {
            this.editorAreaBounds.setBounds(editorAreaBounds);
        }
    }

    /** Setter of editorAreaState property. */
    @Override
    public void setEditorAreaState(int editorAreaState) {
        synchronized(LOCK_EDITOR_AREA_STATE) {
            this.editorAreaState = editorAreaState;
        }
    }

    /** */
    @Override
    public void setEditorAreaFrameState(int frameState) {
        synchronized(LOCK_EDITOR_AREA_FRAME_STATE) {
            this.editorAreaFrameState = frameState;
        }
    }

    /** Sets editor area constraints. */
    @Override
    public void setEditorAreaConstraints(NbWindowImpl window, SplitConstraint[] editorAreaConstraints) {
        synchronized(LOCK_MODES) {
            modesSubModel.setEditorAreaConstraints(window, editorAreaConstraints);
        }
    }

    @Override
    public void setModeConstraints(NbWindowImpl window, ModeImpl mode, SplitConstraint[] constraints) {
        synchronized(LOCK_MODES) {
            // PENDING create changeMode method?
            modesSubModel.removeMode(mode, false);
            modesSubModel.addMode(window, mode, constraints);
        }
    }


    /** Adds mode. */

    // NEW
    @Override
    public void addMode(NbWindowImpl window, ModeImpl mode, SplitConstraint[] constraints) {
        synchronized(LOCK_MODES) {
            modesSubModel.addMode(window, mode, constraints);
        }
    }


    // XXX
    @Override
    public void addModeToSide(NbWindowImpl window, ModeImpl mode, ModeImpl attachMode, String side) {
        synchronized(LOCK_MODES) {
            modesSubModel.addModeToSide(window, mode, attachMode, side);
        }
    }

    // XXX
    @Override
    public void addModeAround(NbWindowImpl window, ModeImpl mode, String side) {
        synchronized(LOCK_MODES) {
            modesSubModel.addModeAround(window, mode, side);
        }
    }

    // XXX
    @Override
    public void addModeAroundEditor(NbWindowImpl window, ModeImpl mode, String side) {
        synchronized(LOCK_MODES) {
            modesSubModel.addModeAroundEditor(window, mode, side);
        }
    }

    @Override
    public void addSlidingMode(NbWindowImpl window, ModeImpl mode, String side, Map<String, Integer> slideInSizes) {
        synchronized(LOCK_MODES) {
            modesSubModel.addModeSliding(window, mode, side, slideInSizes);
        }
    }




    /** Removes mode. */
    @Override
    public void removeMode(ModeImpl mode, boolean destructive) {
        synchronized(LOCK_MODES) {
            NbWindowImpl win = getWindowForMode(mode);
            if(mode == modesSubModel.getEditorMaximizedMode(win) && mode.isEmpty())
                WindowManagerImpl.getInstance().switchMaximizedMode(win, null);
            modesSubModel.removeMode(mode, destructive);
            if(destructive)
                mode2model.remove(mode);
        }
    }

    /** Sets active mode. */
    private Reference<ModeImpl> lastActiveMode;

    private WeakHashMap<NbWindowImpl, Reference<ModeImpl>> activeModeMap = new WeakHashMap<NbWindowImpl, Reference<ModeImpl>>();


    @Override
    public void setActiveMode(NbWindowImpl window, ModeImpl activeMode) {
        if (lastActiveMode != null && lastActiveMode.get() == activeMode) {
            return;
        } else {
            lastActiveMode = new WeakReference<ModeImpl>(activeMode);
        }
        synchronized(LOCK_MODES) {
            boolean success = modesSubModel.setActiveMode(activeMode);
            if (success) {
                activeModeMap.put(window, new WeakReference<ModeImpl>(activeMode));
                updateSlidingSelections(window, activeMode);
            }
        }
    }

    /** Sets editor mode that is currenlty maximized */
    @Override
    public void setEditorMaximizedMode(NbWindowImpl window, ModeImpl maximizedMode) {
        assert null == maximizedMode || maximizedMode.getKind() == Constants.MODE_KIND_EDITOR;
        synchronized(LOCK_MODES) {
            modesSubModel.setEditorMaximizedMode(window, maximizedMode);
        }
    }

    /** Sets view mode that is currenlty maximized */
    @Override
    public void setViewMaximizedMode(NbWindowImpl window, ModeImpl maximizedMode) {
        assert null == maximizedMode || maximizedMode.getKind() == Constants.MODE_KIND_VIEW;
        synchronized(LOCK_MODES) {
            modesSubModel.setViewMaximizedMode(window, maximizedMode);
        }
    }

    /** Setter for toolbarConfigName property. */
    @Override
    public void setToolbarConfigName(String toolbarConfigName) {
        synchronized(LOCK_TOOLBAR_CONFIG) {
            this.toolbarConfigName = toolbarConfigName;
        }
    }


    @Override
    public void addTopComponentGroup(TopComponentGroupImpl tcGroup) {
        synchronized(LOCK_TOPCOMPONENT_GROUPS) {
            topComponentGroups.add(tcGroup);
        }
    }

    @Override
    public void removeTopComponentGroup(TopComponentGroupImpl tcGroup) {
        synchronized(LOCK_TOPCOMPONENT_GROUPS) {
            topComponentGroups.remove(tcGroup);
        }
    }

    @Override
    public void reset() {
        mode2model.clear();
        group2model.clear();
        mainWindowFrameStateJoined = Frame.NORMAL;
        mainWindowFrameStateSeparated = Frame.NORMAL;
        editorAreaState = Constants.EDITOR_AREA_JOINED;
        editorAreaFrameState = Frame.NORMAL;
        toolbarConfigName = "Standard"; // NOI18N
        modesSubModel = new ModesSubModel(this);
        topComponentGroups.clear();
        maximizedDockingStatus.clear();
        defaultDockingStatus.clear();
        slideInMaximizedTopComponents.clear();
    }

    /////////////////////////////////////
    // Mutator methods <<
    /////////////////////////////////////

    /////////////////////////////////////
    // Accessor methods >>
    /////////////////////////////////////
    /** Gets visibility status. */
    @Override
    public boolean isVisible() {
        synchronized(LOCK_VISIBLE) {
            return this.visible;
        }
    }

    /** Getter for mainWindowBoundsJoined property. */
    @Override
    public Rectangle getMainWindowBoundsJoined() {
        synchronized(LOCK_MAIN_WINDOW_BOUNDS_JOINED) {
            return (Rectangle)mainWindowBoundsJoined.clone();
        }
    }

    /** Getter for mainWindowBoundsSeparated property. */
    @Override
    public Rectangle getMainWindowBoundsSeparated() {
        synchronized(LOCK_MAIN_WINDOW_BOUNDS_SEPARATED) {
            return (Rectangle)mainWindowBoundsSeparated.clone();
        }
    }

    @Override
    public Rectangle getMainWindowBoundsSeparatedHelp() {
        synchronized(LOCK_MAIN_WINDOW_BOUNDS_SEPARATED_HELP) {
            return (Rectangle)mainWindowBoundsSeparatedHelp.clone();
        }
    }

    /** Gets frame state of main window when editor area is in tiled(joined) state. */
    @Override
    public int getMainWindowFrameStateJoined() {
        synchronized(LOCK_MAIN_WINDOW_FRAME_STATE_JOINED) {
            return mainWindowFrameStateJoined;
        }
    }

    /** Gets frame state of main window when editor area is in separated state. */
    @Override
    public int getMainWindowFrameStateSeparated() {
        synchronized(LOCK_MAIN_WINDOW_FRAME_STATE_SEPARATED) {
            return mainWindowFrameStateSeparated;
        }
    }

    /** Getter of editorAreaState property. */
    @Override
    public int getEditorAreaState() {
        synchronized(LOCK_EDITOR_AREA_STATE) {
            return this.editorAreaState;
        }
    }

    /** */
    @Override
    public int getEditorAreaFrameState() {
        synchronized(LOCK_EDITOR_AREA_FRAME_STATE) {
            return this.editorAreaFrameState;
        }
    }

    /** Getter of editorAreaBounds property. */
    @Override
    public Rectangle getEditorAreaBounds() {
        synchronized(LOCK_EDITOR_AREA_BOUNDS) {
            return (Rectangle)this.editorAreaBounds.clone();
        }
    }

    @Override
    public Rectangle getEditorAreaBoundsHelp() {
        synchronized(LOCK_EDITOR_AREA_BOUNDS_HELP) {
            return (Rectangle)this.editorAreaBoundsHelp.clone();
        }
    }

    /** Gets editor area constraints. */
    @Override
    public SplitConstraint[] getEditorAreaConstraints(NbWindowImpl window) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getEditorAreaConstraints(window);
        }
    }

    /** Gets set of modes. */
    @Override
    public Set<ModeImpl> getModes() {
        synchronized(LOCK_MODES) {
            return modesSubModel.getModes();
        }
    }

    @Override
    public SplitConstraint[] getModeConstraints(NbWindowImpl window, ModeImpl mode) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getModeConstraints(window, mode);
        }
    }

    @Override
    public SplitConstraint[] getModelElementConstraints(NbWindowImpl window, ModelElement element) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getModelElementConstraints(window, element);
        }
    }

    @Override
    public String getSlidingModeConstraints(NbWindowImpl window, ModeImpl mode) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getSlidingModeConstraints(window, mode);
        }
    }

    @Override
    public ModeImpl getSlidingMode(NbWindowImpl window, String side) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getSlidingMode(window, side);
        }
    }

    /** Gets active mode. */
    @Override
    public ModeImpl getActiveMode() {
        synchronized(LOCK_MODES) {
            return modesSubModel.getActiveMode();
        }
    }

    @Override
    public ModeImpl getActiveMode(NbWindowImpl window) {
        synchronized(LOCK_MODES) {
            Reference<ModeImpl> mRef = activeModeMap.get(window);
            if(mRef != null) {
                return mRef.get();
            }
            return null;
        }
    }




    /** Gets last active editor mode. */
    @Override
    public ModeImpl getLastActiveEditorMode() {
        synchronized(LOCK_MODES) {
            return modesSubModel.getLastActiveEditorMode();
        }
    }

    /**
     * @return The docking status (docked/slided) of TopComponents before the window system
     * switched to maximized mode.
     */
    @Override
    public DockingStatus getDefaultDockingStatus(NbWindowImpl window) {
        DockingStatus status = defaultDockingStatus.get(window);
        if(status == null) {
            status = new DefaultDockingStatus(this);
            defaultDockingStatus.put(window, (DefaultDockingStatus)status);
        }
        return status;
//        return defaultDockingStatus;
    }

    /**
     * @return The docking status (docked/slided) of TopComponents in maximized editor mode.
     */
    @Override
    public DockingStatus getMaximizedDockingStatus(NbWindowImpl window) {
        DockingStatus status = maximizedDockingStatus.get(window);
        if(status == null) {
            status = new DockingStatus(this);
            maximizedDockingStatus.put(window, status);
        }
        return status;
//        return maximizedDockingStatus;
    }

    /** Gets editor maximized mode. */
    @Override
    public ModeImpl getEditorMaximizedMode(NbWindowImpl window) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getEditorMaximizedMode(window);
        }
    }

    /** Gets view maximized mode. */
    @Override
    public ModeImpl getViewMaximizedMode(NbWindowImpl window) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getViewMaximizedMode(window);
        }
    }

    /**
     * Find the side (LEFT/RIGHT/BOTTOM) where the TopComponent from the given
     * mode should slide to.
     *
     * @param mode Mode
     * @return The slide side for TopComponents from the given mode.
     */
    @Override
    public String getSlideSideForMode(NbWindowImpl window, ModeImpl mode ) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getSlideSideForMode( window, mode ); // TODO gwi-slide
        }
    }

    /** Getter for toolbarConfigName property. */
    @Override
    public String getToolbarConfigName() {
        synchronized(LOCK_TOOLBAR_CONFIG) {
            return this.toolbarConfigName;
        }
    }

    /**
     * Gets the sizes (width or height) of TopComponents in the given sliding
     * side, the key in the Map is TopComponent's ID
     */
    @Override
    public Map<String,Integer> getSlideInSizes(String side) {
        synchronized(LOCK_MODES) {
            return modesSubModel.getSlideInSizes( side );
        }
    }

    /** Set the size (width or height of the given TopComponent when it is slided in */
    @Override
    public void setSlideInSize(String side, TopComponent tc, int size) {
        synchronized(LOCK_MODES) {
            modesSubModel.setSlideInSize(side, tc, size);
        }
    }

    /**
     * @return True if the given TopComponent is maximized when it is slided-in.
     */
    @Override
    public boolean isTopComponentMaximizedWhenSlidedIn( String tcid ) {
        return null != tcid && slideInMaximizedTopComponents.contains( tcid );
    }

    /**
     * Set whether the given TopComponent is maximized when it is slided-in.
     */
    @Override
    public void setTopComponentMaximizedWhenSlidedIn( String tcid, boolean maximized ) {
        if( null != tcid ) {
            if( maximized )
                slideInMaximizedTopComponents.add( tcid );
            else
                slideInMaximizedTopComponents.remove( tcid );
        }
    }

    /////////////////////////////////////
    // Accessor methods <<
    /////////////////////////////////////


    ///////////////////
    // Mode specific >>
    @Override
    public void createModeModel(ModeImpl mode, String name, int state, int kind, boolean permanent) {
        synchronized(mode2model) {
            ModeModel mm;
            mm = new DefaultModeModel(name, state, kind, permanent);
            mode2model.put(mode, mm);
        }
    }

    private ModeModel getModelForMode(ModeImpl mode) {
        synchronized(mode2model) {
            return (ModeModel)mode2model.get(mode);
        }
    }

    // Mutators
    /** Sets state. */
    @Override
    public void setModeState(ModeImpl mode, int state) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setState(state);
        }
    }

    /** Sets bounds. */
    @Override
    public void setModeBounds(ModeImpl mode, Rectangle bounds) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setBounds(bounds);
        }
    }

    /** Sets frame state. */
    @Override
    public void setModeFrameState(ModeImpl mode, int frameState) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setFrameState(frameState);
        }
    }

    /** Sets seleted TopComponent. */
    @Override
    public void setModeSelectedTopComponent(ModeImpl mode, TopComponent selected) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setSelectedTopComponent(selected);

            // make sure window is visible
            NbWindowImpl window = WindowManagerImpl.getInstance().getWindowForMode(mode);
            if(window != null && !window.isVisible())
                window.setVisible(true);
        }
    }

    /** Remember which top component was the selected one before switching to/from maximized mode */
    @Override
    public void setModePreviousSelectedTopComponentID(ModeImpl mode, String prevSelectedId) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setPreviousSelectedTopComponentID(prevSelectedId);
        }
    }

    /** Adds opened TopComponent. */
    @Override
    public void addModeOpenedTopComponent(ModeImpl mode, TopComponent tc) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.addOpenedTopComponent(tc);
        }
    }

    /** Inserts opened TopComponent. */
    @Override
    public void insertModeOpenedTopComponent(ModeImpl mode, TopComponent tc, int index) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.insertOpenedTopComponent(tc, index);
        }
    }

    /** Adds closed TopComponent. */
    @Override
    public void addModeClosedTopComponent(ModeImpl mode, TopComponent tc) {
//        System.out.println("DefaultModel:addModeClosedTopComponent");
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.addClosedTopComponent(tc);
        }
    }

//    private void checkForWindowClosure(ModeImpl mode) {
//        List<TopComponent> openTopComponents = mode.getOpenedTopComponents();
//        System.out.println("DefaultModel:checkWindowClosure,openCount=" + openTopComponents.size());
//        if(Boolean.getBoolean("netbeans.winsys.enhanced")) {
//            if(openTopComponents.size() == 0) {
//                System.out.println("Mode is empty but isEmpty=" + mode.isEmpty());
//                // does the mode belong to a topwindow?
//                NbWindow window = WindowManagerImpl.getInstance().getWindowForMode(mode);
//                if(window != null) {
//                    System.out.println("EMPTY MODE BELONGS TO WINDOW: " + window);
//
//                    // need to check for main window
//                    Set<ModeImpl> modes = getModesForWindow((NbWindowImpl)window);
//                    boolean isEmpty = true;
//                    for(ModeImpl m: modes) {
//                        if(!m.getOpenedTopComponents().isEmpty()) {
//                            isEmpty = false;
//                            break;
//                        }
//                    }
//                    if(isEmpty && !("NbMainWindow".equals(window.getName()))) {
//                        // does this really close the window?
//                        window.setVisible(false);
//                        WindowManagerImpl.getInstance().destroyNbWindow((NbWindowImpl)window);
//                    }
//                }
//            }
//        }
//    }

    // XXX
    @Override
    public void addModeUnloadedTopComponent(ModeImpl mode, String tcID, int index) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.addUnloadedTopComponent(tcID, index);
        }
    }

    // XXX
    @Override
    public void setModeUnloadedSelectedTopComponent(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setUnloadedSelectedTopComponent(tcID);
        }
    }

    @Override
    public void setModeUnloadedPreviousSelectedTopComponent(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setUnloadedPreviousSelectedTopComponent(tcID);
        }
    }

    /** */
    @Override
    public void removeModeTopComponent(ModeImpl mode, TopComponent tc, TopComponent recentTc) {
//        System.out.println("DefaultModel:removeModeTopComponent");
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.removeTopComponent(tc, recentTc);
        }
    }

    // XXX
    @Override
    public void removeModeClosedTopComponentID(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.removeClosedTopComponentID(tcID);
        }
    }

    /**
     * @param mode - sliding mode
     */

    @Override
    public void setModeTopComponentPreviousConstraints(ModeImpl mode, String tcID, SplitConstraint[] constraints) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setTopComponentPreviousConstraints(tcID, constraints);
        }
    }

    /**
     * @param mode - sliding mode
     * @param previousMode - the original mode.
     * @param prevIndex - the tab index in the original mode
     */
    @Override
    public void setModeTopComponentPreviousMode(ModeImpl mode, String tcID, ModeImpl previousMode, int prevIndex) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setTopComponentPreviousMode(tcID, previousMode, prevIndex);
        }
    }

    // Accessors
    /** Gets programatic name of mode. */
    @Override
    public String getModeName(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getName();
        } else {
            return null;
        }
    }

    @Override
    public final void setModeName(ModeImpl mode, String name) {
        ModeModel modeModel = getModelForMode(mode);
        modeModel.setName(name);
    }

    /** Gets bounds. */
    @Override
    public Rectangle getModeBounds(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getBounds();
        } else {
            return null;
        }
    }

    @Override
    public Rectangle getModeBoundsSeparatedHelp(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getBoundsSeparatedHelp();
        } else {
            return null;
        }
    }

    /** Gets state. */
    @Override
    public int getModeState(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getState();
        } else {
            return -1;
        }
    }

    /** Gets kind. */
    @Override
    public int getModeKind(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getKind();
        } else {
            return -1;
        }
    }

    /** Gets side. */
    @Override
    public String getModeSide(NbWindowImpl window, ModeImpl mode) {
        String side = modesSubModel.getSlidingModeConstraints(window, mode);
        return side;
    }

    /** Gets frame state. */
    @Override
    public int getModeFrameState(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getFrameState();
        } else {
            return -1;
        }
    }

    /** Gets used defined. */
    @Override
    public boolean isModePermanent(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            boolean result = modeModel.isPermanent();
            if (!result) {
                for (TopComponent tc : mode.getTopComponents()) {
                    result |= tc.getClass().getAnnotation(RetainLocation.class)
                            != null;
                    if (result) {
                        break;
                    }
                }
            }
            return result;
        } else {
            return false;
        }
    }

    @Override
    public void makeModePermanent( ModeImpl mode ) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null)
            modeModel.makePermanent();
    }

    /** Indicates whether the mode is empty. */
    @Override
    public boolean isModeEmpty(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
//            System.out.println("DefaultModel:isModeEmpty modeModel=" + modeModel);
            return modeModel.isEmpty();
        } else {
            return false;
        }
    }

    /** Indicates whether the mode contains the TopComponent. */
    @Override
    public boolean containsModeTopComponent(ModeImpl mode, TopComponent tc) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.containsTopComponent(tc);
        } else {
            return false;
        }
    }

    /** Gets selected TopComponent. */
    @Override
    public TopComponent getModeSelectedTopComponent(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getSelectedTopComponent();
        } else {
            return null;
        }
    }

    /** Get the ID of top component that had been the selected one before switching to/from maximzied mode */
    @Override
    public String getModePreviousSelectedTopComponentID(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getPreviousSelectedTopComponentID();
        } else {
            return null;
        }
    }

    /** Gets list of top components. */
    @Override
    public List<TopComponent> getModeTopComponents(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getTopComponents();
        } else {
            return Collections.emptyList();
        }
    }

    /** Gets list of top components. */
    @Override
    public List<TopComponent> getModeOpenedTopComponents(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getOpenedTopComponents();
        } else {
            return Collections.emptyList();
        }
    }

    // XXX
    @Override
    public List<String> getModeOpenedTopComponentsIDs(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getOpenedTopComponentsIDs();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public int getModeOpenedTopComponentTabPosition(ModeImpl mode, TopComponent tc) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getOpenedTopComponentTabPosition(tc);
        } else {
            return -1;
        }
    }

    @Override
    public List<String> getModeClosedTopComponentsIDs(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getClosedTopComponentsIDs();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getModeTopComponentsIDs(ModeImpl mode) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            return modeModel.getTopComponentsIDs();
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public SplitConstraint[] getModeTopComponentPreviousConstraints(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        return modeModel == null ? null : modeModel.getTopComponentPreviousConstraints(tcID);
    }

    @Override
    public ModeImpl getModeTopComponentPreviousMode(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        return modeModel == null ? null : modeModel.getTopComponentPreviousMode(tcID);
    }

    /** Gets the tab index of the given top component before it was moved to sliding/separate mode */
    @Override
    public int getModeTopComponentPreviousIndex(ModeImpl mode, String tcID) {
        ModeModel modeModel = getModelForMode(mode);
        return modeModel == null ? null : modeModel.getTopComponentPreviousIndex(tcID);
    }


    @Override
    public boolean isModeMinimized( ModeImpl mode ) {
        ModeModel modeModel = getModelForMode(mode);
        return modeModel == null ? false : modeModel.isMinimized();
    }

    @Override
    public void setModeMinimized( ModeImpl mode, boolean minimized ) {
        ModeModel modeModel = getModelForMode(mode);
        if( null != modeModel )
            modeModel.setMinimized( minimized );
    }

    @Override
    public Collection<String> getModeOtherNames( ModeImpl mode ) {
        ModeModel modeModel = getModelForMode(mode);
        if( modeModel != null )
            return modeModel.getOtherNames();
        return Collections.emptyList();
    }

    @Override
    public void addModeOtherName( ModeImpl mode, String otherModeName ) {
        ModeModel modeModel = getModelForMode(mode);
        if( null != modeModel )
            modeModel.addOtherName( otherModeName );
    }

    @Override
    public void dockMode( ModeImpl prevMode, ModeImpl floatingMode ) {
        ModeModel modeModel = getModelForMode(floatingMode);
        if( null != modeModel ) {
            synchronized(LOCK_MODES) {
                modesSubModel.dock( prevMode, floatingMode );
            }
            modeModel.setState( Constants.MODE_STATE_JOINED );
        }
    }

    // End of mode specific.


    ////////////////////////////////////
    // TopComponentGroup specific >>
    @Override
    public void createGroupModel(TopComponentGroupImpl tcGroup, String name, boolean opened) {
        synchronized(group2model) {
            TopComponentGroupModel tcgm = new DefaultTopComponentGroupModel(name, opened);
            group2model.put(tcGroup, tcgm);
        }
    }

    private TopComponentGroupModel getModelForGroup(TopComponentGroupImpl tcGroup) {
        synchronized(group2model) {
            return (TopComponentGroupModel)group2model.get(tcGroup);
        }
    }


    @Override
    public Set<TopComponentGroupImpl> getTopComponentGroups() {
        synchronized(LOCK_TOPCOMPONENT_GROUPS) {
            return new HashSet<TopComponentGroupImpl>(topComponentGroups);
        }
    }

    /** Gets programmatic name of mode. */
    @Override
    public String getGroupName(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getName();
        } else {
            return null;
        }
    }

    @Override
    public void openGroup(TopComponentGroupImpl tcGroup,
            Collection<TopComponent> openedTopComponents,
            Collection<TopComponent> openedBeforeTopComponents) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            groupModel.open(openedTopComponents, openedBeforeTopComponents);
        }
    }

    @Override
    public void closeGroup(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            groupModel.close();
        }
    }

    @Override
    public boolean isGroupOpened(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.isOpened();
        } else {
            return false;
        }
    }

    @Override
    public Set<TopComponent> getGroupTopComponents(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getTopComponents();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<TopComponent> getGroupOpenedTopComponents(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getOpenedTopComponents();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<TopComponent> getGroupOpenedBeforeTopComponents(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getOpenedBeforeTopComponents();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<TopComponent> getGroupOpeningTopComponents(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getOpeningTopComponents();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<TopComponent> getGroupClosingTopComponents(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getClosingTopComponents();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean addGroupUnloadedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.addUnloadedTopComponent(tcID);
        } else {
            return false;
        }
    }

    @Override
    public boolean removeGroupUnloadedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.removeUnloadedTopComponent(tcID);
        } else {
            return false;
        }
    }

    @Override
    public boolean addGroupOpeningTopComponent(TopComponentGroupImpl tcGroup, TopComponent tc) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.addOpeningTopComponent(tc);
        } else {
            return false;
        }
    }

    @Override
    public boolean removeGroupOpeningTopComponent(TopComponentGroupImpl tcGroup, TopComponent tc) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.removeOpeningTopComponent(tc);
        } else {
            return false;
        }
    }

    @Override
    public boolean addGroupUnloadedOpeningTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.addUnloadedOpeningTopComponent(tcID);
        } else {
            return false;
        }
    }

    @Override
    public boolean removeGroupUnloadedOpeningTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.removeUnloadedOpeningTopComponent(tcID);
        } else {
            return false;
        }
    }

    @Override
    public boolean addGroupUnloadedClosingTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.addUnloadedClosingTopComponent(tcID);
        } else {
            return false;
        }
    }
    @Override
    public boolean removeGroupUnloadedClosingTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.removeUnloadedClosingTopComponent(tcID);
        } else {
            return false;
        }
    }
    // XXX
    @Override
    public boolean addGroupUnloadedOpenedTopComponent(TopComponentGroupImpl tcGroup, String tcID) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.addUnloadedOpenedTopComponent(tcID);
        } else {
            return false;
        }
    }

    // XXX>>
    @Override
    public Set<String> getGroupTopComponentsIDs(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getTopComponentsIDs();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<String> getGroupOpeningSetIDs(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getOpeningSetIDs();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<String> getGroupClosingSetIDs(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getClosingSetIDs();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public Set<String> getGroupOpenedTopComponentsIDs(TopComponentGroupImpl tcGroup) {
        TopComponentGroupModel groupModel = getModelForGroup(tcGroup);
        if(groupModel != null) {
            return groupModel.getOpenedTopComponentsIDs();
        } else {
            return Collections.emptySet();
        }
    }
    // XXX<<
    // TopComponentGroup specific <<
    ////////////////////////////////////

    @Override
    public void setMainWindowBoundsUserSeparatedHelp(Rectangle bounds) {
        if(bounds == null) {
            return;
        }

        synchronized(LOCK_MAIN_WINDOW_BOUNDS_SEPARATED_HELP) {
            this.mainWindowBoundsSeparatedHelp.setBounds(bounds);
        }
    }

    @Override
    public void setEditorAreaBoundsUserHelp(Rectangle bounds) {
        if(bounds == null) {
            return;
        }

        synchronized(LOCK_EDITOR_AREA_BOUNDS_HELP) {
            this.editorAreaBoundsHelp.setBounds(bounds);
        }
    }

    @Override
    public void setModeBoundsSeparatedHelp(ModeImpl mode, Rectangle bounds) {
        ModeModel modeModel = getModelForMode(mode);
        if(modeModel != null) {
            modeModel.setBoundsSeparatedHelp(bounds);
        }
    }

    @Override
    public void setSplitWeights(NbWindowImpl window, ModelElement[] snapshots, double[] splitWeights ) {
        synchronized(LOCK_MODES) {
            modesSubModel.setSplitWeights(window, snapshots, splitWeights);
        }
    }

    // Controller updates <<
    /////////////////////////


    //TODO gwi: createWindowSystemSnapshot
    @Override
    public WindowSystemSnapshot createWindowSystemSnapshot() {
        WindowSystemSnapshot wsms = new WindowSystemSnapshot();

        // PENDING
        ModeStructureSnapshot mss = createModeStructureSnapshot();
        wsms.setModeStructureSnapshot(mss);

        ModeImpl activeMode = getActiveMode();
        wsms.setActiveModeSnapshot(activeMode == null ? null : mss.findModeSnapshot(activeMode.getName()));


        HashMap<NbWindowSnapshot, ModeSnapshot> maxMap = new HashMap<NbWindowSnapshot, ModeSnapshot>();
        for(NbWindowImpl win: nbWin2model.keySet()) {
            ModeImpl maximizedMode = null != getViewMaximizedMode(win) ? getViewMaximizedMode(win) : null;
            maxMap.put(new NbWindowSnapshot(win), maximizedMode == null ? null : mss.findModeSnapshot(maximizedMode.getName()));
        }
        wsms.setMaximizedModeSnapshot(maxMap);


        wsms.setMainWindowBoundsJoined(getMainWindowBoundsJoined());
        wsms.setMainWindowBoundsSeparated(getMainWindowBoundsSeparated());
        wsms.setEditorAreaBounds(getEditorAreaBounds());
        wsms.setEditorAreaState(getEditorAreaState());
        wsms.setEditorAreaFrameState(getEditorAreaFrameState());
        wsms.setMainWindowFrameStateJoined(getMainWindowFrameStateJoined());
        wsms.setMainWindowFrameStateSeparated(getMainWindowFrameStateSeparated());
        wsms.setToolbarConfigurationName(getToolbarConfigName());
        return wsms;
    }

    //TODO gwi: modified createModeStructureSnapshot
    /** Creates modes snapshot.. */
    private ModeStructureSnapshot createModeStructureSnapshot() {
        Map<NbWindowSnapshot, WindowModeStructureSnapshot> windowModeStructureSnapshots;
        Set<ModeStructureSnapshot.ModeSnapshot> separateModes;

        synchronized(LOCK_MODES) {
            windowModeStructureSnapshots = modesSubModel.createWindowModeStructureSnapshots();
            separateModes = modesSubModel.createSeparateModeSnapshots(null);
        }

        ModeStructureSnapshot ms =  new ModeStructureSnapshot(windowModeStructureSnapshots, separateModes);
        return ms;
    }
    ///////////////////////////////////////////////////

    /** Checks whether the mode isn't null.  */
    private static boolean validateAddingMode(ModeImpl mode) {
        if(mode == null) {
            Logger.getLogger(DefaultModel.class.getName()).log(Level.WARNING, null,
                              new java.lang.NullPointerException("Not allowed null mode")); // NOI18N
            return false;
        }

        return true;
    }

    /** Keeps selected components of sliding modes in sync with given current
     * active mode. Sliding mode can have non-null selection (=slide) only if
     * it is active mode as well
     */
    private void updateSlidingSelections (NbWindowImpl window, ModeImpl curActive) {
        Set slidingModes = modesSubModel.getSlidingModes(window);
        ModeImpl curSliding = null;
        for (Iterator iter = slidingModes.iterator(); iter.hasNext(); ) {
            curSliding = (ModeImpl)iter.next();
            if (!curSliding.equals(curActive)) {
                setModeSelectedTopComponent(curSliding, null);
            }
        }
    }

    /**
     * A special subclass of DockingStatus for default mode when no TopComponent is maximized.
     */
    private static class DefaultDockingStatus extends DockingStatus {
        public DefaultDockingStatus( Model model ) {
            super( model );
        }

        /**
         * When switching back to default mode, only slide those TopComponents
         * there were slided-out before.
         */
    @Override
        public boolean shouldSlide( String tcID ) {
            return null != tcID && slided.contains( tcID );
        }

        /**
         * In default mode all TopComponents are docked by default.
         */
    @Override
        public boolean shouldDock( String tcID ) {
            return null != tcID && (docked.contains( tcID ) || (!docked.contains( tcID ) && !slided.contains( tcID )));
        }

    @Override
        public void mark() {
            super.mark();
            Set<ModeImpl> modes = model.getModes();
            for( Iterator<ModeImpl> i=modes.iterator(); i.hasNext(); ) {
                ModeImpl modeImpl = i.next();
                if( modeImpl.getState() != Constants.MODE_KIND_VIEW )
                    continue;

                //also remember which top component is the selected one
                String selTcId = null;
                TopComponent selTc = modeImpl.getSelectedTopComponent();
                if( null != selTc )
                    selTcId = WindowManagerImpl.getInstance().findTopComponentID(selTc);
                modeImpl.setPreviousSelectedTopComponentID( selTcId );
            }
        }
    }

    // NEW ---------------------------------------------------------------

    @Override
    public void createNbWindowModel(NbWindowImpl window, String name, Rectangle bounds) {
        synchronized(nbWin2model) {
            NbWindowModel wm = new DefaultNbWindowModel(name, bounds);
            nbWin2model.put(window, wm);

            // Create an editor model
            modesSubModel.createNbWindowEditorSplitSubModel(window);
        }
    }

    @Override
    public Rectangle getNbWindowBounds(NbWindow window) {
        synchronized(nbWin2model) {
            return nbWin2model.get(window).getBounds();
        }
    }

    @Override
    public String getNbWindowName(NbWindow window) {
        synchronized(nbWin2model) {
            NbWindowModel win = nbWin2model.get(window);
            if(win == null)  // TODO is this necessary?
                return null;
            return win.getName();
        }
    }

    @Override
    public boolean isNbWindowVisible(NbWindowImpl window) {
        synchronized(nbWin2model) {
            return nbWin2model.get(window).isVisible();
        }
    }



    @Override
    public void setNbWindowVisible(NbWindowImpl window, boolean visible) {
        synchronized(nbWin2model) {
            if(nbWin2model.get(window) != null)
                nbWin2model.get(window).setVisible(visible);
        }
    }

    @Override
    public void removeNbWindow(NbWindowImpl window) {
        synchronized(nbWin2model) {
            nbWin2model.remove(window);
            modesSubModel.removeNbWindow(window);
        }
    }

    @Override
    public void setNbWindowBounds(NbWindowImpl window, Rectangle bounds) {
        synchronized(nbWin2model) {
            nbWin2model.get(window).setBounds(bounds);
        }
    }

    @Override
    public Set<NbWindowImpl> getNbWindows() {
        synchronized(nbWin2model) {
            return nbWin2model.keySet();
        }
    }

    public NbWindowImpl findNbWindow(String name) {
        synchronized(nbWin2model) {
            for(NbWindowImpl win: nbWin2model.keySet()) {
                if(win.getName().equals(name)) {
                    return win;
                }
            }
            return null;
        }
    }

    @Override
    public Set<ModeImpl> getModesForWindow(NbWindowImpl window) {
        return modesSubModel.getModesForWindow(window);
    }

    @Override
    public NbWindowImpl getWindowForMode(ModeImpl mode) {
        return modesSubModel.getWindowForMode(mode);
    }
}

