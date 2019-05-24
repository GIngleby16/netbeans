/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.delta.test;

import java.io.IOException;
import javax.swing.JEditorPane;
import javax.swing.text.StyledDocument;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.cookies.EditorCookie;
import org.openide.text.Line;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(
        dtd = "-//com.delta.test//rss//EN",
        autostore = false
)
@TopComponent.Description(
        preferredID = "rssTopComponent",
        iconBase = "com/delta/test/icon_rss.gif",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(mode = "editor", openAtStartup = false)
@ActionID(category = "Window", id = "com.delta.test.rssTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_rssAction",
        preferredID = "rssTopComponent"
)
@Messages({
    "CTL_rssAction=rss",
    "CTL_rssTopComponent=rss Window",
    "HINT_rssTopComponent=This is a rss window"
})
public final class rssTopComponent extends TopComponent {
    InstanceContent ic = new InstanceContent();

    public rssTopComponent() {
        initComponents();
        setName(Bundle.CTL_rssTopComponent());
        setToolTipText(Bundle.HINT_rssTopComponent());
        associateLookup(new AbstractLookup(ic));
        
        ic.add(new EditorCookie() {
            @Override
            public void open() {
            }

            @Override
            public boolean close() {
                return true;
            }

            @Override
            public RequestProcessor.Task prepareDocument() {
                return null;
            }

            @Override
            public StyledDocument openDocument() throws IOException {
                return null;            
            }

            @Override
            public StyledDocument getDocument() {
                return null;
            }

            @Override
            public void saveDocument() throws IOException {
            }

            @Override
            public boolean isModified() {
                return false;
            }

            @Override
            public JEditorPane[] getOpenedPanes() {
                return null;
            }

            @Override
            public Line.Set getLineSet() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    @Override
    public void componentOpened() {
        // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }
}
