<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->
#### This project is a Fork of the Apache NetBeans repo [apache/netbeans](https://github.com/apache/netbeans)
<details>
  <summary>Original NetBeans ReadMe</summary>
        
# Apache NetBeans

Apache NetBeans is an open source development environment, tooling platform, and application framework.

### Build status
   * TravisCI:
     * [![Build Status](https://travis-ci.org/apache/netbeans.svg?branch=master)](https://travis-ci.org/apache/netbeans)
   * Apache Jenkins: 
     * Linux: [![Build Status](https://builds.apache.org/view/M-R/view/NetBeans/job/netbeans-linux/badge/icon)](https://builds.apache.org/view/M-R/view/NetBeans/job/netbeans-linux/)
     * Windows: [![Build Status](https://builds.apache.org/view/M-R/view/NetBeans/job/netbeans-windows/badge/icon)](https://builds.apache.org/view/M-R/view/NetBeans/job/netbeans-windows/)

### Requirements

  * Git
  * Ant 1.9.9 or above
  * Oracle JDK 8 or OpenJDK 8 (to build NetBeans)
  * Oracle JDK 9 or OpenJDK 9 (to run NetBeans)
  * MinGW (optional), to build Windows Launchers

**Note:** NetBeans also runs with JDK 8, although then it will not include tools for the JDK 9 Shell.

**Note:** NetBeans license violation checks are managed via the [rat-exclusions.txt](https://github.com/apache/netbeans/blob/master/nbbuild/rat-exclusions.txt) file.

### Building NetBeans

Build with the default config (See the [cluster.config](https://github.com/apache/netbeans/blob/ab66c7fdfdcbf0bde67b96ddb075c83451cdd1a6/nbbuild/cluster.properties#L19) property.)
```
$ ant
```
Build the basic project (mainly, JavaSE features):
```
$ ant -Dcluster.config=basic
```
Build the full project (including Groovy, PHP, JavaEE/JakartaEE, and JavaScript features):
```
$ ant -Dcluster.config=full
```
Build the NetBeans Platform:
```
$ ant -Dcluster.config=platform
```

**Note:** You can also use `php`, `enterprise`, etc. See the [cluster.properties](https://github.com/apache/netbeans/blob/master/nbbuild/cluster.properties) file.

#### Building Windows Launchers
Windows launchers can be build using [MinGW](http://www.mingw.org/) both on Windows and Linux.

As of [NETBEANS-1145](https://issues.apache.org/jira/browse/NETBEANS-1145), the Windows Launchers can be built adding ```do.build.windows.launchers=true``` property to the build process.
```
$ ant -Ddo.build.windows.launchers=true
```

##### Software Requirement to Build Windows Launchers on Ubuntu (16.04+):
```
sudo apt install make mingw-w64
```

### Running NetBeans

Run the build:
```
$ ant tryme
```

**Note:** Look in nbbuild/netbeans for the NetBeans installation created by the build process.

### Get In Touch

[Subscribe](mailto:users-subscribe@netbeans.apache.org) or [mail](mailto:users@netbeans.apache.org) the [users@netbeans.apache.org](mailto:users@netbeans.apache.org) list - Ask questions, find answers, and also help other users.

[Subscribe](mailto:dev-subscribe@netbeans.apache.org) or [mail](mailto:dev@netbeans.apache.org) the [dev@netbeans.apache.org](mailto:dev@netbeans.apache.org) list - Join development discussions, propose new ideas and connect with contributors.

### Download

Developer builds can be downloaded: https://builds.apache.org/job/netbeans-linux.

Convenience binary of released source artifacts: https://netbeans.apache.org/download/index.html.

### Reporting Bugs

Bugs should be reported to https://issues.apache.org/jira/projects/NETBEANS/issues/

### Full History

The origins of the code in this repository are older than its Apache existence.
As such significant part of the history (before the code was donated to Apache)
is kept in an independent repository. To fully understand the code
you may want to merge the modern and ancient versions together:

```bash
$ git clone https://github.com/apache/netbeans.git
$ cd netbeans
$ git log platform/uihandler/arch.xml
```

This gives you just few log entries including the initial checkin and
change of the file headers to Apache. But then the magic comes:

```bash
$ git remote add emilian https://github.com/emilianbold/netbeans-releases.git
$ git fetch emilian # this takes a while, the history is huge!
$ git replace 6daa72c98 32042637 # the 1st donation
$ git replace 6035076ee 32042637 # the 2nd donation
```

When you search the log, or use the blame tool, the full history is available:

```bash
$ git log platform/uihandler/arch.xml
$ git blame platform/uihandler/arch.xml
```

Many thanks to Emilian Bold who converted the ancient history to his
[Git repository](https://github.com/emilianbold/netbeans-releases)
and made the magic possible!
</details>

**This is a work in progress the code is experimental and subject to frequent changes**
<hr>

# Apache NetBeans with WindowManager<sup>2</sup>
An enhanced window manager for Apache NetBeans that supports splitting secondary windows and more

![NetBeans Animation](docs/nb-animation.gif)

## Background
The NetBeans WindowManager has a long history and it has changed considerably over the years.  Some of these changes have lead to behavior that is inconsistent/unexpected. 

<details>
  <summary>More details...</summary>
  <br>
<p>Once there was a main editor region surrounded by ancilliary views.  These ancilliary views could be docked in regions above, below and to the side of the main editor region but Editors and Views could not be mixed within the same dockable regions.  Users could divide up their main window to show as much useful information as possible.</p>
   
As the IDE grew and more and more ancilliary views were added (and displays became a little larger) users wanted the ability to drag editors/view into spearate windows.  
  
* Dragging an Editor into a separate window created a Frame based child window - which could be placed either above or below the main window.

* Dragging a View (non editor) into a separate window created a Dialog based child window - which would always remain visible above the main window.

These separate windows could not be split (to show multiple editors/views at the same time) like the main window.  Instead they were tab based - displaying one item at a time that filled the display area.

**Functionality was consistent but fairly simple.**

Eventually users were allowed to mix Editors and Views in the same dockable regions - partly because similar behavior was available in other IDEs and partly because NetBeans Platform users  (developers creating their own applications based on NetBeans APIs) didn't always have as clear a distinction between editors and views.   Some views could allow updates and some editors could 
be read-only.

**This was where the complexity and inconsistencies began:**

Editor tabs display icons while View based tabs were more compact and did not display icons.  
Dragging an editor from the Editor region into a View based dockable region would result in an
Editor tab with no icon - the Editor from a user perspective was still an Editor but from a 
WindowManager perspective it was now a view!  

Dragging an Editor out of a View based region and into a separate window would result in a Dialog
based window rather than a Frame based window (had the editor been dragged from the editor region).
Because the main window could be split horizontally and vertically into many sections it wasn't always
obvious which region was the main editor region and which regions were view specific regions.

View regions can be minimized into slide out regions (but the editor region can't).

Copied from other sections to be cleaned up...

The current NetBeans Window Manager will create a **Frame** based floating window if the TopComponent was dragged from the editor mode. It will create a **Dialog** based floating window if a TopComponent was dragged from any mode outside the editor region.

Therefore, dropping the same editor TopComponent outside the main window could sometimes result in the creation of a Dialog and sometimes a Frame.

The current NetBeans Window Manager determines if a TopComponent is an **Editor** or a **View** based on the mode the TopComponent is docked into. [(see DevFaqWindowsMode)](http://wiki.netbeans.org/DevFaqWindowsMode)

There are 3 mode types:

* MODE_KIND_EDITOR
* MODE_KIND_VIEW
* MODE_KIND_SLIDING

This means a TopComponent can transition from being an Editor or a View based on the location it's displayed in the GUI. In reality a TopComponent that provides Editor capabilities is an Editor no matter where it's located.

The current Editor detection based on modes leads to inconsistencies in:
* Tab displays (icon vs no icon)
* Floating window creation  (Frame vs Dialog)
* Status Bar visibility (Frames have status bars, dialogs do not)

NetBeans currently displays icons on tabs in the Editor mode.  All other modes display more compact tabs without images.  When you drag an Editor (e.g. Java File) from the Editor mode into a View mode (e.g. the Output mode below the Editor region) the icon is dropped and the tab looks less like an Editor tab and more like a View tab.

This new WindowManager will display icons for Editors regardless of the mode they are contained in.

For example: **Moving this editor**<br><br>
<p align="center">
  <img style="display: flex; margin: auto" align="center" src='/docs/tabs-before.png'>
</p>
<br>

**Results in this...**<br><br>
<p align="center">
  <img style="display: flex; margin: auto" align="center" src='/docs/tabs-after.png'>
</p>
<br>

**Rather than...**<br><br>
<p align="center">
  <img style="display: flex; margin: auto" align="center" src='/docs/tabs-after-current.png'>
</p>
</details>

## Project Goal

The goal of this project is to provide a replacement NetBeans Window Manager with enhanced, **consistent** functionality and to give developers using the **NetBeans Platform** a little more control over how their applications behave and look.

The new Window Manager should be backward compatible with existing applications and should be capable of reading existing persisted mode configurations.

Summary of New Features
* [Consistent TopComponent layout across all windows](#consistent-topcomponent-layout-across-all-windows)
* [Consistent window creation](#consistent-window-creation)
* [Consistent editor detection](#consistent-editor-detection)
* [Consistent tab icon visibility](#consistent-tab-icon-visibility)
* [Consistent Editor status bar visibility](#consistent-editor-status-bar-visibility)
* [Consistent Z-ordering](#consistent-zordering)
* [IconSelector](#iconselector)
* [EditorSelector](#editorselector)
* [NbWindowSelector](#nbwindowselector)

## Integration

Provide instructions for using the new Window Manager here!

# The Details

## Consistent TopComponent layout across all windows

WindowManager<sup>2</sup> allow users to layout TopComponents (Editors & Views) in complex arrangements in secondary (aka Floating) windows.  Secondary/Floating windows can have Sliding modes.
<br>
<p align="center">
  <img style="display: flex; margin: auto" align="center" src='/docs/complex.png'>
</p>

## Consistent Window Creation

WindowManager<sup>2</sup> will create **Dialog** based windows that stay visible above the main window for _all_ TopComponents dropped outside the main window.

This behavior can be altered using [NbWindowSelector](#nbwindowselector).  A simple setting change can instruct WindowManager<sup>2</sup> to create **Frame** based windows for **Editors** and **Dialog** based windows for **Views**.

## Consistent Editor detection

WindowManager<sup>2</sup> will use [EditorSelector](#editorselector) to determine if a TopComponent is an editor.  The result will be consistent regardless of the location or **mode** a TopComponent is docked into.

## Consistent Tab Icon Visibility

WindowManager<sup>2</sup> **will always** display icons in **Editor** tabs and will **never** display icons in **View** tabs.

This behavior can be altered using [IconSelector](#iconselector).  A simple setting change can instruct WindowManager<sup>2</sup> to **always** display icons for **both Editors & Views**.

## Consistent Editor Status Bar Visibility

WindowManager<sup>2</sup> will display editor status bars in both **Frame** and **Dialog** based windows _(if the window contains an Editor)_.

## Consistent Z-ordering

WindowManager<sup>2</sup> will correctly track **Frame** and **Dialog** based windows z-order.  When an application is relaunched the windows will be recreated (and thus overlap) in the same order they were when the application exited.

Tracking the correct z-order will also allow WindowManager<sup>2</sup> to correctly display the intended drop location.

## IconSelector 

IconSelector is a ServiceProvider interface that can be used to determine:
1. If an icon should be displayed in a Modes TabContainer
2. The Icon to be displayed.

The IconSelector Interface:  

```java
public interface IconSelector {
    /**
     * 
     * @param tc
     * @param wantedIcon
     * @return 
     */
    public Icon getIconForTopComponent(TopComponent tc, Icon wantedIcon);    
}
```

IconSelector implementations will be called _(in an undeterministic order)_.  The first non-null value returned will be rendered by the tab-control.  The icon the tab control would like to paint is passed in the wantedIcon parameter.  An IconSelector could return the icon as is, decorate it or replace it with something else.

The DefaultIconSelector implementation: 

```java
@ServiceProvider(service=IconSelector.class)
public class DefaultIconSelector implements IconSelector {

    @Override
    public Icon getIconForTopComponent(TopComponent tc, Icon wantedIcon) {
        if(wantedIcon == null)
            return null;
        
        boolean isEditor = WindowManagerImpl.getInstance().isEditor(tc);
        if(!isEditor)
            return null;
        
        return wantedIcon;
    }    
}
```
The DefaultIconSelector implementation only returns an Icon if the TopComponent is an Editor.

A TopComponent does not have a method for determining if it's an Editor or not.  NetBeans currently determines if a TopComponent is an Editor or a View based on the mode it was added to.  A TopComponent type can therefore change as the component is moved around. 

WindowManager<sup>2</sup> uses a new ServiceProvider interface to consistently determine if a TopComponent is an Editor<br>
[(see EditorSelector)](#editorselector).

<sup>Note:</sup><br>The argument **_netbeans.winsys.enhanced.tab-icons=true_** can be used to change the behavior of the DefaultIconSelector so that the requested icon is always used (Both Editors and Views will have icons)

## EditorSelector 

EditorSelector is a ServiceProvider interface that can be used to determine if a TopComponent is an Editor.  ServiceProvider implementations will be be called _(in an undeterministic order)_.  The first provider to return **true** will indicate the TopComponent is an Editor.

The EditorSelector interface:

```java
public interface EditorSelector {
    /**
     * 
     * @param tc
     * @return true if the supplied TopComponent is an Editor
     */
    public boolean isEditor(TopComponent tc);    
}
```

The DefaultEditorSelector implementation:

```java
@ServiceProvider(service=IconSelector.class)
public class DefaultIconSelector implements IconSelector {

    @Override
    public Icon getIconForTopComponent(TopComponent tc, Icon wantedIcon) {        
        if(wantedIcon == null)
            return null;
        
        if (Boolean.getBoolean("netbeans.winsys.enhanced.tab-icons")) {
            return wantedIcon;
        }
        
        boolean isEditor = WindowManagerImpl.getInstance().isEditor(tc);
        if(!isEditor)
            return null;
        
        return wantedIcon;
    }    
}
```

The default implementation will return **true** for any TopComponent that has an EditorCookie in its lookup.  NetBeans Platform developers can easily provide additional implementations for determing if a TopComponent is an Editor.

## NbWindowSelector

NbWindowSelector is a ServiceProvider interface that can be used to determine what type of window should be created when a TopComponent is dropped in free-space.  NbWindowSelector implementations will be called _(in an undeterministic order)_.  The first implementation that returns **true** will indicate a **Dialog** based window is required.

The NbWindowSelector interface:

```java
public interface NbWindowSelector {
    /**
     * Determine (based on TopComponent) if an NbWindow should be Frame or Dialog
     * 
     * @param tc
     * @return 
     */
    public Boolean isDialogRequested(TopComponent tc);    
}
```

The DefaultNbWindowSelector implementation:

```java
@ServiceProvider(service=NbWindowSelector.class)
public class DefaultNbWindowSelector implements NbWindowSelector {
    @Override
    public Boolean isDialogRequested(TopComponent tc) {
        if (Boolean.getBoolean("netbeans.winsys.enhanced.nbwindow-frames") == Boolean.FALSE) {
            // only create dialogs for non-editors
            boolean isEditor = WindowManagerImpl.getInstance().isEditorTopComponent(tc);
            return !isEditor;
        }
        // default behavior
        return true;
    }    
}
```

The default implementation will always create a **Dialog** based window.

<sup>Note:</sup><br>The argument **_netbeans.winsys.enhanced.nbWindow.nbwindow-frames=true** can be used to change the behavior to create a new **Frame** based window when dropping an **Editor**. A new **Dialog** based window will be created when dropping a **View** (non-editor).
