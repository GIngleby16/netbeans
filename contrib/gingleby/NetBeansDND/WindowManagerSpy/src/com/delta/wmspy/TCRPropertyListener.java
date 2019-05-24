/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.openide.windows.TopComponent;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class TCRPropertyListener implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private DefaultListModel lm = new DefaultListModel();
    
    public TCRPropertyListener() {
        final JList list = new JList(lm);
        
        panel.setLayout(new BorderLayout(0,0));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        
        TopComponent.getRegistry().addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                lm.addElement(evt.toString());
            }
        });
    }

    
    @Override
    public String getTitle() {
        return "TCR Listener";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
