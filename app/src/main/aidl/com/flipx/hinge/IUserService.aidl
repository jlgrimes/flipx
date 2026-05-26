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
     * Make WindowManager ignore per-app orientation requests, so launchers (and any other
     * app) that lock themselves to a fixed orientation rotate with the sensor instead.
     * Runs `wm set-ignore-orientation-request <true|false>` as shell uid.
     */
    boolean setIgnoreOrientationRequest(boolean ignore) = 8;

    /** Returns the current value of the WindowManager ignore-orientation-request flag. */
    boolean isIgnoringOrientationRequest() = 9;
}
