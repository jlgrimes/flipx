package com.flipx.hinge;

interface IUserService {
    void destroy() = 16777114;
    void startWatch() = 1;
    void stopWatch() = 2;
    boolean isRunning() = 3;
    String lastEvent() = 4;
    boolean setHomeHolder(String pkg) = 5;
    String currentHomeHolder() = 6;
    void setLaunchers(String openPkg, String closePkg) = 7;

    /**
     * Lock display rotation to landscape on hinge close; free rotation on hinge open.
     * (Open is intentionally NOT forced — let the routed app, e.g. ES-DE, behave normally.)
     * Disabling restores free rotation system-wide.
     */
    void setOrientationLock(boolean enabled) = 8;

    boolean launchComponent(String componentName) = 9;
}
