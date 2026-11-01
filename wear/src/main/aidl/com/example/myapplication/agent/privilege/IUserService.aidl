package com.example.myapplication.agent.privilege;

interface IUserService {
    String runCommand(String command);
    void destroy();
}
