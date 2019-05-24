/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class ModesSubModelPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private DefaultListModel lm = new DefaultListModel();
    
    public void populate() {
        try {
            Class wmClass = WindowManager.getDefault().getClass();
            Field centralField = wmClass.getDeclaredField("central");
            centralField.setAccessible(true);
            Object centralObject = centralField.get(WindowManager.getDefault());             
            Class centralClass = centralObject.getClass();
            Field modelField = centralClass.getDeclaredField("model");
            modelField.setAccessible(true);
            Object modelObject = modelField.get(centralObject);
            Class modelClass = modelObject.getClass();
            Field modesSubModelField = modelClass.getDeclaredField("modesSubModel");
            modesSubModelField.setAccessible(true);
            Object modesSubModelObject = modesSubModelField.get(modelObject);
            Field modesField = modesSubModelObject.getClass().getDeclaredField("modes");
            modesField.setAccessible(true);
            Object modesObject = modesField.get(modesSubModelObject);
            Set<Mode> modes = (Set<Mode>)modesObject;

            List<Mode> myModes = new ArrayList<Mode>(modes);

            lm.clear();
            Collections.sort(myModes, new Comparator<Mode>() {
                @Override
                public int compare(Mode o1, Mode o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            for(Mode m: myModes) {
                Method kindMethod = m.getClass().getDeclaredMethod("getKind", null);
                Object kind = kindMethod.invoke(m, null);                
                Method stateMethod = m.getClass().getDeclaredMethod("getState", null);
                Object state = stateMethod.invoke(m, null);                
                Method permMethod = m.getClass().getDeclaredMethod("isPermanent", null);
                Object perm = permMethod.invoke(m, null);                
                lm.addElement("" + m.getName() + "   @" + System.identityHashCode(m) + " / " + getKind(kind) + " / " + getState(state) + " / " + perm);
           
                
                TopComponent[] tcs = m.getTopComponents();
                for(TopComponent tc: tcs) {
                    lm.addElement("  TopComponent: " + tc.getName() + "  " + (tc.isShowing()?"(showing)":"") + "   " + (tc.isOpened()?"(open)":"(closed)") + "  " + tc.toString());
                }
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public ModesSubModelPanel() {
        final JList list = new JList(lm);
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                populate();
                list.revalidate();
            }            
        });
        
        panel.setLayout(new BorderLayout(0,0));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);        
    }

    
    @Override
    public String getTitle() {
        return "ModesSubModel:modes";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
    private static String getKind(Object kind) {
        Integer k = (Integer)kind;
        if(k.intValue() == 1)
            return "EDITOR";
        if(k.intValue() == 0)
            return "VIEW";
        if(k.intValue() == 2)
            return "SLIDING";
        return "UNKNOWN";        
    }
    
    private static String getState(Object state) {
        Integer k = (Integer)state;
        if(k.intValue() == 0)
            return "JOINED";
        if(k.intValue() == 1)
            return "FLOATING";
        if(k.intValue() == 2)
            return "NBWINDOW";
        return "UNKNOWN";        
    }
}
