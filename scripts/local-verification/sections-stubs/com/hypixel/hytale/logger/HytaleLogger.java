package com.hypixel.hytale.logger;
public class HytaleLogger {
public HytaleLogger atWarning() { return this; }
public HytaleLogger atSevere() { return this; }
public HytaleLogger withCause(Throwable t) { return this; }
public void log(String format, Object... args) {}
}
