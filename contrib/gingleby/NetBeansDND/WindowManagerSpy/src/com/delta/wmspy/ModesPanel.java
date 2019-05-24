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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
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
public class ModesPanel implements WMSpyPanel {
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
            Field mapField = modelClass.getDeclaredField("mode2model");
            mapField.setAccessible(true);
            Object mapObject = mapField.get(modelObject);
            Map<Object, Object> map = (Map<Object, Object>)mapObject;
            lm.clear();
            ArrayList<Mode> modes = new ArrayList<Mode>();
            for(Object o: map.keySet()) {
                modes.add((Mode)o);
            }
            Collections.sort(modes, new Comparator<Mode>() {
                @Override
                public int compare(Mode o1, Mode o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
            for(Mode m: modes) {
                System.out.println(m);
                lm.addElement("" + m.getName() + "   @" + System.identityHashCode(m));
                
                TopComponent[] tcs = m.getTopComponents();
                for(TopComponent tc: tcs) {
                    lm.addElement("  TopComponent: " + tc.getName() + "  " + (tc.isShowing()?"(visible)":"(hidden)"));
                }
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public ModesPanel() {
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
        return "Modes:mode2Model";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
