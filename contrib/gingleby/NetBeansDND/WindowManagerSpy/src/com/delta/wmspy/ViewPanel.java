/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindow;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class ViewPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private JTextArea textArea = new JTextArea();
    
    public void populate() {
        try {
            Class wmClass = WindowManager.getDefault().getClass();
            Field centralField = wmClass.getDeclaredField("central");
            centralField.setAccessible(true);
            Object centralObject = centralField.get(WindowManager.getDefault());             
            Class centralClass = centralObject.getClass();
            Field viewRequestorField = centralClass.getDeclaredField("viewRequestor");
            viewRequestorField.setAccessible(true);
            Object viewRequestorObject = viewRequestorField.get(centralObject);
            Field viewField = viewRequestorObject.getClass().getDeclaredField("view");
            viewField.setAccessible(true);
            Object viewObject = viewField.get(viewRequestorObject);
            Field vhField = viewObject.getClass().getDeclaredField("hierarchy");
            vhField.setAccessible(true);
            Object vhObject = vhField.get(viewObject);

            Field deskopField = vhObject.getClass().getDeclaredField("desktopMap");
            deskopField.setAccessible(true);
            Object desktopObject = deskopField.get(vhObject);
            Map<NbWindow, Desktop> map = (Map<NbWindow, Desktop>)desktopObject;
            
            textArea.setText(vhObject.toString());
            textArea.append("\n\n-------------------\n\n");
            for(NbWindow win: map.keySet()) {
                textArea.append((win == null?"NbMainWindow":win.getName()) + " -> " + map.get(win) + "\n");
            }
            textArea.append("" + map.size());
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public ViewPanel() {
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                populate();
            }            
        });
        
        panel.setLayout(new BorderLayout(0,0));
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        panel.add(refreshButton, BorderLayout.SOUTH);        
    }

    
    @Override
    public String getTitle() {
        return "ViewHierarchy";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
