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


package org.netbeans.core.windows.view;


import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import org.netbeans.core.windows.NbWindowImpl;
import org.netbeans.core.windows.NbWindowTracker;
import org.netbeans.core.windows.Constants;
import org.netbeans.core.windows.Debug;
import org.netbeans.core.windows.ModeImpl;
import org.netbeans.core.windows.WindowManagerImpl;
import org.netbeans.core.windows.view.dnd.TopComponentDraggable;
import org.netbeans.core.windows.view.dnd.WindowDnDManager;
import org.netbeans.core.windows.view.dnd.ZOrderManager;
import org.netbeans.core.windows.view.ui.NbWindowDialog;
import org.netbeans.core.windows.view.ui.NbWindowFrame;
import org.netbeans.core.windows.view.ui.DesktopImpl;
import org.netbeans.core.windows.view.ui.EditorAreaFrame;
import org.netbeans.core.windows.view.ui.MainWindow;
import org.netbeans.core.windows.view.ui.slides.SlideOperation;
import org.openide.windows.TopComponent;
import org.openide.windows.NbWindow;
import org.netbeans.core.windows.view.ui.NbWindowComponent;

/**
 * Class which manages GUI components.
 *
 * @author Peter Zavadsky
 */
final class ViewHierarchy {

    // this probably shouldn't use String - what can we use?
    private Map<NbWindowImpl, NbWindowComponent> win2Frame = new HashMap<NbWindowImpl, NbWindowComponent>();
    private final NbWindowListener nbWindowListener;
//    private Map<AuxWindowSnapshot, ViewElement> auxCurrentSplitRoot = new HashMap<AuxWindowSnapshot, ViewElement>();

    /**
     * Observes user changes to view hierarchy.
     */
    private final Controller controller;

    private final WindowDnDManager windowDnDManager;

    /**
     * desktop component maintainer
     */
    private Map<NbWindowImpl, DesktopImpl> desktopMap = new HashMap<NbWindowImpl, DesktopImpl>();  
    private Map<NbWindowImpl, ViewElement> currentSplitRootMap = new HashMap<NbWindowImpl, ViewElement>(); 
    private Map<NbWindowImpl, ViewElement> fakeSplitMap = new HashMap<NbWindowImpl, ViewElement>();

    /**
     * Map of separate mode views (view <-> accessor).
     */
    private final Map<ModeView, ModeAccessor> separateModeViews
            = new HashMap<ModeView, ModeAccessor>(10);
    /**
     * Map of sliding mode views (view <-> accessor)
     */
    private final Map<NbWindowImpl, Map<SlidingView, SlidingAccessor>> slidingModeViewsMap = new HashMap<NbWindowImpl, Map<SlidingView, SlidingAccessor>>(10);

    /**
     * Component in which is editor area, when the editor state is separated.
     */
    private EditorAreaFrame editorAreaFrame;

    /**
     * Active mode view.
     */
    private ModeView activeModeView; // 1 per window
    /**
     * Maximized mode view.
     */
    private ModeView maximizedModeView;  // 1 per window

    /**
     * Last non sliding mode view that were active in the past, or null if no
     * such exists
     */
    private WeakReference<ModeView> lastNonSlidingActive;  // 1 per window

    /**
     *
     */
    private final Map<ElementAccessor, ViewElement> accessor2view
            = new HashMap<ElementAccessor, ViewElement>(10);
    /**
     *
     */
    private final Map<ViewElement, ElementAccessor> view2accessor
            = new HashMap<ViewElement, ElementAccessor>(10);

    private MainWindow mainWindow;

    private final MainWindowListener mainWindowListener;

    /**
     * Creates a new instance of ViewHierarchy.
     */
    public ViewHierarchy(Controller controller, WindowDnDManager windowDnDManager) {
        this.controller = controller;
        this.windowDnDManager = windowDnDManager;

        this.mainWindowListener = new MainWindowListener(controller, this);
        this.nbWindowListener = new NbWindowListener(controller, this);
    }

    public boolean isDragInProgress() {
        return windowDnDManager.isDragging();
    }

    public MainWindow getMainWindow() {
        if (mainWindow == null) {
            JFrame mainFrame = null;
            for (Frame f : Frame.getFrames()) {
                if (f instanceof JFrame) {
                    JFrame frame = (JFrame) f;
                    if ("NbMainWindow".equals(frame.getName())) { //NOI18N
                        mainFrame = frame;
                        break;
                    }
                }
            }

            if (null == mainFrame) {
                mainFrame = new JFrame();
                mainFrame.setName("NbMainWindow"); //NOI18N
                if (!Constants.AUTO_FOCUS) {
                    mainFrame.setAutoRequestFocus(false);
                }
            }
            if ("Aqua".equals(UIManager.getLookAndFeel().getID())
                    && null == System.getProperty("apple.awt.brushMetalLook")) {//NOI18N 
                JRootPane root = mainFrame.getRootPane();
                if (null != root) {
                    root.putClientProperty("apple.awt.brushMetalLook", Boolean.TRUE); //NOI18N
                }
            }
            Logger.getLogger(MainWindow.class.getName()).log(Level.FINE, "Installing MainWindow into " + mainFrame); //NOI18N
            mainWindow = MainWindow.install(mainFrame);
        }
        return mainWindow;
    }

    public void installMainWindowListeners() {
        mainWindow.getFrame().addComponentListener(mainWindowListener);
        mainWindow.getFrame().addWindowStateListener(mainWindowListener);
    }

    public void uninstallMainWindowListeners() {
        mainWindow.getFrame().removeComponentListener(mainWindowListener);
        mainWindow.getFrame().removeWindowStateListener(mainWindowListener);
    }

    //TODO gwi: updateViewHierarchy modified to embed AuxWindow roots
    /**
     * Updates the view hierarchy according to new structure.
     */
    public void updateViewHierarchy(ModeStructureAccessor modeStructureAccessor) {
        Map<NbWindowAccessor, WindowModeStructureAccessor> wmsa = modeStructureAccessor.getWindowModeStructureAccessor();
        
        // update the accessors for ALL windows
        updateAccessors(modeStructureAccessor);
    

        // for each window
        for(NbWindowAccessor wa: wmsa.keySet()) {
            NbWindowImpl window = wa.getNbWindow();
            WindowModeStructureAccessor wms = wmsa.get(wa);
                       
            
            // update the current split
            currentSplitRootMap.put(window, updateViewForAccessor(window, wms.getSplitRootAccessor()));
            
            
//            if ((null == currentSplitRootMap.get(window)) && shouldUseFakeSplitRoot()) {
//                currentSplitRootMap.put(window, getFakeSplitRoot(window));
//            }
            
            // set desktop
            
            // NOTE THIS IF PREVENTS THE SPLIT FROM BEING PUT INTO AN AUX WINDOW??
//            if (getDesktop(window).getSplitRoot() == null) {
                setSplitRootIntoDesktop(window, currentSplitRootMap.get(window));
//            }
                        
            updateSlidingViews(window, wms.getSlidingModeAccessors().toArray(new SlidingAccessor[0])); 
        }
                
        updateSeparateViews(null, modeStructureAccessor.getSeparateModeAccessors());        
    }

