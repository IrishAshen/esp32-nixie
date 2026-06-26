#include "command_handler.h"
#include "config.h"

// ── registration ───────────────────────────────────────────────

void CommandHandler::addCommand(
    const String &name,
    std::function<void(const JsonObject &cmd, JsonObject &response)> fn) {
    _handlers[name] = std::move(fn);
}

// ── dispatch ───────────────────────────────────────────────────

String CommandHandler::process(const String &json) {
    // Parse incoming JSON
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, json);

    if (err) {
        Serial.printf("[cmd] parse error: %s\n", err.c_str());
        return _makeError("Invalid JSON: " + String(err.c_str()));
    }

    JsonObject root = doc.as<JsonObject>();

    // Every command must have a "cmd" field
    if (!root["cmd"].is<const char *>()) {
        return _makeError("Missing 'cmd' field");
    }

    const char *cmdName = root["cmd"];

    // Look up handler
    auto it = _handlers.find(String(cmdName));
    if (it == _handlers.end()) {
        Serial.printf("[cmd] unknown command: %s\n", cmdName);
        return _makeError("Unknown command: " + String(cmdName));
    }

    // Build response document
    JsonDocument respDoc;
    JsonObject resp = respDoc.to<JsonObject>();
    resp["event"]  = "response";
    resp["cmd"]    = cmdName;
    // Default: ok; handler can override to "error" if needed.
    resp["status"] = "ok";

    // Execute command handler
    it->second(root, resp);

    // Serialize
    String out;
    serializeJson(resp, out);
    Serial.printf("[cmd] response: %s\n", out.c_str());
    return out;
}

// ── helpers ────────────────────────────────────────────────────

String CommandHandler::listCommands() const {
    String list;
    for (const auto &kv : _handlers) {
        if (!list.isEmpty()) list += ", ";
        list += kv.first;
    }
    return list;
}

String CommandHandler::_makeError(const String &message) {
    JsonDocument doc;
    doc["event"]   = "response";
    doc["status"]  = "error";
    doc["message"] = message;
    String out;
    serializeJson(doc, out);
    return out;
}
