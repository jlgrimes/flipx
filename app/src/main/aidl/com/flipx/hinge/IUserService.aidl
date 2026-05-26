package com.flipx.hinge;

interface IUserService {
    void destroy() = 16777114;
    void startWatch() = 1;
    void stopWatch() = 2;
    boolean isRunning() = 3;
    String lastEvent() = 4;

    /**
     * Set a package as the holder of the HOME role via `cmd role`.
     * Used by MainActivity to silently make flipx the system's default home.
     */
    boolean setHomeHolder(String pkg) = 5;

    /** Returns the package currently holding the HOME role, or empty string. */
    String currentHomeHolder() = 6;
}