    /**
     * Puts new instances of accessors in and reuses the old relevant views.
     */
    public void updateAccessors(ModeStructureAccessor modeStructureAccessor) {
        Map<ElementAccessor, ViewElement> a2v = new HashMap<ElementAccessor, ViewElement>(accessor2view);
        Set<ElementAccessor> accessors = new HashSet<ElementAccessor>();

        accessor2view.clear();
        view2accessor.clear();
        
        Map<NbWindowAccessor, WindowModeStructureAccessor> windowsAccessor = modeStructureAccessor.getWindowModeStructureAccessor();
        
        // for each window
        for(WindowModeStructureAccessor wmsa: windowsAccessor.values()) {
            // add split root
            accessors.addAll(getAllAccessorsForTree(wmsa.getSplitRootAccessor()));
            // add sliding 
            accessors.addAll(wmsa.getSlidingModeAccessors());
        }
        
        // finally add all separated
        accessors.addAll(Arrays.asList(modeStructureAccessor.getSeparateModeAccessors()));

        for (ElementAccessor accessor : accessors) {
            ElementAccessor similar = findSimilarAccessor(accessor, a2v);
            if (similar != null) {
                ViewElement view = a2v.get(similar);
                accessor2view.put(accessor, view);
                view2accessor.put(view, accessor);
            }
        }
    }

    private Set<ElementAccessor> getAllAccessorsForTree(ElementAccessor accessor) {
        Set<ElementAccessor> s = new HashSet<ElementAccessor>();
        if (accessor instanceof ModeAccessor) {
            s.add(accessor);
        } else if (accessor instanceof SplitAccessor) {
            SplitAccessor sa = (SplitAccessor) accessor;
            s.add(sa);
            ElementAccessor[] children = sa.getChildren();
            for (int i = 0; i < children.length; i++) {
                s.addAll(getAllAccessorsForTree(children[i]));
            }
        } else if (accessor instanceof EditorAccessor) {
            EditorAccessor ea = (EditorAccessor) accessor;
            s.add(ea);
            s.addAll(getAllAccessorsForTree(ea.getEditorAreaAccessor()));
        }

        return s;
    }

    private ElementAccessor findSimilarAccessor(ElementAccessor accessor, Map a2v) {
        for (Iterator it = a2v.keySet().iterator(); it.hasNext();) {
            ElementAccessor next = (ElementAccessor) it.next();
            if (accessor.originatorEquals(next)) {
                return next;
            }
        }

        return null;
    }

    private ViewElement updateViewForAccessor(NbWindowImpl window, ElementAccessor patternAccessor) {
//        if(patternAccessor instanceof SplitAccessor)
//            JOptionPane.showMessageDialog(null, "updateViewAccessorFor window=" + window + " pattern=" + patternAccessor);
        if (patternAccessor == null) {
            return null;
        }
        
        ViewElement view = accessor2view.get(patternAccessor);

        if (view != null) {
            if (patternAccessor instanceof SplitAccessor) {
                SplitAccessor sa = (SplitAccessor) patternAccessor;
                ElementAccessor[] childAccessors = sa.getChildren();
                ArrayList<ViewElement> childViews = new ArrayList<ViewElement>(childAccessors.length);
                for (int i = 0; i < childAccessors.length; i++) {
                    childViews.add(updateViewForAccessor(window, childAccessors[i]));
                }
                double[] splitWeights = sa.getSplitWeights();
                ArrayList<Double> weights = new ArrayList<Double>(splitWeights.length);
                for (int i = 0; i < splitWeights.length; i++) {
                    weights.add(Double.valueOf(splitWeights[i]));
                }
                SplitView sv = (SplitView) view;
                sv.setOrientation(sa.getOrientation());
                sv.setSplitWeights(weights);
                sv.setChildren(childViews);
                return sv;
            } else if (patternAccessor instanceof EditorAccessor) {
                EditorAccessor ea = (EditorAccessor) patternAccessor;
                EditorView ev = (EditorView) view;
                ev.setEditorArea(updateViewForAccessor(window, ea.getEditorAreaAccessor()));
                return ev;
            } else if (patternAccessor instanceof SlidingAccessor) {
                SlidingAccessor sa = (SlidingAccessor) patternAccessor;
                SlidingView sv = (SlidingView) view;
                sv.setTopComponents(sa.getOpenedTopComponents(), sa.getSelectedTopComponent());
                sv.setSlideBounds(sa.getBounds());
                sv.setSlideInSizes(sa.getSlideInSizes());
                return sv;
            } else if (patternAccessor instanceof ModeAccessor) {
                // It is a ModeView.
                ModeAccessor ma = (ModeAccessor) patternAccessor;
                ModeView mv = (ModeView) view;
                mv.setTopComponents(ma.getOpenedTopComponents(), ma.getSelectedTopComponent());
                if (ma.getState() == Constants.MODE_STATE_SEPARATED) {
                    mv.setFrameState(ma.getFrameState());
                }
                return mv;
            }
        } else {
            if (patternAccessor instanceof SplitAccessor) {
                SplitAccessor sa = (SplitAccessor) patternAccessor;
                ArrayList<Double> weights = new ArrayList<Double>(sa.getSplitWeights().length);
                for (int i = 0; i < sa.getSplitWeights().length; i++) {
                    weights.add(Double.valueOf(sa.getSplitWeights()[i]));
                }
                ArrayList<ViewElement> children = new ArrayList<ViewElement>(sa.getChildren().length);
                for (int i = 0; i < sa.getChildren().length; i++) {
                    children.add(updateViewForAccessor(window, sa.getChildren()[i]));
                }
                SplitView sv = new SplitView(controller, sa.getResizeWeight(),
                        sa.getOrientation(), weights, children);
                accessor2view.put(patternAccessor, sv);
                view2accessor.put(sv, patternAccessor);
                return sv;
            } else if (patternAccessor instanceof SlidingAccessor) {
                SlidingAccessor sa = (SlidingAccessor) patternAccessor;
                SlidingView sv = new SlidingView(window, controller, windowDnDManager,
                        sa.getOpenedTopComponents(), sa.getSelectedTopComponent(),
                        sa.getSide(),
                        sa.getSlideInSizes());
                sv.setSlideBounds(sa.getBounds());
                accessor2view.put(patternAccessor, sv);
                view2accessor.put(sv, patternAccessor);
                return sv;
            } else if (patternAccessor instanceof ModeAccessor) {
                ModeAccessor ma = (ModeAccessor) patternAccessor;
                ModeView mv;
                if (ma.getState() == Constants.MODE_STATE_JOINED || ma.getState() == Constants.MODE_STATE_NBWINDOW) {
                    mv = new ModeView(controller, windowDnDManager, ma.getResizeWeight(), ma.getKind(),
                            ma.getOpenedTopComponents(), ma.getSelectedTopComponent());
                } else {
                    mv = new ModeView(controller, windowDnDManager, ma.getBounds(), ma.getKind(), ma.getFrameState(),
                            ma.getOpenedTopComponents(), ma.getSelectedTopComponent());
                }
                accessor2view.put(patternAccessor, mv);
                view2accessor.put(mv, patternAccessor);
                return mv;
            } else if (patternAccessor instanceof EditorAccessor) {
                // Editor accesssor indicates there is a editor area (possible split subtree of editor modes).
                EditorAccessor editorAccessor = (EditorAccessor) patternAccessor;
                EditorView ev = new EditorView(controller, windowDnDManager,
                        editorAccessor.getResizeWeight(), updateViewForAccessor(window, editorAccessor.getEditorAreaAccessor()));
                accessor2view.put(patternAccessor, ev);
                view2accessor.put(ev, patternAccessor);
                return ev;
            }
        }

        throw new IllegalStateException("Unknown accessor type, accessor=" + patternAccessor); // NOI18N
    }

