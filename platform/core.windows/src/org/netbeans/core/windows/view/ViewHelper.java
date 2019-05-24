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


import org.netbeans.core.windows.Debug;
import org.netbeans.core.windows.ModeStructureSnapshot;
import org.netbeans.core.windows.WindowSystemSnapshot;

import java.util.*;
import org.netbeans.core.windows.NbWindowImpl;
import org.netbeans.core.windows.NbWindowStructureSnapshot.NbWindowSnapshot;
import org.netbeans.core.windows.ModeStructureSnapshot.ModeSnapshot;
import org.netbeans.core.windows.ModeStructureSnapshot.WindowModeStructureSnapshot;
import org.netbeans.core.windows.model.ModelElement;
import org.netbeans.core.windows.view.NbWindowStructureAccessorImpl.NbWindowAccessorImpl;



/**
 * This class converts snapshot to accessor structure, which is a 'model'
 * of view (GUI) structure window system has to display to user.
 * It reflects the specific view implementation (the difference from snapshot)
 * e.g. merging of split panes with the same orientation, accomodates for split
 * panes with only one visible child etc.
 *
 * @author  Peter Zavadsky
 */
final class ViewHelper {
    
    /** Debugging flag. */
    private static final boolean DEBUG = Debug.isLoggable(ViewHelper.class);
    
    
    /** Creates a new instance of ViewHelper */
    private ViewHelper() {
    }
    
    // TODO gwi: createWindowSystemAccessor (convert windowSystemSnapshot to accessor model)
    public static WindowSystemAccessor createWindowSystemAccessor(
        WindowSystemSnapshot wss
    ) {
        // PENDING When hiding is null.
        if(wss == null) {
            return null;
        }
        
        WindowSystemAccessorImpl wsa = new WindowSystemAccessorImpl();

        ModeStructureAccessorImpl msa = createModeStructureAccessor(wss.getModeStructureSnapshot());
        wsa.setModeStructureAccessor(msa);

        ModeStructureSnapshot.ModeSnapshot activeSnapshot = wss.getActiveModeSnapshot();
        wsa.setActiveModeAccessor(activeSnapshot == null ? null : msa.findModeAccessor(activeSnapshot.getName()));
        
//        wsa.setAuxWindowStructureAccessor(createAuxWindowStructureAccessor(wss.getAuxWindowSnapshot()));
        
        Map<NbWindowSnapshot, ModeSnapshot> maximizedSnapshot = wss.getMaximizedModeSnapshot();
        Map<NbWindowAccessor, ModeAccessor> maximizedAccessors = new HashMap<NbWindowAccessor, ModeAccessor>();
        for(NbWindowSnapshot aws: maximizedSnapshot.keySet()) {
            ModeSnapshot ms = maximizedSnapshot.get(aws);
            ModeAccessor ma = ms == null ? null : msa.findModeAccessor(ms.getName());
            maximizedAccessors.put(new NbWindowAccessorImpl(aws), ma);
        }
        wsa.setMaximizedModeAccessor(maximizedAccessors);

        wsa.setMainWindowBoundsJoined(wss.getMainWindowBoundsJoined());
        wsa.setMainWindowBoundsSeparated(wss.getMainWindowBoundsSeparated());
        wsa.setEditorAreaBounds(wss.getEditorAreaBounds());
        wsa.setEditorAreaState(wss.getEditorAreaState());
        wsa.setEditorAreaFrameState(wss.getEditorAreaFrameState());
        wsa.setMainWindowFrameStateJoined(wss.getMainWindowFrameStateJoined());
        wsa.setMainWindowFrameStateSeparated(wss.getMainWindowFrameStateSeparated());
        wsa.setToolbarConfigurationName(wss.getToolbarConfigurationName());
        return wsa;
    }

