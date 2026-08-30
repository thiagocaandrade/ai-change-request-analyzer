#!/usr/bin/env python3
"""Demonstracao do servidor MCP da aplicacao (evidencia docs/evidence/04-mcp.png).

Faz o handshake streamable HTTP (initialize) e lista as tools via tools/list
usando apenas urllib. Roda contra a aplicacao em execucao (APP_URL).
"""

import json
import sys
import urllib.request

APP_URL = "http://localhost:8080"


def parse_message(raw):
    """Extrai o JSON de uma resposta streamable HTTP (JSON puro ou SSE 'data:')."""
    if raw.startswith(("event:", "id:")) or "data:" in raw:
        for line in raw.splitlines():
            if line.startswith("data:"):
                return json.loads(line[len("data:") :].strip())
        raise SystemExit(f"SSE sem bloco data: {raw[:200]}")
    return json.loads(raw)


def rpc(session_id, request_id, method, params=None):
    payload = {
        "jsonrpc": "2.0",
        "id": request_id,
        "method": method,
    }
    if params is not None:
        payload["params"] = params
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_id:
        headers["Mcp-Session-Id"] = session_id
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        APP_URL + "/mcp", data=body, headers=headers, method="POST"
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        session = response.headers.get("Mcp-Session-Id")
        return session, parse_message(response.read().decode("utf-8"))


def main():
    session_id, init_result = rpc(
        None,
        1,
        "initialize",
        {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "evidence", "version": "1.0"},
        },
    )
    server_info = init_result.get("result", {}).get("serverInfo", {})
    print(f"MCP initialize: servidor {server_info.get('name')} v{server_info.get('version')}")
    print(f"Mcp-Session-Id: {session_id}")
    print()

    _, list_result = rpc(session_id, 2, "tools/list", {})
    tools = list_result.get("result", {}).get("tools", [])
    print(f"tools/list -> {len(tools)} tools expostas:")
    for tool in tools:
        print(f"- {tool.get('name')}: {tool.get('description', '')[:80]}")

    _, call_result = rpc(
        session_id,
        3,
        "tools/call",
        {"name": "get_file", "arguments": {"path": "../fora-do-repo.txt"}},
    )
    content = call_result.get("result", {}).get("content", [])
    print()
    print("tools/call get_file('../fora-do-repo.txt'):")
    for item in content:
        print(f"  {item.get('type')}: {item.get('text')}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
