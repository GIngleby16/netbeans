/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class ZOrderPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private DefaultListModel lm = new DefaultListModel();
    
    public void populate() {
        try {
            ClassLoader systemClassLoader = Lookup.getDefault().lookup(ClassLoader.class); 
            Class zClass = systemClassLoader.loadClass("org.netbeans.core.windows.view.dnd.ZOrderManager");
            Field zField = zClass.getDeclaredField("instance");
            zField.setAccessible(true);
            Object zObject = zField.get(null);      
            
            Field zoField = zClass.getDeclaredField("zOrder");
            zoField.setAccessible(true);
            Object zoObject = zoField.get(zObject);      

            List<WeakReference<RootPaneContainer>> list = (List<WeakReference<RootPaneContainer>>)zoObject;
            lm.clear();
            
            for(WeakReference<RootPaneContainer> ref: list) {
                RootPaneContainer rpc = ref.get();
                if(rpc != null) {
                    Window w = (Window)SwingUtilities.getAncestorOfClass(Window.class, rpc.getRootPane());
                    lm.addElement(w.getName());
                }
            }
                    
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public ZOrderPanel() {
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
        return "ZOrder";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
