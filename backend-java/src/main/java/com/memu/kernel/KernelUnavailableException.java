package com.memu.kernel;

/**
 * Python AI 内核不可达时抛出。
 * 内核由 Tauri sidecar 拉起，Java 不负责重启它，只负责把故障如实暴露出去。
 */
public class KernelUnavailableException extends RuntimeException {

    public KernelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
