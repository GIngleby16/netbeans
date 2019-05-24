/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.wmspy;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import org.openide.modules.ModuleInstall;
import org.openide.util.Lookup;
import org.openide.windows.WindowManager;

public class Installer extends ModuleInstall {

    @Override
    public void restored() {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                JButton gcButton = new JButton("Run Garbage Collector");
                gcButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.out.println(" -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE -- GARBAGE --");
                        System.gc();
                    }
                });
                
                JTabbedPane tabs = new JTabbedPane();
                for (WMSpyPanel descriptor : Lookup.getDefault().lookupAll(WMSpyPanel.class)) {
                    tabs.addTab(descriptor.getTitle(), descriptor.getComponent());
                }        
                JFrame win = new JFrame("Window Manager Spy");
                win.setBounds(50, 50, 640, 480);
                win.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                win.getContentPane().add(tabs);
                win.getContentPane().add(gcButton, BorderLayout.NORTH);
                win.setVisible(true);
            }            
        });
    }

}