    private void updateSeparateViews(NbWindowImpl window, ModeAccessor[] separateModeAccessors) {
        Map<ModeView, ModeAccessor> newViews = new HashMap<ModeView, ModeAccessor>();
        for (int i = 0; i < separateModeAccessors.length; i++) {
            ModeAccessor ma = separateModeAccessors[i];
            ModeView mv = (ModeView) updateViewForAccessor(window, ma);
            newViews.put(mv, ma);
        }

        Set<ModeView> oldViews = new HashSet<ModeView>(separateModeViews.keySet());
        oldViews.removeAll(newViews.keySet());

        separateModeViews.clear();
        separateModeViews.putAll(newViews);

        //#212590 - main window must be visible before showing child Dialogs
        if (WindowManagerImpl.getInstance().getMainWindow().isVisible()) {
            // Close all old views.
            for (Iterator it = oldViews.iterator(); it.hasNext();) {
                ModeView mv = (ModeView) it.next();
                Component comp = mv.getComponent();
                if (comp.isVisible()) {
                    comp.setVisible(false);
                }
                ((Window) comp).dispose();
            }

            // Open all new views.
            for (Iterator it = newViews.keySet().iterator(); it.hasNext();) {
                ModeView mv = (ModeView) it.next();
                Component comp = mv.getComponent();
                // #37463, it is needed to provide a check, otherwise the window would 
                // get fronted each time.
                if (!comp.isVisible()) {
                    comp.setVisible(true);
                }
            }
        }
    }

    private void updateSlidingViews(NbWindowImpl window, SlidingAccessor[] slidingModeAccessors) {
//            StringBuffer buffer = new StringBuffer();
//            buffer.append("ViewHierarchy.updateSlidingViews\n");
//            buffer.append(window  == null?"NbMainWindow":window.getName());
//            buffer.append("\n");
//            for(SlidingAccessor i: slidingModeAccessors)
//                buffer.append(i.getName() +" " + i.getSide() + " has " + i.getOpenedTopComponents().length + "\n");
//            JOptionPane.showMessageDialog(null, buffer.toString());
            
            
        Map<SlidingView, SlidingAccessor> newViews = new HashMap<SlidingView, SlidingAccessor>();
        for (int i = 0; i < slidingModeAccessors.length; i++) {
            SlidingAccessor sa = slidingModeAccessors[i];
            SlidingView sv = (SlidingView) updateViewForAccessor(window, sa);
//            JOptionPane.showMessageDialog(null, "" + window + " sv=" + sv + " sa=" + sa);
            newViews.put(sv, sa);
        }
        Map<SlidingView, SlidingAccessor>  map = slidingModeViewsMap.get(window);
        if(map == null) {
            map = new HashMap<SlidingView, SlidingAccessor>(4);
            slidingModeViewsMap.put(window, map);
        }

        
        Set<SlidingView> oldViews = new HashSet<SlidingView>(map.keySet());
        oldViews.removeAll(newViews.keySet());

        Set<SlidingView> addedViews = new HashSet<SlidingView>(newViews.keySet());
        addedViews.removeAll(map.keySet());

        map.clear();
        map.putAll(newViews);

        // remove old views.
        for (SlidingView curSv : oldViews) {
            getDesktop(window).removeSlidingView(curSv);
        }
        // add all new views.
        if(addedViews.size() > 0)
        for (SlidingView curSv : addedViews) {
//            JOptionPane.showMessageDialog(null, "desktop for " + (window==null?"NbMainWindow":window.getName()) + " " + curSv.getSide() + " " + curSv.getTopComponents().size());
            getDesktop(window).addSlidingView(curSv);
        }

        getDesktop(window).updateCorners();
    }

    public ModeView getModeViewForAccessor(ModeAccessor modeAccessor) {
        return (ModeView) accessor2view.get(modeAccessor);
    }

    public ElementAccessor getAccessorForView(ViewElement view) {
        return (ElementAccessor) view2accessor.get(view);
    }

    public void activateMode(ModeAccessor activeModeAccessor) {
        ModeView activeModeV = getModeViewForAccessor(activeModeAccessor);
        activateModeView(activeModeV);
    }

    private void activateModeView(ModeView modeView) {
        setActiveModeView(modeView);
        if (modeView != null) {
            modeView.focusSelectedTopComponent();
            // remember last non sliding active view
            if (!(modeView instanceof SlidingView)) {
                lastNonSlidingActive = new WeakReference<ModeView>(modeView);
            }
        }
    }

    /**
     * Set active mode view.
     */
    private void setActiveModeView(ModeView modeView) {
        //#39729 fix - when main window has focus, do not discard (in SDI the actual component can be hidden
        if (modeView == activeModeView && activeModeView != null && activeModeView.isActive()) {
            return;
        }
        if (activeModeView != null && modeView != activeModeView) {
            activeModeView.setActive(false);
        }

        activeModeView = modeView;

        if (activeModeView != null) {
            activeModeView.setActive(true);
        }
    }

    /**
     * Gets active mode view.
     */
    public ModeView getActiveModeView() {
        return activeModeView;
    }

    /**
     * Gets last non sliding mode view that was active in the past or null if no
     * such exists
     */
    ModeView getLastNonSlidingActiveModeView() {
        return lastNonSlidingActive == null ? null : lastNonSlidingActive.get();
    }

    public void setMaximizedModeView(ModeView modeView) {
        if (modeView == maximizedModeView) {
            return;
        }

        maximizedModeView = modeView;
    }

    public ModeView getMaximizedModeView() {
        return maximizedModeView;
    }

