package com.mcfaas.controlplane.core;

public enum ExecutionState {
    QUEUED,
    RUNNING,
    SUCCESS,
    ERROR,
    TIMEOUT
}