    //TODO gwi: modified to include NBWindowSplitRoots
    private static ModeStructureAccessorImpl createModeStructureAccessor(ModeStructureSnapshot mss) {
        Map<NbWindowSnapshot, WindowModeStructureSnapshot> windows = mss.getWindowModeStructures();
        Map<NbWindowAccessor, WindowModeStructureAccessor> windowAccessors = new HashMap<NbWindowAccessor, WindowModeStructureAccessor>();

        Set<ModeAccessor> separateModes = createSeparateModeAccessors(mss.getSeparateModeSnapshots());        
        
        // null window will be the main-window, all others are nbwindows
        for(NbWindowSnapshot windowSnapshot: windows.keySet()) {
            WindowModeStructureSnapshot wmss = windows.get(windowSnapshot);            
            WindowModeStructureAccessor wmsa = new WindowModeStructureAccessorImpl(
                    createVisibleAccessor(wmss.getSplitRootSnapshot()), 
                    createSlidingModeAccessors(windowSnapshot, wmss.getSlidingModeSnapshots()));            
            windowAccessors.put(new NbWindowAccessorImpl(windowSnapshot), wmsa);  

//            StringBuffer buffer = new StringBuffer();
//            buffer.append("ViewHelper.createModeStructureAccessor\n");
//            buffer.append(windowSnapshot.getAuxWindow() == null?"NbMainWindow":windowSnapshot.getAuxWindow().getName());
//            buffer.append("\n");
//            for(SlidingAccessor i: wmsa.getSlidingModeAccessors())
//                buffer.append(i.getName() +" " + i.getSide() + " has " + i.getOpenedTopComponents().length + "\n");
//            JOptionPane.showMessageDialog(null, buffer.toString());
        }        
        return new ModeStructureAccessorImpl(windowAccessors, separateModes);
        
        // OLD CODE TO BE REMOVED...
        
//        Set<ModeAccessor> separateModes = createSeparateModeAccessors(mss.getSeparateModeSnapshots());        
//        ModeStructureAccessorImpl msa = new ModeStructureAccessorImpl(windowModeStructureAccessors, separateModes);
//        return msa;
//               
//        Map<AuxWindowAccessor, WindowModeStructureAccessor> windowAccessors = new HashMap<AuxWindowAccessor, WindowModeStructureAccessor>();
        
//        ElementAccessor splitRoot = createVisibleAccessor(mss.getSplitRootSnapshot());
        
        // create element accessor for each aux window split root!
//        Map<AuxWindowSnapshot, ElementSnapshot> auxRoots = mss.getAuxSplitRootSnapshot();
//        Map<AuxWindowSnapshot, ElementAccessor> auxSplitRoot = new HashMap<AuxWindowSnapshot, ElementAccessor>();
//
//        for(AuxWindowSnapshot snapshot: auxRoots.keySet()) {
//            auxSplitRoot.put(snapshot, createVisibleAccessor(auxRoots.get(snapshot)));
//        } 
//        
//        Set<SlidingAccessor> slidingModes = createSlidingModeAccessors(mss.getSlidingModeSnapshots());
//        
//        ModeStructureAccessorImpl msa =  new ModeStructureAccessorImpl(splitRoot, auxSplitRoot, separateModes, slidingModes);
//        return msa;
    }
    
    private static Set<ModeAccessor> createSeparateModeAccessors(ModeStructureSnapshot.ModeSnapshot[] separateModeSnapshots) {
        Set<ModeAccessor> s = new HashSet<ModeAccessor>();
        for(int i = 0; i < separateModeSnapshots.length; i++) {
            ModeStructureSnapshot.ModeSnapshot snapshot = separateModeSnapshots[i];
            if(snapshot.isVisibleSeparate()) {
                s.add(new ModeStructureAccessorImpl.ModeAccessorImpl(
                    snapshot.getOriginator(),
                    snapshot));
            }
        }
        
        return s;
    }
    
    private static Set<SlidingAccessor> createSlidingModeAccessors(NbWindowSnapshot windowSnapshot, ModeStructureSnapshot.SlidingModeSnapshot[] slidingModeSnapshots) {
        Set<SlidingAccessor> s = new HashSet<SlidingAccessor>();
        ModeStructureSnapshot.SlidingModeSnapshot snapshot; 
        for(int i = 0; i < slidingModeSnapshots.length; i++) {
            snapshot = slidingModeSnapshots[i];
            s.add(new ModeStructureAccessorImpl.SlidingAccessorImpl(
                windowSnapshot,
                snapshot.getOriginator(),
                snapshot,
                snapshot.getSide(),
                snapshot.getSlideInSizes()
            ));
        }
        
        return s;
    }

