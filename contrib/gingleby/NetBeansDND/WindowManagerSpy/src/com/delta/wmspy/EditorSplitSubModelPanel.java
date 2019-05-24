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
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindow;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class EditorSplitSubModelPanel implements WMSpyPanel {
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
            Field editorsField = modesSubModelObject.getClass().getDeclaredField("editorSplitSubModel");
            editorsField.setAccessible(true);
            Object editorsObject = editorsField.get(modesSubModelObject);
            Map<NbWindow, Object> editorMap = (Map<NbWindow, Object>)editorsObject;

            tabs.removeAll();
            
            for(NbWindow win: editorMap.keySet()) {
                JTextArea textArea = new JTextArea();
                textArea.setText(editorMap.get(win).toString());
                JScrollPane sp = new JScrollPane(textArea);
                if(win == null) {
                    tabs.addTab("NbMainWindow", sp);
                } else {
                    tabs.addTab(win.getName(), sp);
                }
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public EditorSplitSubModelPanel() {
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
        return "ModesSubModel:editorSplitSubModel";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
