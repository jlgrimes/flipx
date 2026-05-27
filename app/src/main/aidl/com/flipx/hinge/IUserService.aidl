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
     * Lock the display rotation to the hinge state on the Anbernic RG Rotate:
     * hinge open → portrait (rotation 0), hinge closed → landscape (rotation 1).
     * Disabling calls `wm user-rotation free`, restoring sensor-based rotation.
     */
    void setOrientationLock(boolean enabled) = 8;

    /**
     * Launch a component via `am start -n <component>` running as shell uid.
     * Bypasses HomeRouterActivity's home-stack window context — the launched
     * activity gets the full display, not the 648x720 inherited wrapper bounds.
     */
    boolean launchComponent(String componentName) = 9;

    /**
     * RG Rotate fullscreen workaround. The 720x720 square panel reserves ~36px on each
     * side for system bars even when apps don't draw them, pillarboxing all apps to
     * 648x720. Toggling this on applies both immersive overrides we can reach from
     * shell: ignore-orientation-request and policy_control immersive.full=*.
     */
    void setForceFullscreen(boolean enabled) = 10;
}