    /** */
    private static ElementAccessor createVisibleAccessor(ModeStructureSnapshot.ElementSnapshot snapshot) {
        if(snapshot == null) {
            return null;
        }

        if(snapshot instanceof ModeStructureSnapshot.EditorSnapshot) { // Is always visible.
            ModeStructureSnapshot.EditorSnapshot editorSnapshot = (ModeStructureSnapshot.EditorSnapshot)snapshot;
            return new ModeStructureAccessorImpl.EditorAccessorImpl(
                editorSnapshot.getOriginator(),
                editorSnapshot,
                createVisibleAccessor(editorSnapshot.getEditorAreaSnapshot()),
                editorSnapshot.getResizeWeight());
        }
        
        if(snapshot.isVisibleInSplit()) {
            if(snapshot instanceof ModeStructureSnapshot.SplitSnapshot) {
                ModeStructureSnapshot.SplitSnapshot splitSnapshot = (ModeStructureSnapshot.SplitSnapshot)snapshot;
                return createSplitAccessor(splitSnapshot);
            } else if(snapshot instanceof ModeStructureSnapshot.ModeSnapshot) {
                ModeStructureSnapshot.ModeSnapshot modeSnapshot = (ModeStructureSnapshot.ModeSnapshot)snapshot;
                return new ModeStructureAccessorImpl.ModeAccessorImpl(
                    modeSnapshot.getOriginator(),
                    modeSnapshot);
            }
        } else {
            if(snapshot instanceof ModeStructureSnapshot.SplitSnapshot) {
                //the split has only one visible child, so create an accessor for this child
                ModeStructureSnapshot.SplitSnapshot splitSnapshot = (ModeStructureSnapshot.SplitSnapshot)snapshot;
                for(Iterator it = splitSnapshot.getChildSnapshots().iterator(); it.hasNext(); ) {
                    ModeStructureSnapshot.ElementSnapshot child = (ModeStructureSnapshot.ElementSnapshot)it.next();
                    if(child.hasVisibleDescendant()) {
                        return createVisibleAccessor(child);
                    }
                }
            }
        }
        
        return null;
    }
    
    private static ElementAccessor createSplitAccessor(ModeStructureSnapshot.SplitSnapshot splitSnapshot) {
        List visibleChildren = splitSnapshot.getVisibleChildSnapshots();
        
        ArrayList<ElementAccessor> children = new ArrayList<ElementAccessor>( visibleChildren.size() );
        ArrayList<Double> weights = new ArrayList<Double>( visibleChildren.size() );
        
        int index = 0;
        for( Iterator i=visibleChildren.iterator(); i.hasNext(); index++ ) {
            ModeStructureSnapshot.ElementSnapshot child = (ModeStructureSnapshot.ElementSnapshot)i.next();
            ElementAccessor childAccessor = createVisibleAccessor( child );
            double weight = splitSnapshot.getChildSnapshotSplitWeight( child );
            //double weight = getSplitWeight( splitSnapshot, child );
            if( childAccessor instanceof SplitAccessor 
                && ((SplitAccessor)childAccessor).getOrientation() == splitSnapshot.getOrientation() ) {
                //merge nested splits with the same orientation into one big split
                //e.g. (A | B | C) where B is a nested split (X | Y | Z) 
                //will be merged into -> (A | X | Y | Z | C)
                SplitAccessor subSplit = (SplitAccessor)childAccessor;
                ElementAccessor[] childrenToMerge = subSplit.getChildren();
                double[] weightsToMerge = subSplit.getSplitWeights();
                for( int j=0; j<childrenToMerge.length; j++ ) {
                    children.add( childrenToMerge[j] );
                    weights.add( Double.valueOf( weightsToMerge[j] * weight ) );
                }
            } else {
                children.add( childAccessor );
                weights.add( Double.valueOf( weight ) );
            }
        }
        
        ElementAccessor[] childrenAccessors = new ElementAccessor[children.size()];
        double[] splitWeights = new double[children.size()];
        for( int i=0; i<children.size(); i++ ) {
            ElementAccessor ea = (ElementAccessor)children.get( i );
            Double weight = (Double)weights.get( i );
            childrenAccessors[i] = ea;
            splitWeights[i] = weight.doubleValue();
        }
        
        return new ModeStructureAccessorImpl.SplitAccessorImpl( 
                splitSnapshot.getOriginator(), 
                splitSnapshot, 
                splitSnapshot.getOrientation(), 
                splitWeights,
                childrenAccessors, 
                splitSnapshot.getResizeWeight());
    }
    
