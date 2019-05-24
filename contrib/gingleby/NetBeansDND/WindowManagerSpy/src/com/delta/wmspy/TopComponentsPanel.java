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
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.TopComponent.Registry;
import org.openide.windows.WindowManager;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class TopComponentsPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();    
    private DefaultListModel lm = new DefaultListModel();
    private JList list  = new JList(lm);
    
    private void populate() {
        lm.clear();
        Registry reg = TopComponent.getRegistry();        
        for(TopComponent tc: reg.getOpened()) {
            lm.addElement(tc);
        }
    }

    class MyCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            
            TopComponent tc = (TopComponent)value;
            StringBuilder sb = new StringBuilder();
            sb.append("" + tc.getName());
            Mode m = WindowManager.getDefault().findMode(tc);
            if(m != null) {
                sb.append(":");
                sb.append(m.getName());
            }
            if(tc.equals(TopComponent.getRegistry().getActivated()))
                sb.append("  (ACTIVATED)");
            if(tc.isOpened())
                sb.append("  (OPEN)");
            else
                sb.append("  (CLOSED)");
            
            
            sb.append("\n");
            return super.getListCellRendererComponent(list, sb.toString(), index, isSelected, cellHasFocus); 
        }
        
    }

    public TopComponentsPanel() {
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
                                TopComponent tc = (TopComponent)lm.get(index);
                                tc.open();
                                list.repaint();
                            }
                        });
                        JMenuItem closeItem = new JMenuItem("Close");
                        closeItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                TopComponent tc = (TopComponent)lm.get(index);
                                tc.close();
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
        JButton button = new JButton("Refresh");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                populate();
            }
        });
        panel.setLayout(new BorderLayout(0,0));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(button, BorderLayout.SOUTH);
        
    }    
    

    @Override
    public String getTitle() {
        return "Top Component Registry";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
    
}
