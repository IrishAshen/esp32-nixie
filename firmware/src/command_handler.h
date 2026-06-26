#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <functional>
#include <map>

// ─────────────────────────────────────────────────────────────────
//  JSON command handler
//
//  Uses a command registry pattern – addCommand() maps a string
//  to a handler function.  This makes it trivial to extend with
//  new commands without touching the dispatch core.
//
//  Handler signature:
//    void(const JsonObject &cmd, JsonObject &response)
//
//  cmd:       the parsed "cmd" object from the incoming JSON
//  response:  a pre-allocated JsonDocument root that handlers
//             populate with {"event":"response", "status":"ok", …}
// ─────────────────────────────────────────────────────────────────

class CommandHandler {
public:
    /// Register a command handler.
    /// @param name   command string (e.g. "set_wifi")
    /// @param fn     handler function
    void addCommand(const String &name,
                    std::function<void(const JsonObject &cmd,
                                       JsonObject &response)> fn);

    /// Process an incoming JSON string.
    /// Returns a response JSON string, or empty string if no
    /// response is needed.
    String process(const String &json);

    /// Return names of all registered commands (for debugging).
    String listCommands() const;

private:
    std::map<String, std::function<void(const JsonObject &cmd,
                                         JsonObject &response)>> _handlers;

    /// Build a standard error response.
    static String _makeError(const String &message);
};