    /**
     * Update the model with new split weights when user moved a splitter bar or
     * when the main window has been resized.
     *
     * @param splitAccessor The split parent that has been modified.
     * @param children Split children.
     * @param splitWeights New split weights.
     * @param controllerHandler
     *
     */
    public static void setSplitWeights(NbWindowImpl window, SplitAccessor splitAccessor,
        ElementAccessor[] children, double[] splitWeights, ControllerHandler controllerHandler) {
        
        ModeStructureSnapshot.SplitSnapshot splitSnapshot = (ModeStructureSnapshot.SplitSnapshot)splitAccessor.getSnapshot();

        if(splitSnapshot == null) {
            return;
        }
        
        ModelElement[] elements = new ModelElement[ children.length ];
        for( int i=0; i<children.length; i++ ) {
            //the split child may belong to another splitter that has only one visible child
            //so we must set new split weight for its actual parent
            //e.g. the 'S' = (A | X | C) and X's parent is B -> (X | Y | Z) but Y and Z are not visible
            //so instead of setting split weight of X in splitter B we must set split weight of B in 'S'
            //see also method createVisibleAccessor()
            ModeStructureSnapshot.ElementSnapshot snapshot = findVisibleSplitSnapshot( children[i].getSnapshot() );
            elements[ i ] = snapshot.getOriginator();
            //if this splitter has been merged with a nested splitter with the same orientation
            //then the nested split child split weight must be corrected according to its parent
            splitWeights[ i ] = correctNestedSplitWeight( snapshot.getParent(), splitWeights[i] );
        }

        controllerHandler.userChangedSplit( window, elements, splitWeights );
    }
    
    /**
     * Recalculate the given child split weight if the given split is nested in a parent
     * split with the same orientation.
     * e.g. If this split X is (A | B) with weights 0.5 and 0.5 and is nested in a parent
     * split (X | Y | Z) with split weights 0.5, 0.25 and 0.25 then the accessors
     * look like this (A | B | Y | Z) with split weights 0.25, 0.25, 0.25, and 0.25
     * If the new split weight for A being corrected is 0.3 then the corrected value will be (0.3 / 0.5)
     *
     * Another problems is that the parent split may have a different orientation
     * but contains one visible child only (i.e. does not show in the split hierarchy).
     * In this case the split weight must be corrected in one level up in the split hierarchy.
     *
     * @return New split weight corrected as described above or the original value if the
     * split does not have a parent split with the same orientation.
     */
    private static double correctNestedSplitWeight( ModeStructureSnapshot.SplitSnapshot split, double splitWeight ) {
        int nestedSplitOrientation = split.getOrientation();
        ModeStructureSnapshot.SplitSnapshot parentSplit = split.getParent();
        while( null != parentSplit && !parentSplit.isVisibleInSplit() ) { //issue #59103
            split = parentSplit;
            parentSplit = parentSplit.getParent();
        }
        if( null != parentSplit && parentSplit.getOrientation() == nestedSplitOrientation ) {
            double parentSplitWeight = parentSplit.getChildSnapshotSplitWeight( split );
            if( parentSplit.getVisibleChildSnapshots().size() > 1 && parentSplitWeight > 0.0 )
                splitWeight /= parentSplitWeight;
            
            return correctNestedSplitWeight( parentSplit, splitWeight );
        }
        return splitWeight;
    }
    
    /**
     * Find the topmost split parent of the given snapshot that is visible in split hierarchy 
     * (i.e. has at least two visible children).
     */
    private static ModeStructureSnapshot.ElementSnapshot findVisibleSplitSnapshot( ModeStructureSnapshot.ElementSnapshot snapshot ) {
        ModeStructureSnapshot.SplitSnapshot parent = snapshot.getParent();
        if( null != parent ) {
            List visibleChildren = parent.getVisibleChildSnapshots();
            if( visibleChildren.size() == 1 ) {
                return findVisibleSplitSnapshot( parent );
            }
        }
        return snapshot;
    }

    private static void debugLog(String message) {
        Debug.log(ViewHelper.class, message);
    }
    
    // NEW --------------------------------------------------------------------
    
//    private static AuxWindowStructureAccessorImpl createAuxWindowStructureAccessor(AuxWindowStructureSnapshot snapshot) {
//        return new AuxWindowStructureAccessorImpl(snapshot.getAuxWindowSnapshots());
//    }

}

