package com.delta.wmspy;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindow;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class WindowsPanel implements WMSpyPanel {
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
            Field modeMapField = modesSubModelObject.getClass().getDeclaredField("mode2window");
            modeMapField.setAccessible(true);
            Object editorsObject = modeMapField.get(modesSubModelObject);
            
            Field activeField = modelClass.getDeclaredField("lastActiveMode");
            activeField.setAccessible(true);
            Object activeObject = activeField.get(modelObject);

            
            
            Map<Mode, NbWindow> modesMap = (Map<Mode, NbWindow>)editorsObject;
            WeakHashMap<NbWindow, Reference<Mode>> lastActiveModeMap = (WeakHashMap<NbWindow, Reference<Mode>>)activeObject;
            lm.clear();
            
            for(Mode mode: modesMap.keySet()) {
                NbWindow window = modesMap.get(mode);
                lm.addElement(mode.getName() + " -> " + (window==null?"NbMainWindow":window.getName()));
            }
            lm.addElement("--------------");
            for(NbWindow window: lastActiveModeMap.keySet()) {
                Reference<Mode> mRef = lastActiveModeMap.get(window);
                lm.addElement((window == null ? "NbMainWindow":window.getName()) + " -> " + mRef.get());
            }
            
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public WindowsPanel() {
        JList list = new JList(lm);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                populate();
            }            
        });
        
        panel.setLayout(new BorderLayout(0,0));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);        
    }

    
    @Override
    public String getTitle() {
        return "ModesSubModel:mode2NbWindow";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
