package com.flipx.hinge;

interface IUserService {
    void destroy() = 16777114;
    void startWatch() = 1;
    void stopWatch() = 2;
    boolean isRunning() = 3;
    String lastEvent() = 4;
    boolean setHomeHolder(String pkg) = 5;
    String currentHomeHolder() = 6;

    /**
     * Tell the watcher which packages are the configured launchers. When the hinge
     * flips and the foreground app is one of these, the watcher auto-fires a HOME
     * intent to route via flipx into the new launcher.
     */
    void setLaunchers(String openPkg, String closePkg) = 7;
}
