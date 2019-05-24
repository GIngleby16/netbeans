/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Map;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import org.openide.windows.TopComponentGroup;
import org.openide.windows.WindowManager;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class GroupsPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private DefaultListModel lm = new DefaultListModel();
    
    private class Group {
        Object g;
        Object gm;
        
        public Group(Object g, Object gm) {
            this.g = g;
            this.gm = gm;
        }
        
        public TopComponentGroup getTopComponentGroup() {
            return (TopComponentGroup)g;
        }
        
        public String getName() {
            try {
                Field nameField = gm.getClass().getDeclaredField("name");
                nameField.setAccessible(true);             
                return (String)nameField.get(gm);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        }
    }
    
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
            Field groupsField = modelClass.getDeclaredField("group2model");
            groupsField.setAccessible(true);
            Object groupsObject = groupsField.get(modelObject);
            Map<Object, Object> groupSet = (Map<Object, Object>)groupsObject;   
            lm.clear();
            for(Object m: groupSet.keySet()) {
                // m == the group
                Object mg = groupSet.get(m); // mg = the group model!
                Group g = new Group(m, mg);
                lm.addElement(g);
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    class MyCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Group g = (Group)value;
            value = g.getName() + ":  " + g.getTopComponentGroup();
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus); 
        }
        
    }
    
    public GroupsPanel() {
        final JList list = new JList(lm);
        
        list.setCellRenderer(new MyCellRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                final int index = list.locationToIndex(e.getPoint());
                if(index > -1) {
                    list.setSelectedIndex(index);
                    if(e.isPopupTrigger()) {
                        JMenuItem openItem = new JMenuItem("Open");
                        openItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                Group g = (Group)lm.get(index);
                                TopComponentGroup tcg = g.getTopComponentGroup();
                                tcg.open();
                                list.repaint();
                            }
                        });
                        JMenuItem closeItem = new JMenuItem("Close");
                        closeItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                Group g = (Group)lm.get(index);
                                TopComponentGroup tcg = g.getTopComponentGroup();
                                tcg.close();
                                list.repaint();
                            }
                        });
                        JPopupMenu popup = new JPopupMenu();
                        popup.add(openItem);
                        popup.add(closeItem);
                        popup.show(list, e.getX(), e.getY());
                    }
                }
            }            
        });
        
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
        return "Groups";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
