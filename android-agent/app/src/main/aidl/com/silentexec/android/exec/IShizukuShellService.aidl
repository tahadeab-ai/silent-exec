package com.silentexec.android.exec;

interface IShizukuShellService {
    void destroy();
    int execInputCommand(String command);
}
