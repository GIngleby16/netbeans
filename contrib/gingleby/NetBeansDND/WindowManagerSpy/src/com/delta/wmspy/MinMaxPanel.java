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
import java.util.List;
import java.util.WeakHashMap;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.openide.windows.WindowManager;
import org.openide.windows.NbWindow;

/**
 *
 * @author D9255343
 */
@org.openide.util.lookup.ServiceProvider(service=WMSpyPanel.class)
public class MinMaxPanel implements WMSpyPanel {
    private JPanel panel = new JPanel();
    private DefaultListModel lm = new DefaultListModel();
    
    public void populate() {
        try {
            lm.clear();
            
            Class wmClass = WindowManager.getDefault().getClass();
            Field centralField = wmClass.getDeclaredField("central");
            centralField.setAccessible(true);
            Object centralObject = centralField.get(WindowManager.getDefault());             
            Class centralClass = centralObject.getClass();
            Field modelField = centralClass.getDeclaredField("model");
            modelField.setAccessible(true);
            Object modelObject = modelField.get(centralObject);
            Class modelClass = modelObject.getClass();

            {
                Field maxDSField = modelClass.getDeclaredField("maximizedDockingStatus");
                maxDSField.setAccessible(true);
                Object maxDSObject = maxDSField.get(modelObject);
                HashMap<NbWindow, Object> map = (HashMap<NbWindow, Object>)maxDSObject;
                for(NbWindow win: map.keySet()) {
                    lm.addElement("Window: " + (win == null?"NbMainWindow":win.getName()));
                    
                    Object dataStoreObject = map.get(win);
                    Class dataStoreClass = dataStoreObject.getClass();
                    
                    Class maxDSClass = dataStoreObject.getClass();            
                    Field dockedField = dataStoreClass.getDeclaredField("docked");
                    dockedField.setAccessible(true);
                    Object dockedObject = dockedField.get(dataStoreObject);            
                    List<String> docked = (List<String>)dockedObject;

                    Field slidedField = dataStoreClass.getDeclaredField("slided");
                    slidedField.setAccessible(true);
                    Object slidedObject = slidedField.get(dataStoreObject);            
                    List<String> slided = (List<String>)slidedObject;

                    Field markedField = dataStoreClass.getDeclaredField("marked");
                    markedField.setAccessible(true);
                    Object markedObject = markedField.get(dataStoreObject);            
                    boolean marked = (boolean)markedObject;


                    lm.addElement("maximizedDockingStatus:");
                    for(String id: docked)
                        lm.addElement("    " + id + " (docked)");
                    for(String id: slided)
                        lm.addElement("    " + id + " (slided)");
                    lm.addElement("    " + (marked?"marked":"not marked"));
                }
            }
            
            lm.addElement("-");

            {
                Field defaultDSField = modelClass.getDeclaredField("defaultDockingStatus");
                defaultDSField.setAccessible(true);
                Object defaultDSObject = defaultDSField.get(modelObject);
                
                HashMap<NbWindow, Object> map = (HashMap<NbWindow, Object>)defaultDSObject;
                for(NbWindow win: map.keySet()) {
                    lm.addElement("Window: " + (win == null?"NbMainWindow":win.getName()));

                    Object dataStoreObject = map.get(win);
                    System.out.println("OBJECT=" + dataStoreObject);
                    Class dataStoreClass = dataStoreObject.getClass();
                    System.out.println("CLASS=" + dataStoreClass);

                    Field dockedField = dataStoreClass.getSuperclass().getDeclaredField("docked");
                    dockedField.setAccessible(true);
                    Object dockedObject = dockedField.get(dataStoreObject);            
                    List<String> docked = (List<String>)dockedObject;

                    Field slidedField = dataStoreClass.getSuperclass().getDeclaredField("slided");
                    slidedField.setAccessible(true);
                    Object slidedObject = slidedField.get(dataStoreObject);            
                    List<String> slided = (List<String>)slidedObject;

                    Field markedField = dataStoreClass.getSuperclass().getDeclaredField("marked");
                    markedField.setAccessible(true);
                    Object markedObject = markedField.get(dataStoreObject);            
                    boolean marked = (boolean)markedObject;

                    lm.addElement("defaultDockingStatus:");
                    for(String id: docked)
                        lm.addElement("    " + id + " (docked)");
                    for(String id: slided)
                        lm.addElement("    " + id + " (slided)");

                    lm.addElement("    " + (marked?"marked":"not marked"));
                }                
            }
            
            lm.addElement("-");
            
            // modes sub model...

            Field modesSubModelField = modelClass.getDeclaredField("modesSubModel");
            modesSubModelField.setAccessible(true);
            Object modesSubModelObject = modesSubModelField.get(modelObject);
            
            Field editorsField = modesSubModelObject.getClass().getDeclaredField("editorMaximizedMode");
            editorsField.setAccessible(true);
            Object editorsObject = editorsField.get(modesSubModelObject);
            
            WeakHashMap<Object,Object> editors = (WeakHashMap<Object,Object>)editorsObject;
            for(Object key: editors.keySet())
                lm.addElement("editorMaximizedMode=" + (key==null?"NbMainWindow":key) + " -> " + editors.get(key));

        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    public MinMaxPanel() {
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
        return "Min/Max";
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }
}
