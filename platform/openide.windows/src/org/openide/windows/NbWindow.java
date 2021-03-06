/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.openide.windows;

import java.awt.Rectangle;

/**
 * NbWindow - a secondary display window that can arrange TopComponents
 * in splits in a similar way that NbMainWindow does.
 * 
 * @author Graeme Ingleby graeme@ingleby.net
 */
public interface NbWindow {
    public String getName();
    
    public boolean isDialog();

    public Rectangle getBounds();
    public void setBounds(Rectangle bounds);
    
    public void setVisible(boolean visible);
    public boolean isVisible();
    
    public void setTitle(String title);
    public String getTitle();
    
}