    public void removeModeView(ModeView modeView) {
        if (!view2accessor.containsKey(modeView)) {
            return;
        }


        ElementAccessor i = view2accessor.get(modeView);       
        Object accessor = view2accessor.remove(modeView);
        accessor2view.remove(accessor);

        if (separateModeViews.keySet().contains(modeView)) {
            separateModeViews.keySet().remove(modeView);
            modeView.getComponent().setVisible(false);
            return;
        }

        setSplitRootIntoDesktop(null, (SplitView) removeModeViewFromElement(getDesktop(null).getSplitRoot(), modeView));
    }

    /**
     * Gets set of all mode view components.
     */
    public Set<Component> getModeComponents() {
        Set<Component> set = new HashSet<Component>();
        for (ViewElement next : view2accessor.keySet()) {
            if (next instanceof ModeView) {
                ModeView modeView = (ModeView) next;
                set.add(modeView.getComponent());
            }
        }

        return set;
    }

    public Component getSlidingModeComponent(NbWindowImpl window, String side) {
        Iterator it = slidingModeViewsMap.get(window).keySet().iterator();
        while (it.hasNext()) {
            SlidingView mod = (SlidingView) it.next();
            if (mod.getSide().equals(side)) {
                return mod.getComponent();
            }
        }
        return null;
    }

    /**
     * Gets set of separate mode view frames and editor frame (if separated).
     */
    public Set<Component> getSeparateModeFrames() {
        Set<Component> s = new HashSet<Component>();
        for (ModeView modeView : separateModeViews.keySet()) {
            s.add(modeView.getComponent());
        }

        if (editorAreaFrame != null) {
            s.add(editorAreaFrame);
        }

        return s;
    }

    private ViewElement removeModeViewFromElement(ViewElement view, ModeView modeView) {
        if (view == modeView) {
            return null;
        } else if (view instanceof SplitView) {
            SplitView sv = (SplitView) view;
            List<ViewElement> children = sv.getChildren();
            ArrayList<ViewElement> newChildren = new ArrayList<ViewElement>(children.size());
            ViewElement removedView = null;
            for (ViewElement child : children) {
                ViewElement newChild = removeModeViewFromElement(child, modeView);
                if (newChild != child) {
                    removedView = child;
                }
                if (null != newChild) {
                    newChildren.add(newChild);
                }
            }
            if (newChildren.size() == 0) {
                //the view is not split anymore
                return newChildren.get(0);
            }
            if (null != removedView) {
                sv.remove(removedView);
            }
            sv.setChildren(newChildren);
            return sv;
        } else if (view instanceof EditorView) {
            EditorView ev = (EditorView) view;
            ev.setEditorArea(removeModeViewFromElement(ev.getEditorArea(), modeView));
            return ev;
        }

        return view;
    }

    private Component getDesktopComponent(NbWindowImpl window) {
        return currentSplitRootMap.get(window) == null ? null : getDesktop(window).getDesktopComponent();
    }

    public ViewElement getSplitRootElement(NbWindowImpl window) {
        return currentSplitRootMap.get(window);
    }

    public void releaseAll() {
        // TODO gwi-release: Need to release all split roots?
        setSplitRootIntoDesktop(null, null);
        separateModeViews.clear();
        activeModeView = null;
        accessor2view.clear();
    }

    public void setSplitModesVisible(boolean visible) {
        // set main window split modes visible
        //setVisibleModeElement(getDesktop(null).getSplitRoot(), visible);
        
        // for each window 
        for(NbWindowImpl window: win2Frame.keySet()) {
            setVisibleModeElement(getDesktop(window).getSplitRoot(), visible);            
        }
    }

    private static void setVisibleModeElement(ViewElement view, boolean visible) {
        if (view instanceof ModeView) {
            view.getComponent().setVisible(visible);
        } else if (view instanceof SplitView) {
            SplitView sv = (SplitView) view;
            List children = sv.getChildren();
            for (Iterator i = children.iterator(); i.hasNext();) {
                ViewElement child = (ViewElement) i.next();
                setVisibleModeElement(child, visible);
            }
        } else if (view instanceof EditorView) {
            setVisibleModeElement(((EditorView) view).getEditorArea(), visible);
        }
    }

    public void setSeparateModesVisible(boolean visible) {
        if (editorAreaFrame != null) {
            if (editorAreaFrame.isVisible() != visible) {
                //#48829 the visible check needed because of this issue
                editorAreaFrame.setVisible(visible);
            }
        }

        for (ModeView mv : separateModeViews.keySet()) {
            if (mv.getComponent().isVisible() != visible) {
                //#48829 the visible check needed because of this issue
                mv.getComponent().setVisible(visible);
            }
        }
    }

    public void updateEditorAreaFrameState(int frameState) {
        if (editorAreaFrame != null) {
            editorAreaFrame.setExtendedState(frameState);
        }
    }

    public void updateFrameStates() {
        for (ModeView mv : separateModeViews.keySet()) {
            mv.updateFrameState();
        }
    }

    public void updateMainWindowBounds(WindowSystemAccessor wsa) {
        JFrame frame = mainWindow.getFrame();
        if (wsa.getEditorAreaState() == Constants.EDITOR_AREA_JOINED) {
            frame.setBounds(wsa.getMainWindowBoundsJoined());
        } else {
            // #45832 clear the desktop when going to SDI,
            setMainWindowDesktop(null);
            // invalidate to recalculate the main window's preffered size..
            frame.invalidate();
            frame.setBounds(wsa.getMainWindowBoundsSeparated());
        }
        // #38146 So the updateSplit works with correct size.
        frame.validate();
        // PENDING consider better handling this event so there is not doubled
        // validation (one in MainWindow - needs to be provided here) and this as second one.
    }

    private void setMaximizedViewIntoDesktop(NbWindowImpl window, ViewElement elem) {
        boolean revalidate = elem.updateAWTHierarchy(getDesktop(window).getInnerPaneDimension());

        getDesktop(window).setMaximizedView(elem);

        if (revalidate) {
            getDesktop(window).getDesktopComponent().invalidate();
            ((JComponent) getDesktop(window).getDesktopComponent()).revalidate();
            getDesktop(window).getDesktopComponent().repaint();
        }
    }

