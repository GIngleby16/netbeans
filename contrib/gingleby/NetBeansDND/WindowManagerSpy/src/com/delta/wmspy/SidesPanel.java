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
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import org.openide.windows.Mode;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindow;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class SidesPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
//    private DefaultListModel lm = new DefaultListModel();
    

    private JTabbedPane tabs = new JTabbedPane();

    
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
            Field smField = modesSubModelObject.getClass().getDeclaredField("slidingModes2Sides");
            smField.setAccessible(true);
            Object smObject = smField.get(modesSubModelObject);
            HashMap<NbWindow, HashMap<Mode, String>> sidesMap = (HashMap<NbWindow, HashMap<Mode, String>>)smObject;

            Field smField2 = modesSubModelObject.getClass().getDeclaredField("slidingSides2Modes");
            smField2.setAccessible(true);
            Object smObject2 = smField2.get(modesSubModelObject);
            HashMap<NbWindow, HashMap<String, Mode>> sidesMap2 = (HashMap<NbWindow, HashMap<String, Mode>>)smObject2;
            
            

        
            tabs.removeAll();
            
            JTextArea textArea = new JTextArea();
            JScrollPane sp = new JScrollPane(textArea);
            for(NbWindow window: sidesMap.keySet()) {
                textArea.append("Window: " + (window==null?"NbMainWindow":window.getName()) + "\n");
                HashMap<Mode, String> map = sidesMap.get(window);
                for(Mode mode: sidesMap.get(window).keySet()) {
                    textArea.append("  " + mode.getName() + "->" + map.get(mode) + "\n");
                }
            }
            tabs.addTab("slidingModes2Sides", sp);
            
            for(NbWindow win: sidesMap2.keySet()) {
                HashMap<String, Mode> test = sidesMap2.get(win);
                
                JTextArea textArea2 = new JTextArea();
                for(String side: test.keySet()) {
                    textArea2.append(side + " -> " + test.get(side).getName() + "\n");
                }
                JScrollPane sp2 = new JScrollPane(textArea2);
                if(win == null) {
                    tabs.addTab("NbMainWindow", sp2);
                } else {
                    tabs.addTab(win.getName(), sp2);
                }
            }
            
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public SidesPanel() {
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                populate();
            }            
        });
        
        panel.setLayout(new BorderLayout(0,0));
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);        
    }

    
    @Override
    public String getTitle() {
        return "ModesSubModel:sides";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
