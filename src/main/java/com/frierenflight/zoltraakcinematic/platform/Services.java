// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.platform;

import java.util.ServiceLoader;

public class Services {
    public static final IPlatformHelper PLATFORM = loadPlatform();

    private static IPlatformHelper loadPlatform() {
        ServiceLoader<IPlatformHelper> loader = ServiceLoader.load(IPlatformHelper.class);
        for (IPlatformHelper helper : loader) {
            return helper;
        }
        // Fallback for standalone/direct-javac environments
        try {
            Class<?> clazz = Class.forName("com.frierenflight.zoltraakcinematic.platform.neoforge.NeoForgePlatformHelper");
            return (IPlatformHelper) clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to locate an IPlatformHelper implementation via ServiceLoader or fallback", t);
        }
    }
}