    private void setSplitRootIntoDesktop(NbWindowImpl window, ViewElement root) {
        boolean revalidate = false;
        getDesktop(window).setSplitRoot(root);
        
        
        // if aux window - place this desktop into the window
        if(window != null) {
            Window frame = (Window)win2Frame.get(window);            
            if (frame == null) {
                if(window.isDialog()) {
                    frame = new NbWindowDialog(window, window.getBounds(), controller);
                } else {
                    frame = new NbWindowFrame(window, window.getBounds(), controller);
                }
                win2Frame.put(window, (NbWindowComponent)frame);

                // install listeners
                frame.addComponentListener(nbWindowListener);
            }
            if (!frame.isVisible() && window.isVisible()) {
                frame.setVisible(true);
            }
            // TODO gwi-window: HOW TO CORRECTLY DESTORY THE WINDOW?
            if (frame.isVisible() && !window.isVisible()) {
                frame.setVisible(false);
            }
        }
        if(win2Frame.get(window) == null)
            MainWindow.getInstance().setDesktop(getDesktop(window).getDesktopComponent());
        else
            win2Frame.get(window).setDesktop(getDesktop(window).getDesktopComponent());
        
                
        if (root != null) {
            Dimension dim = getDesktop(window).getInnerPaneDimension();
//            debugLog("innerpanedidim=" + dim + " currentsize=" + root.getComponent().getSize());
            revalidate = root.updateAWTHierarchy(dim);
        }

        if (revalidate) {
            getDesktop(window).getDesktopComponent().invalidate();
            ((JComponent) getDesktop(window).getDesktopComponent()).revalidate();
            getDesktop(window).getDesktopComponent().repaint();
//            debugLog("revalidating..size=" + desktop.getDesktopComponent().getSize() + "innerpane=" + desktop.getInnerPaneDimension());
        }
    }

    // PENDING Revise, updating desktop and editor area, bounds... separate this method.
    public void updateDesktop(WindowSystemAccessor wsa) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        List<Component> focusOwnerAWTHierarchyChain; // To find out whether there was a change in AWT hierarchy according to focusOwner.
        if (focusOwner != null) {
            focusOwnerAWTHierarchyChain = getComponentAWTHierarchyChain(focusOwner);
        } else {
            focusOwnerAWTHierarchyChain = Collections.emptyList();
        }

        try {
            if (wsa.getEditorAreaState() == Constants.EDITOR_AREA_JOINED) {
                if (maximizedModeView != null) {
                    setMainWindowDesktop(getDesktopComponent(null));
                    setMaximizedViewIntoDesktop(null, maximizedModeView);
                    return;
                }
            }

            int editorAreaState = wsa.getEditorAreaState();
            if (editorAreaState == Constants.EDITOR_AREA_JOINED) {
                if (editorAreaFrame != null) {
                    editorAreaFrame.setVisible(false);
                    editorAreaFrame = null;
                }
                setMainWindowDesktop(getDesktopComponent(null));
                setSplitRootIntoDesktop(null, getSplitRootElement(null));

            } else {
                boolean showEditorFrame = hasEditorAreaVisibleView(null);  // TODO gwi-window: using null will be main only

                if (editorAreaFrame == null && showEditorFrame) {
                    editorAreaFrame = createEditorAreaFrame();
                    Rectangle editorAreaBounds = wsa.getEditorAreaBounds();
                    if (editorAreaBounds != null) {
                        editorAreaFrame.setBounds(editorAreaBounds);
                    }
                } else if (editorAreaFrame != null && !showEditorFrame) { // XXX
                    editorAreaFrame.setVisible(false);
                    editorAreaFrame = null;
                }

                setMainWindowDesktop(null);
                if (showEditorFrame) {
                    setSplitRootIntoDesktop(null, getSplitRootElement(null));
                    setEditorAreaDesktop(getDesktopComponent(null));
                    // #39755 restore the framestate of the previously closed editorArea.
                    updateEditorAreaFrameState(wsa.getEditorAreaFrameState());
                }
            }
        } finally {
            // XXX #37239, #37632 Preserve focus in case the focusOwner component
            // was 'shifted' in AWT hierarchy. I.e. when removed/added it loses focus,
            // but we need to keep it, e.g. for case when its parent split is removing.
            if (focusOwner != null
                    && !focusOwnerAWTHierarchyChain.equals(getComponentAWTHierarchyChain(focusOwner))
                    /**
                     * #64189
                     */
                    && SwingUtilities.getAncestorOfClass(Window.class, focusOwner) != null) {
                focusOwner.requestFocus();
            }
        }
    }

    public void updateDesktopTest(ModeAccessor ma, WindowSystemAccessor wsa) {
        NbWindowImpl window = null;
        if(ma != null) {
            ModeImpl mode = ma.getMode();
            window = WindowManagerImpl.getInstance().getWindowForMode(mode);
        }
        
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        List<Component> focusOwnerAWTHierarchyChain; // To find out whether there was a change in AWT hierarchy according to focusOwner.
        if (focusOwner != null) {
            focusOwnerAWTHierarchyChain = getComponentAWTHierarchyChain(focusOwner);
        } else {
            focusOwnerAWTHierarchyChain = Collections.emptyList();
        }

        try {
            if (wsa.getEditorAreaState() == Constants.EDITOR_AREA_JOINED) {
                if (maximizedModeView != null) {
                    if(window == null) {
                        setMainWindowDesktop(getDesktopComponent(window));  // NO THIS NEEDS TO SET THE DESKTOP INTO THE APPROPIATE WINDOW (MAIN OR NBWINDOW)
                    } else {
                        // put desktop component into nbwindow?
                    }
                    setMaximizedViewIntoDesktop(window, maximizedModeView);
                    return;
                }
            }

            int editorAreaState = wsa.getEditorAreaState();
            if (editorAreaState == Constants.EDITOR_AREA_JOINED) {
                if (editorAreaFrame != null) {
                    editorAreaFrame.setVisible(false);
                    editorAreaFrame = null;
                }
                if(window == null) {
                    setMainWindowDesktop(getDesktopComponent(null));
                } else {
                    // put desktop component into nbwindow?
                }
                setSplitRootIntoDesktop(window, getSplitRootElement(window));
            } else {
                boolean showEditorFrame = hasEditorAreaVisibleView(window);  // TODO gwi-window: using null will be main only

                if (editorAreaFrame == null && showEditorFrame) {
                    editorAreaFrame = createEditorAreaFrame();
                    Rectangle editorAreaBounds = wsa.getEditorAreaBounds();
                    if (editorAreaBounds != null) {
                        editorAreaFrame.setBounds(editorAreaBounds);
                    }
                } else if (editorAreaFrame != null && !showEditorFrame) { // XXX
                    editorAreaFrame.setVisible(false);
                    editorAreaFrame = null;
                }

                if(window == null)
                    setMainWindowDesktop(null);
                if (showEditorFrame) {
                    setSplitRootIntoDesktop(window, getSplitRootElement(window));
                    setEditorAreaDesktop(getDesktopComponent(window));
                    // #39755 restore the framestate of the previously closed editorArea.
                    updateEditorAreaFrameState(wsa.getEditorAreaFrameState());
                }
            }
        } finally {
            // XXX #37239, #37632 Preserve focus in case the focusOwner component
            // was 'shifted' in AWT hierarchy. I.e. when removed/added it loses focus,
            // but we need to keep it, e.g. for case when its parent split is removing.
            if (focusOwner != null
                    && !focusOwnerAWTHierarchyChain.equals(getComponentAWTHierarchyChain(focusOwner))
                    /**
                     * #64189
                     */
                    && SwingUtilities.getAncestorOfClass(Window.class, focusOwner) != null) {
                focusOwner.requestFocus();
            }
        }
    }
    
    
    public void updateDesktop() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        List focusOwnerAWTHierarchyChain; // To find out whether there was a change in AWT hierarchy according to focusOwner.
        if (focusOwner != null) {
            focusOwnerAWTHierarchyChain = getComponentAWTHierarchyChain(focusOwner);
        } else {
            focusOwnerAWTHierarchyChain = Collections.EMPTY_LIST;
        }
        try {
            //        System.out.println("updatedesktop()");
            if (mainWindow.hasDesktop()) {
                setMainWindowDesktop(getDesktopComponent(null));
                if (maximizedModeView != null) {
                    //                System.out.println("viewhierarchy: have maximized=" + maximizedModeView.getClass());
                    setMaximizedViewIntoDesktop(null, maximizedModeView);
                    //                setMainWindowDesktop();
                } else {
                    //                System.out.println("viewhierarchy: no maximized");
                    setSplitRootIntoDesktop(null, getSplitRootElement(null));   //TODO gwi-window: using null will be main window only
                    //                setMainWindowDesktop(getDesktopComponent());
                }
            } else {
                boolean showEditorFrame = hasEditorAreaVisibleView(null); //TODO gwi-window: using null will be main window only

                if (editorAreaFrame != null) {
                    if (showEditorFrame) {
                        editorAreaFrame.setDesktop(getDesktopComponent(null));
                    } else { // XXX
                        editorAreaFrame.setVisible(false);
                        editorAreaFrame = null;
                    }
                }
            }
        } finally {
            // XXX #37239, #37632 Preserve focus in case the focusOwner component
            // was 'shifted' in AWT hierarchy. I.e. when removed/added it loses focus,
            // but we need to keep it, e.g. for case when its parent split is removing.
            if (focusOwner != null
                    && !focusOwnerAWTHierarchyChain.equals(getComponentAWTHierarchyChain(focusOwner))
                    /**
                     * #64189
                     */
                    && SwingUtilities.getAncestorOfClass(Window.class, focusOwner) != null) {
                focusOwner.requestFocus();
            }
        }
    }

    public void performSlideIn(SlideOperation operation) {
        getDesktop(operation.getNbWindow()).performSlideIn(operation, getPureEditorAreaBounds(operation.getNbWindow())); //TODO gwi-window: using null will be main window only
    }

    public void performSlideOut(SlideOperation operation) {
        getDesktop(operation.getNbWindow()).performSlideOut(operation, getPureEditorAreaBounds(operation.getNbWindow())); //TODO gwi-window: using null will be main window only
    }

    public void performSlideIntoDesktop(SlideOperation operation) {
        getDesktop(operation.getNbWindow()).performSlideIntoDesktop(operation, getPureEditorAreaBounds(operation.getNbWindow())); //TODO gwi-window: using null will be main window only
    }

    public void performSlideIntoEdge(SlideOperation operation) {
        getDesktop(operation.getNbWindow()).performSlideIntoEdge(operation, getPureEditorAreaBounds(operation.getNbWindow())); //TODO gwi-window: using null will be main window only
    }

    public void performSlideResize(SlideOperation operation) {
        getDesktop(operation.getNbWindow()).performSlideResize(operation);
    }

    public void performSlideToggleMaximize(TopComponent tc, String side) {
        // TODO gwi-window: using null window
        getDesktop(null).performSlideToggleMaximize(tc, side, getPureEditorAreaBounds(null)); //TODO gwi-window: using null will be main window only
    }

    private void setMainWindowDesktop(Component component) {
        setDesktop(component, true);
    }

    private void setEditorAreaDesktop(Component component) {
        setDesktop(component, false);
    }

    private void setDesktop(Component component, boolean toMainWindow) {

        if (toMainWindow) {
            mainWindow.setDesktop(component);
        } else {
            editorAreaFrame.setDesktop(component);
        }

    }

    private List<Component> getComponentAWTHierarchyChain(Component comp) {
        List<Component> l = new ArrayList<Component>();
        Component c = comp;
        while (c != null) {
            l.add(c);
            c = c.getParent();
        }

        Collections.reverse(l);
        return l;
    }

    private boolean hasEditorAreaVisibleView(NbWindowImpl window) {
        //#41875 fix, checking for null EditorView, can be null when using the netbeans.winsys.hideEmptyDocArea command line property
        EditorView view = findEditorAreaElement(window);
        return (view != null ? (view.getEditorArea() != null) : false);
    }

    private EditorAreaFrame createEditorAreaFrame() {
        final EditorAreaFrame frame = new EditorAreaFrame();
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent evt) {
                if (frame.getExtendedState() == Frame.MAXIMIZED_BOTH) {
                    // Ignore changes when the frame is in maximized state.
                    return;
                }
                controller.userResizedEditorArea(frame.getBounds());
            }

            @Override
            public void componentMoved(ComponentEvent evt) {
                if (frame.getExtendedState() == Frame.MAXIMIZED_BOTH) {
                    // Ignore changes when the frame is in maximized state.
                    return;
                }
                controller.userResizedEditorArea(frame.getBounds());
            }
        });
        frame.setWindowActivationListener(controller);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evt) {
                closeEditorModes(null);  // TODO gwi-window: USING null this will only work for main window
            }
        });

        frame.addWindowStateListener(new WindowStateListener() {
            @Override
            public void windowStateChanged(WindowEvent evt) {
                // All the timestamping is a a workaround beause of buggy GNOME and of its kind who iconify the windows on leaving the desktop.
                long currentStamp = System.currentTimeMillis();
                if (currentStamp > (frame.getUserStamp() + 500) && currentStamp > (frame.getMainWindowStamp() + 1000)) {
                    controller.userChangedFrameStateEditorArea(evt.getNewState());
                    long stamp = System.currentTimeMillis();
                    frame.setUserStamp(stamp);
                } else {
                    frame.setUserStamp(0);
                    frame.setMainWindowStamp(0);
                    frame.setExtendedState(evt.getOldState());
                    //frame.setExtendedState(evt.getOldState());
                }

            }
        });

        return frame;
    }

    private void closeEditorModes(NbWindowImpl window) {
        closeModeForView(findEditorAreaElement(window).getEditorArea());
    }

    private void closeModeForView(ViewElement view) {
        if (view instanceof ModeView) {
            controller.userClosingMode((ModeView) view);
        } else if (view instanceof SplitView) {
            SplitView sv = (SplitView) view;
            List children = sv.getChildren();
            for (Iterator i = children.iterator(); i.hasNext();) {
                ViewElement child = (ViewElement) i.next();
                closeModeForView(child);
            }
        }
    }

    public void updateEditorAreaBounds(Rectangle bounds) {
        if (editorAreaFrame != null) {
            editorAreaFrame.setBounds(bounds);
        }
    }

    // XXX
    public Rectangle getPureEditorAreaBounds(NbWindowImpl window) {
        EditorView editorView = findEditorAreaElement(window);
        if (editorView == null) {
            return new Rectangle();
        } else {
            return editorView.getPureBounds(window);
        }
    }

    private EditorView findEditorAreaElement(NbWindowImpl window) {
        return findEditorViewForElement(getSplitRootElement(window));
    }

    Component getEditorAreaComponent(NbWindowImpl window) {
        EditorView editor = findEditorAreaElement(window);
        if (null != editor) {
            return editor.getComponent();
        }
        return null;
    }

    private EditorView findEditorViewForElement(ViewElement view) {
        if (view instanceof EditorView) {
            return (EditorView) view;
        } else if (view instanceof SplitView) {
            SplitView sv = (SplitView) view;
            List children = sv.getChildren();
            for (Iterator i = children.iterator(); i.hasNext();) {
                ViewElement child = (ViewElement) i.next();
                EditorView ev = findEditorViewForElement(child);
                if (null != ev) {
                    return ev;
                }
            }
        }

        return null;
    }

    public void updateUI() {
        SwingUtilities.updateComponentTreeUI(mainWindow.getFrame());
        if (editorAreaFrame != null) {
            SwingUtilities.updateComponentTreeUI(editorAreaFrame);
        }
        for (ModeView mv : separateModeViews.keySet()) {
            SwingUtilities.updateComponentTreeUI(mv.getComponent());
        }
    }

    public Set<TopComponent> getShowingTopComponents() {
        Set<TopComponent> s = new HashSet<TopComponent>();
        for (ElementAccessor accessor : accessor2view.keySet()) {
            if (accessor instanceof ModeAccessor) {
                s.add(((ModeAccessor) accessor).getSelectedTopComponent());
            }
        }
        for (ModeAccessor accessor : separateModeViews.values()) {
            s.add(accessor.getSelectedTopComponent());
        }

        return s;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();        

        sb.append("SplitRootMap:\n");
        for(NbWindowImpl window: currentSplitRootMap.keySet()) {
            sb.append((window == null?"NbMainWindow":window.getName()) + "\n");
            sb.append(dumpElement(currentSplitRootMap.get(window), 0) + "\n"); // NOI18N           
        }

        sb.append("DesktopMap:\n");
        for(NbWindowImpl window: desktopMap.keySet()) {
            sb.append((window == null?"NbMainWindow":window.getName()) + " >" + desktopMap.get(window) + "<\n");
            sb.append(dumpElement(desktopMap.get(window).getSplitRoot(), 0) + "\n"); // NOI18N           
        }

        sb.append("FakeSplitMap:\n");
        for(NbWindowImpl window: fakeSplitMap.keySet()) {
            sb.append((window == null?"NbMainWindow":window.getName()) + " " + fakeSplitMap.get(window) + "\n");
            sb.append(dumpElement(fakeSplitMap.get(window), 0) + "\n"); // NOI18N           
        }
        
        sb.append("Separated:\n");
        sb.append(separateModeViews.keySet() + "\n"); // NOI18N
        
        return sb.toString();
    }

    private String dumpElement(ViewElement view, int indent) {
        String indentString = createIndentString(indent);
        StringBuffer sb = new StringBuffer();

        if (view instanceof ModeView) {
            sb.append(indentString + view + "->" + view.getComponent().getClass() + "@" + view.getComponent().hashCode());
        } else if (view instanceof EditorView) {
            sb.append(indentString + view);
            sb.append("\n" + dumpElement(((EditorView) view).getEditorArea(), ++indent));
        } else if (view instanceof SplitView) {
            sb.append(indentString + view + "->" + view.getComponent().getClass() + "@" + view.getComponent().hashCode());
            indent++;
            List children = ((SplitView) view).getChildren();
            for (Iterator i = children.iterator(); i.hasNext();) {
                ViewElement child = (ViewElement) i.next();
                sb.append("\n" + dumpElement(child, indent));
            }
        }

        return sb.toString();
    }

    private static String createIndentString(int indent) {
        StringBuffer sb = new StringBuffer(indent);
        for (int i = 0; i < indent; i++) {
            sb.append("\t");
        }

        return sb.toString();
    }

    private String dumpAccessors() {
        StringBuffer sb = new StringBuffer();
        for (ElementAccessor accessor : accessor2view.keySet()) {
            sb.append("accessor=" + accessor + "\tview=" + accessor2view.get(accessor) + "\n"); // NOI18N
        }

        return sb.toString();
    }

    private void changeStateOfSeparateViews(boolean iconify) {
        // All the timestamping is a a workaround beause of buggy GNOME and of its kind who iconify the windows on leaving the desktop.
        long mainStamp = System.currentTimeMillis();
        if (editorAreaFrame != null) {
            if (iconify) {
                if (mainStamp < (editorAreaFrame.getUserStamp() + 500)) {
                    int newState = editorAreaFrame.getExtendedState() & ~Frame.ICONIFIED;
                    controller.userChangedFrameStateEditorArea(newState);
                    editorAreaFrame.setExtendedState(newState);
                }
            }
            editorAreaFrame.setMainWindowStamp(mainStamp);
            editorAreaFrame.setVisible(!iconify);
        }
        for (Iterator it = separateModeViews.keySet().iterator(); it.hasNext();) {
            ModeView mv = (ModeView) it.next();
            Component comp = mv.getComponent();
            if (comp instanceof Frame) {
                Frame fr = (Frame) comp;
                if (iconify) {
                    if (mainStamp < (mv.getUserStamp() + 500)) {
                        int newState = fr.getExtendedState() & ~Frame.ICONIFIED;
                        controller.userChangedFrameStateMode(mv, newState);
                        mv.setFrameState(newState);
                    }
                }
                mv.setMainWindowStamp(mainStamp);
                fr.setVisible(!iconify);
            }
        }
    }

    void userStartedKeyboardDragAndDrop(TopComponentDraggable draggable) {
        windowDnDManager.startKeyboardDragAndDrop(draggable);
    }

    /**
     * Main window listener.
     */
    private static class MainWindowListener extends ComponentAdapter
            implements WindowStateListener {

        private final Controller controller;
        private final ViewHierarchy hierarchy;

        public MainWindowListener(Controller controller, ViewHierarchy hierarchy) {
            this.controller = controller;
            this.hierarchy = hierarchy;
        }

        @Override
        public void componentResized(ComponentEvent evt) {
            controller.userResizedMainWindow(evt.getComponent().getBounds());
        }

        @Override
        public void componentMoved(ComponentEvent evt) {
            controller.userMovedMainWindow(evt.getComponent().getBounds());
        }

        @Override
        public void windowStateChanged(WindowEvent evt) {
            int oldState = evt.getOldState();
            int newState = evt.getNewState();
            controller.userChangedFrameStateMainWindow(newState);

            if (Constants.AUTO_ICONIFY) {
                if (((oldState & Frame.ICONIFIED) == 0)
                        && ((newState & Frame.ICONIFIED) == Frame.ICONIFIED)) {
                    hierarchy.changeStateOfSeparateViews(true);
                } else if (((oldState & Frame.ICONIFIED) == Frame.ICONIFIED)
                        && ((newState & Frame.ICONIFIED) == 0)) {
                    hierarchy.changeStateOfSeparateViews(false);
                }
            }
        }
    } // End of main window listener.

    private static void debugLog(String message) {
        Debug.log(ViewHierarchy.class, message);
    }

    private boolean shouldUseFakeSplitRoot() {
        return Constants.SWITCH_HIDE_EMPTY_DOCUMENT_AREA;
    }


    //#209678 - with option SWITCH_HIDE_EMPTY_DOCUMENT_AREA closing all editor
    //windows will hide all sliding bars, so let's use a dummy split root
    //to keep sliding bars visible when no window is docked
    private ViewElement getFakeSplitRoot(NbWindowImpl window) {
        if (null == fakeSplitMap.get(window)) {
            final JPanel panel = new JPanel();
            panel.setOpaque(false);
            fakeSplitMap.put(window, new ViewElement(controller, 1.0) {

                @Override
                public Component getComponent() {
                    return panel;
                }

                @Override
                public boolean updateAWTHierarchy(Dimension availableSpace) {
                    return false;
                }
            });
        }
        return fakeSplitMap.get(window);
    }

    private DesktopImpl getDesktop(NbWindowImpl window) {
        synchronized (this) {
            if (null == desktopMap.get(window)) {
                desktopMap.put(window, new DesktopImpl(window == null)); // window == null identifies the main-window
            }
        }
        return desktopMap.get(window);
    }

    // NEW -------------------------------------------------------------------
    public void updateNbWindows(NbWindowStructureAccessor nbWindowStructureAccessor) {
        Set<NbWindowImpl> oldFrameNames = new HashSet<NbWindowImpl>(win2Frame.keySet());
        Set<NbWindowImpl> newFrameNames = new HashSet<NbWindowImpl>();

        NbWindowAccessor[] nbWindowAccessors = nbWindowStructureAccessor.getNbWindowAccessors();
        for (NbWindowAccessor winAccessor : nbWindowAccessors) {
            newFrameNames.add(winAccessor.getNbWindow());
        }
        oldFrameNames.removeAll(newFrameNames);

        // hide closed frames
        for (NbWindowImpl win : oldFrameNames) {
            NbWindowComponent window = win2Frame.get(win);
            win2Frame.remove(win);
            window.setVisible(false);
        }

        // show new frames
        for (NbWindowAccessor winAccessor : nbWindowAccessors) {
            //String name = winAccessor.getName();
            NbWindowComponent frame = win2Frame.get(winAccessor.getNbWindow());
            if (frame == null) {
                NbWindowImpl win = winAccessor.getNbWindow();

                frame = new NbWindowFrame(win, winAccessor.getBounds(), controller);
                win2Frame.put(win, frame);

                // install listeners
                frame.addComponentListener(nbWindowListener);
            }
            if (!frame.isVisible() && winAccessor.getNbWindow().isVisible()) {
                frame.setVisible(true);
            }
        }
    }

    //TODO gwi-window: rename getAuxWindowComponent
    public NbWindowComponent getNbWindowFrame(NbWindowImpl window) {
        return win2Frame.get(window);
    }

    //TODO gwi-window: rename getAuxWindowComponent
    public NbWindowComponent getNbWindowFrame(String name) {
        // temp! methods using this need to be refactored to use the NbWindowImpl
        for (NbWindowImpl win : win2Frame.keySet()) {
            if (win.getName().equals(name)) {
                return win2Frame.get(win);
            }
        }
        return null; // no frame found
    }

    /**
     * NBWindow listener.
     */
    private static class NbWindowListener extends ComponentAdapter
            implements WindowStateListener {

        private final Controller controller;
        private final ViewHierarchy hierarchy;

        public NbWindowListener(Controller controller, ViewHierarchy hierarchy) {
            this.controller = controller;
            this.hierarchy = hierarchy;
        }

        @Override
        public void componentResized(ComponentEvent evt) {
            Window frame = (Window) evt.getComponent();
            NbWindow window = ((NbWindowComponent) frame).getNbWindow();
            controller.userResizedNbWindow((NbWindowImpl)window, evt.getComponent().getBounds());
        }

        @Override
        public void componentMoved(ComponentEvent evt) {
            Window frame = (Window) evt.getComponent();
            NbWindow window = ((NbWindowComponent) frame).getNbWindow();
            controller.userMovedNbWindow((NbWindowImpl)window, evt.getComponent().getBounds());
        }

        @Override
        public void windowStateChanged(WindowEvent evt) {
//            int oldState = evt.getOldState();
//            int newState = evt.getNewState();
//            controller.userChangedFrameStateMainWindow(newState);
//            
//            if (Constants.AUTO_ICONIFY) {
//                if (((oldState & Frame.ICONIFIED) == 0) &&
//                    ((newState & Frame.ICONIFIED) == Frame.ICONIFIED)) {
//                    hierarchy.changeStateOfSeparateViews(true);
//                } else if (((oldState & Frame.ICONIFIED) == Frame.ICONIFIED) && 
//                           ((newState & Frame.ICONIFIED) == 0 )) {
//                    hierarchy.changeStateOfSeparateViews(false);
//                }
//            }
        }
    } // End of nbwindow listener.

    public void hideNbWindows() {
        for (NbWindowComponent win : win2Frame.values()) {
            win.setVisible(false);
        }
    }

    public void openZOrderWindows() {
        Set<NbWindow> windows = WindowManagerImpl.getInstance().getNbWindows();

        if (windows.size() == 0) {
            getMainWindow().setVisible(true);
            return;
        }

        for (String id : ZOrderManager.getInstance().getZOrder()) {
            if ("NbMainWindow".equals(id)) {
                getMainWindow().setVisible(true);
            } else {
                // We can assume window has not been created yet!
                for (NbWindow window : windows) {
                    NbWindowImpl win = (NbWindowImpl) window;
                    if (win.getName().equals(id) && win.isVisible()) {
                        Window nbWindow = (Window)NbWindowTracker.getInstance().toWindow(win);
                        nbWindow.setVisible(true);
                    }
                }
            }
        }
    }

    public Set<Component> getNbWindowFrames() {
        Set<Component> s = new HashSet<Component>();
        for (NbWindowComponent w : win2Frame.values()) {
            s.add((Component)w);
        }

        return s;
    }
    
    public void removeNbWindowImpl(NbWindowImpl window) {
        win2Frame.remove(window);
        desktopMap.remove(window);
        currentSplitRootMap.remove(window);
        fakeSplitMap.remove(window);
        slidingModeViewsMap.remove(window);
    }
}

